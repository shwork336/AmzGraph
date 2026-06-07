package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.api.ApproveImageAssetComplianceRequest;
import com.snails.ecommerce.listing.api.ImageAssetComplianceReviewResponse;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.infrastructure.ImageAssetRepository;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 图片资产合规豁免应用服务。
 *
 * <p>负责记录管理员对 FAIL 图片资产的强制豁免。豁免不修改原始合规状态，只补充人工确认人、原因和时间，
 * 供导出服务判断是否允许放行。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageAssetComplianceService {

    /** Listing 任务仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** 图片版本仓储。 */
    private final ImageVersionRepository imageVersionRepository;

    /** 图片资产仓储。 */
    private final ImageAssetRepository imageAssetRepository;

    /** 操作审计日志服务。 */
    private final OperationAuditLogService operationAuditLogService;

    /**
     * 记录管理员对 FAIL 图片资产的合规豁免。
     *
     * @param taskId Listing 任务 ID
     * @param imageVersionId 图片版本 ID
     * @param assetId 图片资产 ID
     * @param request 豁免请求
     * @return 豁免结果响应
     */
    @Transactional
    public ImageAssetComplianceReviewResponse approveCompliance(
            String taskId,
            String imageVersionId,
            String assetId,
            ApproveImageAssetComplianceRequest request) {
        requireValidRequest(request);
        requireTask(taskId);
        ImageVersion imageVersion = requireImageVersion(taskId, imageVersionId);
        ImageAsset asset = requireImageAsset(imageVersion.getVersionId(), assetId);
        requireFailedAsset(asset);

        ImageAssetComplianceReviewResponse response = saveReview(asset, request);
        operationAuditLogService.record(
                "IMAGE_ASSET_COMPLIANCE_APPROVED",
                request.reviewedBy(),
                "IMAGE_ASSET",
                asset.getAssetId(),
                taskId,
                request.reason(),
                Map.of(
                        "imageVersionId", imageVersion.getVersionId(),
                        "complianceStatus", asset.getComplianceStatus()));
        return response;
    }

    /**
     * 记录运营对 WARNING 图片资产的人工确认。
     *
     * @param taskId Listing 任务 ID
     * @param imageVersionId 图片版本 ID
     * @param assetId 图片资产 ID
     * @param request 确认请求
     * @return 确认结果响应
     */
    @Transactional
    public ImageAssetComplianceReviewResponse confirmWarning(
            String taskId,
            String imageVersionId,
            String assetId,
            ApproveImageAssetComplianceRequest request) {
        requireValidRequest(request);
        requireTask(taskId);
        ImageVersion imageVersion = requireImageVersion(taskId, imageVersionId);
        ImageAsset asset = requireImageAsset(imageVersion.getVersionId(), assetId);
        requireWarningAsset(asset);

        ImageAssetComplianceReviewResponse response = saveReview(asset, request);
        operationAuditLogService.record(
                "IMAGE_ASSET_WARNING_CONFIRMED",
                request.reviewedBy(),
                "IMAGE_ASSET",
                asset.getAssetId(),
                taskId,
                request.reason(),
                Map.of(
                        "imageVersionId", imageVersion.getVersionId(),
                        "complianceStatus", asset.getComplianceStatus()));
        return response;
    }

    /**
     * 校验管理员和豁免原因必填。
     */
    private void requireValidRequest(ApproveImageAssetComplianceRequest request) {
        if (request == null
                || !StringUtils.hasText(request.reviewedBy())
                || !StringUtils.hasText(request.reason())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Compliance reviewer and reason are required");
        }
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
     * 查询并校验图片版本属于当前任务。
     */
    private ImageVersion requireImageVersion(String taskId, String imageVersionId) {
        ImageVersion version = imageVersionRepository.findById(imageVersionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Image version not found: " + imageVersionId));
        if (!taskId.equals(version.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Image version does not belong to task: " + imageVersionId);
        }
        return version;
    }

    /**
     * 查询并校验图片资产属于当前图片版本。
     */
    private ImageAsset requireImageAsset(String imageVersionId, String assetId) {
        ImageAsset asset = imageAssetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Image asset not found: " + assetId));
        if (!imageVersionId.equals(asset.getImageVersionId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Image asset does not belong to image version: " + assetId);
        }
        return asset;
    }

    /**
     * 校验只有 FAIL 图片资产允许执行管理员豁免。
     */
    private void requireFailedAsset(ImageAsset asset) {
        if (!"FAIL".equals(asset.getComplianceStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Only failed image asset can be approved by admin: " + asset.getAssetId());
        }
    }

    /**
     * 校验只有 WARNING 图片资产允许执行普通人工确认。
     */
    private void requireWarningAsset(ImageAsset asset) {
        if (!"WARNING".equals(asset.getComplianceStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Only warning image asset can be confirmed by operator: " + asset.getAssetId());
        }
    }

    /**
     * 写入人工确认信息。
     */
    private ImageAssetComplianceReviewResponse saveReview(
            ImageAsset asset,
            ApproveImageAssetComplianceRequest request) {
        asset.setComplianceReviewedBy(request.reviewedBy());
        asset.setComplianceReviewReason(request.reason());
        asset.setComplianceReviewedAt(LocalDateTime.now());
        ImageAsset saved = imageAssetRepository.save(asset);

        return toResponse(saved);
    }

    /**
     * 转换为合规豁免响应。
     */
    private ImageAssetComplianceReviewResponse toResponse(ImageAsset asset) {
        return new ImageAssetComplianceReviewResponse(
                asset.getAssetId(),
                asset.getImageVersionId(),
                asset.getComplianceStatus(),
                asset.getComplianceReviewedBy(),
                asset.getComplianceReviewReason(),
                asset.getComplianceReviewedAt());
    }
}
