package com.snails.ecommerce.listing.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图片版本响应。
 *
 * <p>该 DTO 用于输出图片组版本的生成参数、状态和版本关系，避免直接暴露 JPA 实体。</p>
 *
 * @param versionId 图片版本 ID
 * @param taskId 所属任务 ID
 * @param parentVersionId 父图片版本 ID
 * @param briefVersionId 生成该图片版本使用的 Brief 版本 ID
 * @param iterationPrompt 本次图片迭代附加 Prompt
 * @param referenceImageUrl 参考图 URL
 * @param inputProductUrls 输入产品图 URL 列表
 * @param imageProvider 图片生成供应商
 * @param imageModel 图片生成模型
 * @param generationParams 图片生成参数
 * @param status 图片生成状态
 * @param qualityScore 质量评分
 * @param selected 是否为最终选中版本
 * @param createdAt 创建时间
 */
public record ImageVersionResponse(
        String versionId,
        String taskId,
        String parentVersionId,
        String briefVersionId,
        String iterationPrompt,
        String referenceImageUrl,
        List<String> inputProductUrls,
        String imageProvider,
        String imageModel,
        String generationParams,
        String status,
        Integer qualityScore,
        boolean selected,
        LocalDateTime createdAt
) {
}
