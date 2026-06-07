package com.snails.ecommerce.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operation_audit_log")
/**
 * 操作审计日志。
 *
 * <p>记录运营或管理员在关键业务节点上的人工动作，用于后续统一审计查询和问题回放。</p>
 */
public class OperationAuditLog {

    /** 审计日志 ID。 */
    @Id
    @Column(length = 64)
    private String auditLogId;

    /** 操作动作，例如 EXPORT_PACKAGE_CANCELED。 */
    @Column(nullable = false, length = 80)
    private String action;

    /** 操作人 ID，当前阶段来自统一请求头或请求体回退字段。 */
    @Column(nullable = false, length = 128)
    private String operatorId;

    /** 被操作对象类型，例如 EXPORT_PACKAGE 或 IMAGE_ASSET。 */
    @Column(nullable = false, length = 80)
    private String targetType;

    /** 被操作对象 ID。 */
    @Column(nullable = false, length = 128)
    private String targetId;

    /** 关联 Listing 任务 ID。 */
    @Column(length = 64)
    private String taskId;

    /** 操作原因。 */
    @Column(columnDefinition = "text")
    private String reason;

    /** 操作上下文 JSON。 */
    @Column(columnDefinition = "text")
    private String detailJson;

    /** 创建时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 新建审计日志时自动填充创建时间。 */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
