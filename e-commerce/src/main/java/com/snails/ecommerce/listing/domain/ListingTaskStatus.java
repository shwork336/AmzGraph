package com.snails.ecommerce.listing.domain;

/**
 * Listing 任务主状态。
 *
 * <p>该状态描述任务整体所处阶段，文案和图片生成状态由 {@link GenerationStatus} 分别表达。</p>
 */
public enum ListingTaskStatus {
    /** 任务已创建但尚未开始处理。 */
    PENDING,
    /** 正在解析产品资料和采集竞品数据。 */
    EXTRACTING_DATA,
    /** 等待运营审核并批准 Brief。 */
    WAIT_BRIEF_APPROVE,
    /** 正在生成文案或图片资产。 */
    GENERATING,
    /** 等待运营选择最终文案版本和图片版本。 */
    WAIT_FINAL_APPROVE,
    /** 任务已归档完成。 */
    COMPLETED,
    /** 主流程失败。 */
    FAILED,
    /** 任务被取消。 */
    CANCELLED
}
