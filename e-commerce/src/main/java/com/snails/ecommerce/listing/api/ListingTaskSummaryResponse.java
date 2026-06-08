package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.listing.domain.ListingTask;
import java.time.LocalDateTime;

/**
 * Listing 任务列表摘要响应。
 *
 * <p>用于运营工作台任务列表，只返回列表扫描和跳转所需字段。</p>
 *
 * @param taskId 任务 ID
 * @param status 任务主状态
 * @param textStatus 文案生成状态
 * @param imageStatus 图片生成状态
 * @param briefStatus Brief 审核状态
 * @param categoryCode 类目代码
 * @param categoryTemplateId 类目模板 ID
 * @param marketplace 站点市场
 * @param language 生成语言
 * @param selectedTextVersionId 最终文案版本 ID
 * @param selectedImageVersionId 最终图片版本 ID
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record ListingTaskSummaryResponse(
        String taskId,
        String status,
        String textStatus,
        String imageStatus,
        String briefStatus,
        String categoryCode,
        String categoryTemplateId,
        String marketplace,
        String language,
        String selectedTextVersionId,
        String selectedImageVersionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 从任务实体创建列表摘要。
     */
    public static ListingTaskSummaryResponse from(ListingTask task) {
        return new ListingTaskSummaryResponse(
                task.getTaskId(),
                task.getStatus().name(),
                task.getTextStatus().name(),
                task.getImageStatus().name(),
                task.getBriefStatus().name(),
                task.getCategoryCode(),
                task.getCategoryTemplateId(),
                task.getMarketplace(),
                task.getLanguage(),
                task.getSelectedTextVersionId(),
                task.getSelectedImageVersionId(),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
