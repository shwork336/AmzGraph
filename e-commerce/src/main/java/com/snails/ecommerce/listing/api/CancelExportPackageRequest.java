package com.snails.ecommerce.listing.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 取消导出交付包请求。
 *
 * <p>取消只用于尚未执行的 PENDING 导出记录，取消人和原因用于归档页审计。</p>
 *
 * @param canceledBy 执行取消的操作人
 * @param cancelReason 取消原因
 */
public record CancelExportPackageRequest(
        @NotBlank String canceledBy,
        @NotBlank String cancelReason
) {
}
