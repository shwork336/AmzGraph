package com.snails.ecommerce.listing.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 图片资产合规豁免请求。
 *
 * @param reviewedBy 执行豁免的管理员
 * @param reason 豁免原因
 */
public record ApproveImageAssetComplianceRequest(
        @NotBlank String reviewedBy,
        @NotBlank String reason
) {
}
