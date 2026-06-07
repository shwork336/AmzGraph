package com.snails.ecommerce.listing.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图片资产响应。
 *
 * <p>该 DTO 用于输出单张图片资产的生成、尺寸和合规信息，避免直接暴露 JPA 实体。</p>
 *
 * @param assetId 图片资产 ID
 * @param imageVersionId 所属图片版本 ID
 * @param type 图片用途类型
 * @param prompt 原始 Prompt
 * @param rewrittenPrompt 改写后 Prompt
 * @param generatedImageUrl 生成图片 URL
 * @param sourceEditableFileUrl 可编辑源文件 URL
 * @param sizeProfile 尺寸配置档位
 * @param targetWidth 目标宽度
 * @param targetHeight 目标高度
 * @param complianceStatus 合规状态
 * @param complianceMethods 合规检测方法
 * @param complianceIssues 合规问题
 * @param complianceReviewedBy 人工确认人
 * @param complianceReviewedAt 人工确认时间
 * @param sortOrder 图片组内排序
 * @param createdAt 创建时间
 */
public record ImageAssetResponse(
        String assetId,
        String imageVersionId,
        String type,
        String prompt,
        String rewrittenPrompt,
        String generatedImageUrl,
        String sourceEditableFileUrl,
        String sizeProfile,
        Integer targetWidth,
        Integer targetHeight,
        String complianceStatus,
        List<String> complianceMethods,
        List<String> complianceIssues,
        String complianceReviewedBy,
        LocalDateTime complianceReviewedAt,
        Integer sortOrder,
        LocalDateTime createdAt
) {
}
