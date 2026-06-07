package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.api.PagedResponse;
import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.OperationAuditLogResponse;
import com.snails.ecommerce.listing.domain.OperationAuditLog;
import com.snails.ecommerce.listing.infrastructure.OperationAuditLogRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * 操作审计日志应用服务测试。
 *
 * <p>验证审计日志写入、筛选、排序和分页边界。</p>
 */
@SpringBootTest
class OperationAuditLogServiceTest {

    @Autowired
    private OperationAuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private OperationAuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        service = new OperationAuditLogService(auditLogRepository, new IdGenerator(), objectMapper);
    }

    @Test
    void recordsAuditLog() {
        OperationAuditLog auditLog = service.record(
                "EXPORT_PACKAGE_CANCELED",
                "operator@example.com",
                "EXPORT_PACKAGE",
                "export_001",
                "task_001",
                "Duplicate export request",
                Map.of("format", "ZIP"));

        assertThat(auditLog.getAuditLogId()).startsWith("audit_");
        assertThat(auditLog.getAction()).isEqualTo("EXPORT_PACKAGE_CANCELED");
        assertThat(auditLog.getOperatorId()).isEqualTo("operator@example.com");
        assertThat(auditLog.getDetailJson()).contains("\"format\":\"ZIP\"");
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }

    @Test
    void listsAuditLogsWithFiltersAndDescendingOrder() {
        service.record(
                "EXPORT_PACKAGE_CANCELED",
                "operator-a@example.com",
                "EXPORT_PACKAGE",
                "export_001",
                "task_001",
                "Duplicate export request",
                Map.of("format", "ZIP"));
        OperationAuditLog second = service.record(
                "IMAGE_ASSET_WARNING_CONFIRMED",
                "operator-b@example.com",
                "IMAGE_ASSET",
                "asset_001",
                "task_001",
                "Warning accepted",
                Map.of("complianceStatus", "WARNING"));
        service.record(
                "EXPORT_PACKAGE_CANCELED",
                "operator-a@example.com",
                "EXPORT_PACKAGE",
                "export_002",
                "task_002",
                "Duplicate export request",
                Map.of("format", "WORD"));

        PagedResponse<OperationAuditLogResponse> response = service.listAuditLogsPage(
                "task_001",
                null,
                null,
                null,
                null,
                0,
                20);

        assertThat(response.totalItems()).isEqualTo(2);
        assertThat(response.items()).extracting(OperationAuditLogResponse::auditLogId)
                .first()
                .isEqualTo(second.getAuditLogId());
    }

    @Test
    void filtersByActionOperatorAndTarget() {
        service.record(
                "EXPORT_PACKAGE_CANCELED",
                "operator-a@example.com",
                "EXPORT_PACKAGE",
                "export_001",
                "task_001",
                "Duplicate export request",
                Map.of());
        service.record(
                "IMAGE_ASSET_COMPLIANCE_APPROVED",
                "admin@example.com",
                "IMAGE_ASSET",
                "asset_001",
                "task_001",
                "Approved",
                Map.of());

        PagedResponse<OperationAuditLogResponse> response = service.listAuditLogsPage(
                null,
                "IMAGE_ASSET_COMPLIANCE_APPROVED",
                "admin@example.com",
                "IMAGE_ASSET",
                "asset_001",
                0,
                20);

        assertThat(response.totalItems()).isEqualTo(1);
        assertThat(response.items().get(0).action()).isEqualTo("IMAGE_ASSET_COMPLIANCE_APPROVED");
        assertThat(response.items().get(0).operatorId()).isEqualTo("admin@example.com");
    }

    @Test
    void returnsPagedAuditLogs() {
        service.record("EXPORT_PACKAGE_CANCELED", "operator@example.com", "EXPORT_PACKAGE", "export_001", "task_001", "first", Map.of());
        service.record("EXPORT_PACKAGE_CANCELED", "operator@example.com", "EXPORT_PACKAGE", "export_002", "task_001", "second", Map.of());
        service.record("EXPORT_PACKAGE_CANCELED", "operator@example.com", "EXPORT_PACKAGE", "export_003", "task_001", "third", Map.of());

        PagedResponse<OperationAuditLogResponse> response = service.listAuditLogsPage(
                "task_001",
                "EXPORT_PACKAGE_CANCELED",
                null,
                null,
                null,
                1,
                2);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void rejectsInvalidPageRequest() {
        assertThatThrownBy(() -> service.listAuditLogsPage(null, null, null, null, null, -1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.listAuditLogsPage(null, null, null, null, null, 0, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
