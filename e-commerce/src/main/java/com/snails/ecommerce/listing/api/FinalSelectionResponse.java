package com.snails.ecommerce.listing.api;

import java.time.LocalDateTime;

/**
 * 终审选择响应。
 *
 * <p>该 DTO 用于确认任务最终选中的文案版本和图片版本，避免直接暴露 JPA 实体。</p>
 *
 * @param taskId 任务 ID
 * @param status 任务主状态
 * @param selectedTextVersionId 最终选中的文案版本 ID
 * @param selectedImageVersionId 最终选中的图片版本 ID
 * @param updatedAt 任务更新时间
 */
public record FinalSelectionResponse(
        String taskId,
        String status,
        String selectedTextVersionId,
        String selectedImageVersionId,
        LocalDateTime updatedAt
) {
}
