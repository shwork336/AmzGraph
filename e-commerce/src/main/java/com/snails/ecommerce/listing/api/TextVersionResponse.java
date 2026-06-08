package com.snails.ecommerce.listing.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文案版本响应。
 *
 * <p>该 DTO 用于输出 Listing 文案版本内容和版本关系，避免直接暴露 JPA 实体。</p>
 *
 * @param versionId 文案版本 ID
 * @param taskId 所属任务 ID
 * @param parentVersionId 父文案版本 ID
 * @param briefVersionId 生成该文案使用的 Brief 版本 ID
 * @param iterationPrompt 本次迭代附加 Prompt
 * @param title Amazon Listing 标题
 * @param bulletPoints 5 点 Bullet Points
 * @param description 产品描述
 * @param backendSearchTerms 后台搜索词
 * @param targetKeywords 目标关键词
 * @param complianceWarnings 文案合规警告
 * @param qualityScore 质量评分
 * @param selected 是否为最终选中版本
 * @param createdAt 创建时间
 */
public record TextVersionResponse(
        String versionId,
        String taskId,
        String parentVersionId,
        String briefVersionId,
        String iterationPrompt,
        String title,
        List<String> bulletPoints,
        String description,
        String backendSearchTerms,
        List<String> targetKeywords,
        List<String> complianceWarnings,
        Integer qualityScore,
        boolean selected,
        LocalDateTime createdAt
) {
}
