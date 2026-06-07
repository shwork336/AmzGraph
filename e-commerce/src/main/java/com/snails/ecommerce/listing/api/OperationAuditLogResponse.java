package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.listing.domain.OperationAuditLog;
import java.time.LocalDateTime;

/**
 * 操作审计日志响应。
 *
 * <p>用于管理端审计查询接口，避免直接暴露 JPA 实体。</p>
 *
 * @param auditLogId 审计日志 ID
 * @param action 操作动作
 * @param operatorId 操作人 ID
 * @param targetType 目标对象类型
 * @param targetId 目标对象 ID
 * @param taskId 关联任务 ID
 * @param reason 操作原因
 * @param detailJson 操作上下文 JSON
 * @param createdAt 创建时间
 */
public record OperationAuditLogResponse(
        String auditLogId,
        String action,
        String operatorId,
        String targetType,
        String targetId,
        String taskId,
        String reason,
        String detailJson,
        LocalDateTime createdAt
) {

    /**
     * 从审计日志实体创建响应。
     */
    public static OperationAuditLogResponse from(OperationAuditLog auditLog) {
        return new OperationAuditLogResponse(
                auditLog.getAuditLogId(),
                auditLog.getAction(),
                auditLog.getOperatorId(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getTaskId(),
                auditLog.getReason(),
                auditLog.getDetailJson(),
                auditLog.getCreatedAt());
    }
}
