package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.common.api.PagedResponse;
import com.snails.ecommerce.listing.api.OperationAuditLogResponse;
import com.snails.ecommerce.listing.domain.OperationAuditLog;
import com.snails.ecommerce.listing.infrastructure.OperationAuditLogRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 操作审计日志应用服务。
 *
 * <p>当前阶段只负责在关键人工操作成功后写入统一审计日志，不提供查询 API。</p>
 */
@Service
@RequiredArgsConstructor
public class OperationAuditLogService {

    /** 审计日志仓储。 */
    private final OperationAuditLogRepository auditLogRepository;

    /** 业务 ID 生成器。 */
    private final IdGenerator idGenerator;

    /** JSON 字段映射器。 */
    private final ObjectMapper objectMapper;

    /**
     * 记录一条人工操作审计日志。
     */
    @Transactional
    public OperationAuditLog record(
            String action,
            String operatorId,
            String targetType,
            String targetId,
            String taskId,
            String reason,
            Map<String, ?> detail) {
        requireAuditFields(action, operatorId, targetType, targetId);
        OperationAuditLog auditLog = new OperationAuditLog();
        auditLog.setAuditLogId(idGenerator.generate("audit"));
        auditLog.setAction(action);
        auditLog.setOperatorId(operatorId);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setTaskId(taskId);
        auditLog.setReason(reason);
        auditLog.setDetailJson(writeDetail(detail));
        return auditLogRepository.save(auditLog);
    }

    /**
     * 分页查询操作审计日志。
     *
     * <p>当前阶段筛选维度较少，复用内存筛选方式，保持与导出列表查询一致的实现复杂度。</p>
     */
    @Transactional(readOnly = true)
    public PagedResponse<OperationAuditLogResponse> listAuditLogsPage(
            String taskId,
            String action,
            String operatorId,
            String targetType,
            String targetId,
            int page,
            int size) {
        requireValidPageRequest(page, size);
        List<OperationAuditLog> filteredLogs = auditLogRepository.findAll()
                .stream()
                .filter(auditLog -> !StringUtils.hasText(taskId) || taskId.equals(auditLog.getTaskId()))
                .filter(auditLog -> !StringUtils.hasText(action) || action.equals(auditLog.getAction()))
                .filter(auditLog -> !StringUtils.hasText(operatorId) || operatorId.equals(auditLog.getOperatorId()))
                .filter(auditLog -> !StringUtils.hasText(targetType) || targetType.equals(auditLog.getTargetType()))
                .filter(auditLog -> !StringUtils.hasText(targetId) || targetId.equals(auditLog.getTargetId()))
                .sorted(Comparator
                        .comparing(OperationAuditLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OperationAuditLog::getAuditLogId)
                        .reversed())
                .toList();
        int totalItems = filteredLogs.size();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<OperationAuditLogResponse> items = filteredLogs.subList(fromIndex, toIndex)
                .stream()
                .map(OperationAuditLogResponse::from)
                .toList();
        return new PagedResponse<>(
                items,
                page,
                size,
                totalItems,
                totalPages,
                page + 1 < totalPages,
                page > 0);
    }

    /**
     * 校验审计日志关键字段，避免产生不可追踪记录。
     */
    private void requireAuditFields(String action, String operatorId, String targetType, String targetId) {
        if (!StringUtils.hasText(action)
                || !StringUtils.hasText(operatorId)
                || !StringUtils.hasText(targetType)
                || !StringUtils.hasText(targetId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Audit action, operator and target are required");
        }
    }

    /**
     * 校验分页参数。
     */
    private void requireValidPageRequest(int page, int size) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Page must be greater than or equal to 0");
        }
        if (size <= 0 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Page size must be between 1 and 100");
        }
    }

    /**
     * 序列化操作上下文。
     */
    private String writeDetail(Map<String, ?> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to write operation audit detail");
        }
    }
}
