package com.snails.ecommerce.competitor.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/**
 * 单条手工竞品快照请求。
 *
 * @param asin Amazon ASIN
 * @param title 竞品标题
 * @param bulletPoints Bullet Points
 * @param rating Amazon 评分
 * @param reviewCount 评论数量
 * @param reviewPainPoints 评论痛点
 * @param keywordSignals 关键词信号
 * @param sourceName 手工来源名称
 */
public record ManualCompetitorSnapshotRequest(
        @NotBlank String asin,
        @NotBlank String title,
        @NotNull List<@NotBlank String> bulletPoints,
        @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating,
        @PositiveOrZero Long reviewCount,
        @NotNull List<@NotBlank String> reviewPainPoints,
        @NotNull List<@NotBlank String> keywordSignals,
        String sourceName
) {
}
