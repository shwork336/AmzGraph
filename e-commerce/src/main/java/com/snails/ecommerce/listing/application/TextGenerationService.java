package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.TextVersionResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.TextVersionRepository;
import com.snails.ecommerce.template.application.CategoryTemplateService;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Listing 文案生成应用服务。
 *
 * <p>负责在 Brief 已批准后调用文案生成端口创建 {@link TextVersion}，并维护任务的文案生成状态。
 * 当前阶段只实现首版文案最小闭环，不推进终审状态，也不触发图片生成。</p>
 */
@Service
@RequiredArgsConstructor
public class TextGenerationService {

    /** Listing 任务仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** Brief 版本仓储。 */
    private final ListingBriefVersionRepository listingBriefVersionRepository;

    /** 文案版本仓储。 */
    private final TextVersionRepository textVersionRepository;

    /** 类目模板服务。 */
    private final CategoryTemplateService categoryTemplateService;

    /** 文案生成端口。 */
    private final ListingTextGenerator listingTextGenerator;

    /** 文案版本 ID 生成器。 */
    private final IdGenerator idGenerator;

    /** JSON 字段映射器。 */
    private final ObjectMapper objectMapper;

    /**
     * 基于任务当前已批准 Brief 生成一个文案版本。
     *
     * <p>每次调用都会追加新版本，不覆盖历史版本。图片生成尚未接入，因此成功后主任务仍保持
     * {@link ListingTaskStatus#GENERATING}，图片状态保持原值。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 新创建的文案版本响应
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TextVersionResponse generateInitialTextVersion(String taskId) {
        ListingTask task = requireTask(taskId);
        requireGeneratingWithApprovedBrief(task);
        ListingBriefVersion brief = requireApprovedLatestBrief(taskId);
        CategoryTemplate template = categoryTemplateService.getEnabledTemplate(
                task.getCategoryCode(),
                task.getMarketplace(),
                task.getLanguage());
        String parentVersionId = textVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescVersionIdDesc(taskId)
                .map(TextVersion::getVersionId)
                .orElse(null);

        task.setTextStatus(GenerationStatus.RUNNING);
        listingTaskRepository.save(task);

        try {
            TextVersion generated = listingTextGenerator.generateText(brief, template, null);
            if (generated == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Text generator returned empty result");
            }
            generated.setVersionId(idGenerator.generate("text"));
            generated.setTaskId(taskId);
            generated.setParentVersionId(parentVersionId);
            generated.setBriefVersionId(brief.getBriefVersionId());
            generated.setSelected(false);

            TextVersion saved = textVersionRepository.save(generated);
            task.setTextStatus(GenerationStatus.SUCCEEDED);
            moveToFinalReviewIfReady(task);
            listingTaskRepository.save(task);
            return toResponse(saved);
        } catch (BusinessException exception) {
            markTextGenerationFailed(task);
            throw exception;
        } catch (RuntimeException exception) {
            markTextGenerationFailed(task);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to generate Listing text");
        }
    }

    /**
     * 查询任务的全部文案版本。
     *
     * <p>查询操作不触发生成、状态变更或终审选择。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 按创建时间和版本 ID 倒序排列的文案版本
     */
    @Transactional(readOnly = true)
    public List<TextVersionResponse> listTextVersions(String taskId) {
        requireTask(taskId);
        return textVersionRepository
                .findByTaskIdOrderByCreatedAtDescVersionIdDesc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询任务，不存在时返回稳定业务错误。
     */
    private ListingTask requireTask(String taskId) {
        return listingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TASK_NOT_FOUND,
                        "Listing task not found: " + taskId));
    }

    /**
     * 校验任务已进入生成阶段且 Brief 已批准。
     */
    private void requireGeneratingWithApprovedBrief(ListingTask task) {
        if (task.getStatus() != ListingTaskStatus.GENERATING
                || task.getBriefStatus() != BriefStatus.APPROVED) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Listing task is not ready for text generation: " + task.getTaskId());
        }
    }

    /**
     * 查询当前最新 Brief，并要求其已批准。
     */
    private ListingBriefVersion requireApprovedLatestBrief(String taskId) {
        ListingBriefVersion brief = listingBriefVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(taskId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Brief not found for task: " + taskId));
        if (!brief.isApproved()) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Latest Brief has not been approved: " + brief.getBriefVersionId());
        }
        return brief;
    }

    /**
     * 持久化文案生成失败状态。
     */
    private void markTextGenerationFailed(ListingTask task) {
        task.setTextStatus(GenerationStatus.FAILED);
        listingTaskRepository.save(task);
    }

    /**
     * 文案和图片均生成成功时推进到终审阶段。
     */
    private void moveToFinalReviewIfReady(ListingTask task) {
        if (task.getTextStatus() == GenerationStatus.SUCCEEDED
                && task.getImageStatus() == GenerationStatus.SUCCEEDED) {
            task.setStatus(ListingTaskStatus.WAIT_FINAL_APPROVE);
        }
    }

    /**
     * 将文案实体转换为稳定 API 响应。
     */
    private TextVersionResponse toResponse(TextVersion version) {
        return new TextVersionResponse(
                version.getVersionId(),
                version.getTaskId(),
                version.getParentVersionId(),
                version.getBriefVersionId(),
                version.getIterationPrompt(),
                version.getTitle(),
                readStringList(version.getBulletPointsJson()),
                version.getDescription(),
                version.getBackendSearchTerms(),
                readStringList(version.getTargetKeywordsJson()),
                readStringList(version.getComplianceWarningsJson()),
                version.getQualityScore(),
                version.isSelected(),
                version.getCreatedAt());
    }

    /**
     * 读取 JSON 字符串数组，空值按空数组处理。
     */
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(
                    json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<List<String>>() {
                    });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process text version JSON fields");
        }
    }
}
