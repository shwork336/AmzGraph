package com.snails.ecommerce.competitor.application;

import com.snails.ecommerce.competitor.domain.CompetitorSourceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部竞品供应商到内部快照模型之间的标准化数据。
 *
 * <p>该结构不依赖 JPA，供应商适配器必须先完成字段映射，再把数据交给业务层。</p>
 *
 * @param asin Amazon ASIN
 * @param title 标准化竞品标题
 * @param bulletPoints 标准化 Bullet Points
 * @param rating Amazon 评分
 * @param reviewCount 评论数量
 * @param reviewPainPoints 从评论中提炼的用户痛点
 * @param keywordSignals 从竞品内容和评论中提炼的关键词信号
 * @param sourceType 数据来源类型
 * @param sourceName 具体数据来源名称
 * @param rawResponseFileKey 外部供应商原始响应文件键
 * @param capturedAt 竞品数据采集时间
 */
public record CompetitorSnapshotData(
        String asin,
        String title,
        List<String> bulletPoints,
        BigDecimal rating,
        Long reviewCount,
        List<String> reviewPainPoints,
        List<String> keywordSignals,
        CompetitorSourceType sourceType,
        String sourceName,
        String rawResponseFileKey,
        LocalDateTime capturedAt
) {
}
