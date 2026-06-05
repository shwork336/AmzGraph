package com.snails.ecommerce.listing.domain;

/**
 * 生成子流程状态。
 *
 * <p>文案流和图片流分别记录该状态，避免一个子流程失败直接覆盖任务主状态。</p>
 */
public enum GenerationStatus {
    /** 尚未开始。 */
    NOT_STARTED,
    /** 正在运行。 */
    RUNNING,
    /** 已成功。 */
    SUCCEEDED,
    /** 部分成功，部分资产或步骤失败。 */
    PARTIAL_FAILED,
    /** 整个子流程失败。 */
    FAILED,
    /** 正在重试。 */
    RETRYING
}
