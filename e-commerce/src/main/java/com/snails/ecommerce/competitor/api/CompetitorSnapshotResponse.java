package com.snails.ecommerce.competitor.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 竞品快照响应。
 *
 * <p>该 DTO 稳定输出标准化竞品字段和审计信息，避免直接暴露 JPA 实体。</p>
 */
public record CompetitorSnapshotResponse(
        String snapshotId,
        String taskId,
        String asin,
        String title,
        List<String> bulletPoints,
        BigDecimal rating,
        Long reviewCount,
        List<String> reviewPainPoints,
        List<String> keywordSignals,
        String sourceType,
        String sourceName,
        String rawResponseFileKey,
        LocalDateTime capturedAt,
        String createdBy,
        LocalDateTime createdAt
) {
}
