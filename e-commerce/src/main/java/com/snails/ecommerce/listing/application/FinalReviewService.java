package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.api.ApproveFinalSelectionRequest;
import com.snails.ecommerce.listing.api.FinalSelectionResponse;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.TextVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Listing 终审选择应用服务。
 *
 * <p>负责在任务进入终审阶段后选择最终文案版本和图片版本，并把任务推进到完成状态。
 * 当前阶段只处理版本选择和状态流转，不触发导出包生成。</p>
 */
@Service
@RequiredArgsConstructor
public class FinalReviewService {

    /** Listing 任务仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** 文案版本仓储。 */
    private final TextVersionRepository textVersionRepository;

    /** 图片版本仓储。 */
    private final ImageVersionRepository imageVersionRepository;

    /**
     * 批准最终图文版本选择，并完成任务。
     *
     * @param taskId Listing 任务 ID
     * @param request 终审选择请求
     * @return 终审结果响应
     */
    @Transactional
    public FinalSelectionResponse approveFinalSelection(
            String taskId,
            ApproveFinalSelectionRequest request) {
        ListingTask task = requireTask(taskId);
        requireWaitingFinalApprove(task);

        TextVersion selectedTextVersion = requireTextVersion(taskId, request.selectedTextVersionId());
        ImageVersion selectedImageVersion = requireImageVersion(taskId, request.selectedImageVersionId());
        requireSucceededImageVersion(selectedImageVersion);

        List<TextVersion> textVersions = textVersionRepository.findByTaskId(taskId);
        textVersions.forEach(version ->
                version.setSelected(version.getVersionId().equals(selectedTextVersion.getVersionId())));
        textVersionRepository.saveAll(textVersions);

        List<ImageVersion> imageVersions = imageVersionRepository.findByTaskId(taskId);
        imageVersions.forEach(version ->
                version.setSelected(version.getVersionId().equals(selectedImageVersion.getVersionId())));
        imageVersionRepository.saveAll(imageVersions);

        task.setSelectedTextVersionId(selectedTextVersion.getVersionId());
        task.setSelectedImageVersionId(selectedImageVersion.getVersionId());
        task.setStatus(ListingTaskStatus.COMPLETED);
        ListingTask savedTask = listingTaskRepository.save(task);

        return toResponse(savedTask);
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
     * 校验任务处于等待终审状态。
     */
    private void requireWaitingFinalApprove(ListingTask task) {
        if (task.getStatus() != ListingTaskStatus.WAIT_FINAL_APPROVE) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Listing task is not waiting for final approval: " + task.getTaskId());
        }
    }

    /**
     * 查询并校验文案版本属于当前任务。
     */
    private TextVersion requireTextVersion(String taskId, String versionId) {
        TextVersion version = textVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Text version not found: " + versionId));
        if (!taskId.equals(version.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Text version does not belong to task: " + versionId);
        }
        return version;
    }

    /**
     * 查询并校验图片版本属于当前任务。
     */
    private ImageVersion requireImageVersion(String taskId, String versionId) {
        ImageVersion version = imageVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Image version not found: " + versionId));
        if (!taskId.equals(version.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Image version does not belong to task: " + versionId);
        }
        return version;
    }

    /**
     * 校验图片版本已生成成功。
     */
    private void requireSucceededImageVersion(ImageVersion version) {
        if (version.getStatus() != GenerationStatus.SUCCEEDED) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Image version is not ready for final approval: " + version.getVersionId());
        }
    }

    /**
     * 创建终审选择响应。
     */
    private FinalSelectionResponse toResponse(ListingTask task) {
        return new FinalSelectionResponse(
                task.getTaskId(),
                task.getStatus().name(),
                task.getSelectedTextVersionId(),
                task.getSelectedImageVersionId(),
                task.getUpdatedAt());
    }
}
