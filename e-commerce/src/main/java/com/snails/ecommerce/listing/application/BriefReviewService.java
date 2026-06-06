package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.ApproveBriefRequest;
import com.snails.ecommerce.listing.api.BriefVersionResponse;
import com.snails.ecommerce.listing.api.CreateBriefVersionRequest;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Brief 人工审核应用服务。
 *
 * <p>负责读取 Brief 版本和基于最新版本创建人工修改版本。当前阶段保持线性版本链，
 * 不允许从历史版本创建分支，也不在查询操作中触发任何生成或状态变更。</p>
 */
@Service
@RequiredArgsConstructor
public class BriefReviewService {

    /** 任务主记录仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** Brief 版本仓储。 */
    private final ListingBriefVersionRepository listingBriefVersionRepository;

    /** Brief 业务 ID 生成器。 */
    private final IdGenerator idGenerator;

    /** Brief 列表字段的 JSON 序列化器。 */
    private final ObjectMapper objectMapper;

    /**
     * 查询任务的全部 Brief 版本。
     *
     * @param taskId 任务 ID
     * @return 按创建时间和版本 ID 倒序排列的 Brief 版本
     */
    @Transactional(readOnly = true)
    public List<BriefVersionResponse> listBriefVersions(String taskId) {
        requireTask(taskId);
        return listingBriefVersionRepository
                .findByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 查询任务当前最新 Brief。
     *
     * @param taskId 任务 ID
     * @return 最新 Brief 完整内容
     */
    @Transactional(readOnly = true)
    public BriefVersionResponse getLatestBrief(String taskId) {
        requireTask(taskId);
        return toResponse(requireLatestBrief(taskId));
    }

    /**
     * 基于任务最新 Brief 创建人工修改版本。
     *
     * <p>该操作不会覆盖已有版本。请求中的基础版本必须仍是当前最新版本，避免并发修改形成分支。</p>
     *
     * @param taskId 任务 ID
     * @param request 人工修改内容
     * @return 新创建的 Brief 版本
     */
    @Transactional
    public BriefVersionResponse createVersion(String taskId, CreateBriefVersionRequest request) {
        ListingTask task = requireTask(taskId);
        requireWaitingForBriefApproval(task);

        ListingBriefVersion latestBrief = requireLatestBrief(taskId);
        if (!latestBrief.getBriefVersionId().equals(request.baseBriefVersionId())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Brief version is not the latest version: " + request.baseBriefVersionId());
        }
        if (latestBrief.isApproved()) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Approved Brief cannot be modified: " + latestBrief.getBriefVersionId());
        }

        ListingBriefVersion newBrief = new ListingBriefVersion();
        newBrief.setBriefVersionId(idGenerator.generate("brief"));
        newBrief.setTaskId(taskId);
        newBrief.setParentBriefVersionId(latestBrief.getBriefVersionId());
        newBrief.setTargetAudience(request.targetAudience());
        newBrief.setCoreSellingPointsJson(writeStringList(request.coreSellingPoints()));
        newBrief.setTargetKeywordsJson(writeStringList(request.targetKeywords()));
        newBrief.setForbiddenClaimsJson(writeStringList(request.forbiddenClaims()));
        newBrief.setImageDirectionPromptsJson(writeStringList(request.imageDirectionPrompts()));
        newBrief.setComplianceNotesJson(writeStringList(request.complianceNotes()));
        newBrief.setApproved(false);
        newBrief.setCreatedBy(request.createdBy().trim());

        return toResponse(listingBriefVersionRepository.save(newBrief));
    }

    /**
     * 批准任务当前最新 Brief，并把任务推进到图文生成阶段。
     *
     * <p>审批只更新 Brief 审计字段和任务状态，不在本阶段调用文案或图片生成服务。</p>
     *
     * @param taskId 任务 ID
     * @param briefVersionId 待批准的 Brief 版本 ID
     * @param request 审批人信息
     * @return 批准后的 Brief 版本
     */
    @Transactional
    public BriefVersionResponse approveBrief(
            String taskId,
            String briefVersionId,
            ApproveBriefRequest request) {
        ListingTask task = requireTask(taskId);
        requireWaitingForBriefApproval(task);

        ListingBriefVersion brief = listingBriefVersionRepository.findById(briefVersionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Brief not found: " + briefVersionId));
        if (!taskId.equals(brief.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Brief does not belong to task: " + briefVersionId);
        }

        ListingBriefVersion latestBrief = requireLatestBrief(taskId);
        if (!latestBrief.getBriefVersionId().equals(briefVersionId)) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Brief version is not the latest version: " + briefVersionId);
        }
        if (brief.isApproved()) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Brief has already been approved: " + briefVersionId);
        }

        brief.setApproved(true);
        brief.setApprovedBy(request.approvedBy().trim());
        brief.setApprovedAt(LocalDateTime.now());
        task.setBriefStatus(BriefStatus.APPROVED);
        task.setStatus(ListingTaskStatus.GENERATING);

        listingTaskRepository.save(task);
        return toResponse(listingBriefVersionRepository.save(brief));
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
     * 校验任务仍处于 Brief 待审核阶段。
     */
    private void requireWaitingForBriefApproval(ListingTask task) {
        if (task.getStatus() != ListingTaskStatus.WAIT_BRIEF_APPROVE
                || task.getBriefStatus() != BriefStatus.WAIT_APPROVE) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Listing task is not waiting for Brief approval: " + task.getTaskId());
        }
    }

    /**
     * 查询任务最新 Brief，不存在时返回请求无效。
     */
    private ListingBriefVersion requireLatestBrief(String taskId) {
        return listingBriefVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(taskId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Brief not found for task: " + taskId));
    }

    /**
     * 将 Brief 实体转换为稳定 API 响应。
     */
    private BriefVersionResponse toResponse(ListingBriefVersion brief) {
        return new BriefVersionResponse(
                brief.getBriefVersionId(),
                brief.getTaskId(),
                brief.getParentBriefVersionId(),
                brief.getTargetAudience(),
                readStringList(brief.getCoreSellingPointsJson()),
                readStringList(brief.getTargetKeywordsJson()),
                readStringList(brief.getForbiddenClaimsJson()),
                readStringList(brief.getImageDirectionPromptsJson()),
                readStringList(brief.getComplianceNotesJson()),
                brief.isApproved(),
                brief.getCreatedBy(),
                brief.getApprovedBy(),
                brief.getApprovedAt(),
                brief.getCreatedAt());
    }

    /**
     * 序列化 Brief 列表字段。
     */
    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process Brief JSON fields");
        }
    }

    /**
     * 解析 Brief 列表字段。
     */
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(
                    json == null ? "[]" : json,
                    new TypeReference<List<String>>() {
                    });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process Brief JSON fields");
        }
    }
}
