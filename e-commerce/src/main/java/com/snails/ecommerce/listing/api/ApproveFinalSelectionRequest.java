package com.snails.ecommerce.listing.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 终审选择请求。
 *
 * @param selectedTextVersionId 最终选中的文案版本 ID
 * @param selectedImageVersionId 最终选中的图片版本 ID
 */
public record ApproveFinalSelectionRequest(
        @NotBlank String selectedTextVersionId,
        @NotBlank String selectedImageVersionId
) {
}
