package com.snails.ecommerce.listing.domain;

/**
 * Brief 审核状态。
 */
public enum BriefStatus {
    /** 草稿状态。 */
    DRAFT,
    /** 等待运营审核。 */
    WAIT_APPROVE,
    /** 已批准，可用于生成文案和图片。 */
    APPROVED,
    /** 已驳回，需要修改或重新生成。 */
    REJECTED
}
