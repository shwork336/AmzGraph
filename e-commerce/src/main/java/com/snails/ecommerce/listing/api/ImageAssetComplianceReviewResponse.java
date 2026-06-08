package com.snails.ecommerce.listing.api;

import java.time.LocalDateTime;

/**
 * 图片资产合规豁免响应。
 *
 * <p>该 DTO 用于返回管理员对单张图片资产的合规豁免结果，避免直接暴露 JPA 实体。</p>
 *
 * @param assetId 图片资产 ID
 * @param imageVersionId 所属图片版本 ID
 * @param complianceStatus 合规状态
 * @param complianceReviewedBy 豁免管理员
 * @param complianceReviewReason 豁免原因
 * @param complianceReviewedAt 豁免时间
 */
public record ImageAssetComplianceReviewResponse(
        String assetId,
        String imageVersionId,
        String complianceStatus,
        String complianceReviewedBy,
        String complianceReviewReason,
        LocalDateTime complianceReviewedAt
) {
}
