package com.snails.ecommerce.listing.api;

import org.springframework.util.StringUtils;

/**
 * 操作人审计上下文解析工具。
 *
 * <p>当前阶段没有接入登录态，先统一使用请求头承载当前操作人，并保留请求体字段作为兼容回退。</p>
 */
final class OperatorAuditContext {

    /** 统一操作人请求头。 */
    static final String HEADER_OPERATOR_ID = "X-Operator-Id";

    private OperatorAuditContext() {
    }

    /**
     * 解析操作人，优先使用统一请求头，缺失时回退请求体中的操作人字段。
     */
    static String resolveOperator(String headerOperatorId, String bodyOperatorId) {
        if (StringUtils.hasText(headerOperatorId)) {
            return headerOperatorId;
        }
        return bodyOperatorId;
    }
}
