package com.snails.ecommerce.competitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 标准化竞品数据快照。
 *
 * <p>快照采用追加写入模型，同一任务和 ASIN 可以保留多个采集版本，供后续 Brief 生成和审计使用。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "competitor_snapshot")
public class CompetitorSnapshot {

    /** 快照业务 ID。 */
    @Id
    @Column(length = 64)
    private String snapshotId;

    /** 所属 Listing 任务 ID。 */
    @Column(nullable = false, length = 64)
    private String taskId;

    /** Amazon ASIN。 */
    @Column(nullable = false, length = 32)
    private String asin;

    /** 标准化竞品标题。 */
    @Column(nullable = false, columnDefinition = "text")
    private String title;

    /** 标准化 Bullet Points JSON。 */
    @Column(columnDefinition = "text")
    private String bulletPointsJson;

    /** Amazon 评分。 */
    private BigDecimal rating;

    /** 评论数量。 */
    private Long reviewCount;

    /** 评论痛点 JSON。 */
    @Column(columnDefinition = "text")
    private String reviewPainPointsJson;

    /** 关键词信号 JSON。 */
    @Column(columnDefinition = "text")
    private String keywordSignalsJson;

    /** 数据来源类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompetitorSourceType sourceType;

    /** 具体数据来源名称。 */
    @Column(nullable = false, length = 128)
    private String sourceName;

    /** 外部供应商原始响应文件键，手工数据为空。 */
    @Column(length = 512)
    private String rawResponseFileKey;

    /** 快照数据采集时间。 */
    @Column(nullable = false)
    private LocalDateTime capturedAt;

    /** 手工录入人或系统提供者标识。 */
    @Column(nullable = false, length = 128)
    private String createdBy;

    /** 数据库记录创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建快照时补齐未显式提供的采集时间和创建时间。 */
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (capturedAt == null) {
            capturedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
