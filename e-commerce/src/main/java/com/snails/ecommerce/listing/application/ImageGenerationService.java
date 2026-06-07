package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.ImageAssetResponse;
import com.snails.ecommerce.listing.api.ImageVersionResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.infrastructure.ImageAssetRepository;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
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
 * Listing 图片生成应用服务。
 *
 * <p>负责在 Brief 已批准后调用图片生成端口创建 {@link ImageVersion} 和 {@link ImageAsset}，
 * 并维护任务的图片生成状态。当前阶段只实现首版图片组最小闭环，不推进终审状态。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private static final String PLACEHOLDER_PROVIDER = "PLACEHOLDER";
    private static final String PLACEHOLDER_MODEL = "placeholder-image-model";

    /** Listing 任务仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** Brief 版本仓储。 */
    private final ListingBriefVersionRepository listingBriefVersionRepository;

    /** 图片版本仓储。 */
    private final ImageVersionRepository imageVersionRepository;

    /** 图片资产仓储。 */
    private final ImageAssetRepository imageAssetRepository;

    /** 类目模板服务。 */
    private final CategoryTemplateService categoryTemplateService;

    /** 图片资产生成端口。 */
    private final ImageAssetGenerator imageAssetGenerator;

    /** 业务 ID 生成器。 */
    private final IdGenerator idGenerator;

    /** JSON 字段映射器。 */
    private final ObjectMapper objectMapper;

    /**
     * 基于任务当前已批准 Brief 生成一个图片版本。
     *
     * <p>每次调用都会追加新图片版本，不覆盖历史版本。终审尚未接入，因此成功后主任务仍保持
     * {@link ListingTaskStatus#GENERATING}。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 新创建的图片版本响应
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public ImageVersionResponse generateInitialImageVersion(String taskId) {
        ListingTask task = requireTask(taskId);
        requireGeneratingWithApprovedBrief(task);
        ListingBriefVersion brief = requireApprovedLatestBrief(taskId);
        CategoryTemplate template = categoryTemplateService.getEnabledTemplate(
                task.getCategoryCode(),
                task.getMarketplace(),
                task.getLanguage());
        String parentVersionId = imageVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescVersionIdDesc(taskId)
                .map(ImageVersion::getVersionId)
                .orElse(null);

        task.setImageStatus(GenerationStatus.RUNNING);
        listingTaskRepository.save(task);

        ImageVersion imageVersion = createRunningImageVersion(task, brief, parentVersionId);
        ImageVersion savedVersion = imageVersionRepository.save(imageVersion);
        try {
            List<ImageAsset> generatedAssets = imageAssetGenerator.generateImageAssets(savedVersion, brief, template);
            if (generatedAssets == null || generatedAssets.isEmpty()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Image generator returned empty result");
            }
            for (int index = 0; index < generatedAssets.size(); index++) {
                ImageAsset asset = generatedAssets.get(index);
                asset.setAssetId(idGenerator.generate("asset"));
                asset.setImageVersionId(savedVersion.getVersionId());
                asset.setSortOrder(index + 1);
            }
            imageAssetRepository.saveAll(generatedAssets);

            savedVersion.setStatus(GenerationStatus.SUCCEEDED);
            savedVersion.setQualityScore(80);
            ImageVersion completedVersion = imageVersionRepository.save(savedVersion);
            task.setImageStatus(GenerationStatus.SUCCEEDED);
            listingTaskRepository.save(task);
            return toVersionResponse(completedVersion);
        } catch (BusinessException exception) {
            markImageGenerationFailed(task, savedVersion);
            throw exception;
        } catch (RuntimeException exception) {
            markImageGenerationFailed(task, savedVersion);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to generate Listing images");
        }
    }

    /**
     * 查询任务的全部图片版本。
     *
     * <p>查询操作不触发生成、状态变更或终审选择。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 按创建时间和版本 ID 倒序排列的图片版本
     */
    @Transactional(readOnly = true)
    public List<ImageVersionResponse> listImageVersions(String taskId) {
        requireTask(taskId);
        return imageVersionRepository
                .findByTaskIdOrderByCreatedAtDescVersionIdDesc(taskId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    /**
     * 查询指定图片版本下的全部图片资产。
     *
     * <p>图片版本必须属于指定任务，避免跨任务读取资产。</p>
     *
     * @param taskId Listing 任务 ID
     * @param imageVersionId 图片版本 ID
     * @return 按组内排序和资产 ID 排列的图片资产
     */
    @Transactional(readOnly = true)
    public List<ImageAssetResponse> listImageAssets(String taskId, String imageVersionId) {
        requireTask(taskId);
        ImageVersion imageVersion = imageVersionRepository.findById(imageVersionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Image version not found: " + imageVersionId));
        if (!taskId.equals(imageVersion.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Image version does not belong to task: " + imageVersionId);
        }
        return imageAssetRepository
                .findByImageVersionIdOrderBySortOrderAscAssetIdAsc(imageVersionId)
                .stream()
                .map(this::toAssetResponse)
                .toList();
    }

    /**
     * 创建运行中的图片版本。
     */
    private ImageVersion createRunningImageVersion(
            ListingTask task,
            ListingBriefVersion brief,
            String parentVersionId) {
        ImageVersion version = new ImageVersion();
        version.setVersionId(idGenerator.generate("image"));
        version.setTaskId(task.getTaskId());
        version.setParentVersionId(parentVersionId);
        version.setBriefVersionId(brief.getBriefVersionId());
        version.setInputProductUrlsJson(task.getOriginalProductUrlsJson());
        version.setImageProvider(PLACEHOLDER_PROVIDER);
        version.setImageModel(PLACEHOLDER_MODEL);
        version.setGenerationParamsJson("{}");
        version.setStatus(GenerationStatus.RUNNING);
        version.setSelected(false);
        return version;
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
                    "Listing task is not ready for image generation: " + task.getTaskId());
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
     * 持久化图片生成失败状态。
     */
    private void markImageGenerationFailed(ListingTask task, ImageVersion imageVersion) {
        imageVersion.setStatus(GenerationStatus.FAILED);
        imageVersionRepository.save(imageVersion);
        task.setImageStatus(GenerationStatus.FAILED);
        listingTaskRepository.save(task);
    }

    /**
     * 将图片版本实体转换为稳定 API 响应。
     */
    private ImageVersionResponse toVersionResponse(ImageVersion version) {
        return new ImageVersionResponse(
                version.getVersionId(),
                version.getTaskId(),
                version.getParentVersionId(),
                version.getBriefVersionId(),
                version.getIterationPrompt(),
                version.getReferenceImageUrl(),
                readStringList(version.getInputProductUrlsJson()),
                version.getImageProvider(),
                version.getImageModel(),
                version.getGenerationParamsJson(),
                version.getStatus() == null ? null : version.getStatus().name(),
                version.getQualityScore(),
                version.isSelected(),
                version.getCreatedAt());
    }

    /**
     * 将图片资产实体转换为稳定 API 响应。
     */
    private ImageAssetResponse toAssetResponse(ImageAsset asset) {
        return new ImageAssetResponse(
                asset.getAssetId(),
                asset.getImageVersionId(),
                asset.getType() == null ? null : asset.getType().name(),
                asset.getPrompt(),
                asset.getRewrittenPrompt(),
                asset.getGeneratedImageUrl(),
                asset.getSourceEditableFileUrl(),
                asset.getSizeProfile(),
                asset.getTargetWidth(),
                asset.getTargetHeight(),
                asset.getComplianceStatus(),
                readStringList(asset.getComplianceMethodsJson()),
                readStringList(asset.getComplianceIssuesJson()),
                asset.getComplianceReviewedBy(),
                asset.getComplianceReviewedAt(),
                asset.getSortOrder(),
                asset.getCreatedAt());
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process image JSON fields");
        }
    }
}
