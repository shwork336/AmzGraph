package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.domain.ExportPackage;
import com.snails.ecommerce.listing.infrastructure.ExportPackageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导出后台 Worker 应用服务。
 *
 * <p>负责扫描状态为 {@code PENDING} 的导出记录，并逐条委托 {@link ExportPackageService} 执行。
 * 单条导出失败不会中断本批后续记录，避免一个坏任务阻塞整个导出队列。</p>
 */
@Service
@RequiredArgsConstructor
public class ExportPackageWorkerService {

    /** 默认导出超时阈值。 */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    /** 导出记录仓储，用于扫描待处理记录。 */
    private final ExportPackageRepository exportPackageRepository;

    /** 导出应用服务，用于复用单条待处理导出的执行边界。 */
    private final ExportPackageService exportPackageService;

    /**
     * 扫描并处理一批待执行导出记录。
     *
     * @param batchSize 本批最多处理的记录数
     * @return 本批处理统计
     */
    public ExportPackageWorkerResult processPendingExports(int batchSize) {
        return processPendingExports(batchSize, DEFAULT_TIMEOUT);
    }

    /**
     * 扫描并处理一批待执行导出记录，并在执行前恢复超时记录。
     *
     * @param batchSize 本批最多处理的记录数
     * @param timeout 超时阈值
     * @return 本批处理统计
     */
    @Transactional
    public ExportPackageWorkerResult processPendingExports(int batchSize, Duration timeout) {
        if (batchSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Export worker batch size must be greater than 0");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Export worker timeout must be greater than 0");
        }

        int recovered = recoverTimedOutPackages(timeout);
        List<ExportPackage> pendingPackages = exportPackageRepository
                .findByStatusOrderByCreatedAtAscExportPackageIdAsc("PENDING", PageRequest.of(0, batchSize));
        int claimed = 0;
        int succeeded = 0;
        int failed = 0;
        for (ExportPackage exportPackage : pendingPackages) {
            try {
                if (exportPackageService.claimAndRunPendingExportPackage(exportPackage.getExportPackageId()) == null) {
                    continue;
                }
                claimed++;
                succeeded++;
            } catch (RuntimeException exception) {
                claimed++;
                failed++;
            }
        }
        return new ExportPackageWorkerResult(recovered, pendingPackages.size(), claimed, succeeded, failed);
    }

    /**
     * 将超时的 PENDING/RUNNING 导出记录恢复为失败状态。
     */
    private int recoverTimedOutPackages(Duration timeout) {
        LocalDateTime updatedBefore = LocalDateTime.now().minus(timeout);
        List<ExportPackage> timedOutPending = exportPackageRepository
                .findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc("PENDING", updatedBefore);
        List<ExportPackage> timedOutRunning = exportPackageRepository
                .findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc("RUNNING", updatedBefore);
        return failTimedOutPackages(timedOutPending, "Pending export package timed out before worker execution")
                + failTimedOutPackages(timedOutRunning, "Running export package timed out before completion");
    }

    /**
     * 批量标记超时导出记录为失败。
     */
    private int failTimedOutPackages(List<ExportPackage> exportPackages, String failureReason) {
        for (ExportPackage exportPackage : exportPackages) {
            exportPackage.setStatus("FAILED");
            exportPackage.setFailureReason(failureReason);
            exportPackage.setUpdatedAt(LocalDateTime.now());
            exportPackageRepository.save(exportPackage);
        }
        return exportPackages.size();
    }
}
