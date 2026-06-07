package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.OperationAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 操作审计日志仓储。
 */
public interface OperationAuditLogRepository extends JpaRepository<OperationAuditLog, String> {

    /**
     * 按目标对象查询审计日志，便于测试和后续审计详情页复用。
     */
    List<OperationAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtAscAuditLogIdAsc(
            String targetType,
            String targetId);
}
