package com.snails.ecommerce.competitor.domain;

/**
 * 竞品快照数据来源。
 *
 * <p>当前阶段只创建手工快照，其他值用于稳定后续 Bright Data 与缓存适配器的数据模型。</p>
 */
public enum CompetitorSourceType {
    /** 运营手工录入。 */
    MANUAL,
    /** Bright Data 供应商采集。 */
    BRIGHT_DATA,
    /** 外部采集失败时使用的缓存快照。 */
    CACHE
}
