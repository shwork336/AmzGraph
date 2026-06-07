package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.application.ExportPackageWorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 导出后台 Worker 调度适配器。
 *
 * <p>该适配器默认不启用，避免测试和本地启动时自动生成导出文件。部署环境可通过
 * {@code listing.export-worker.enabled=true} 打开后台扫描。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "listing.export-worker", name = "enabled", havingValue = "true")
public class ExportPackageWorkerScheduler {

    /** 单批默认处理数量。 */
    private static final int DEFAULT_BATCH_SIZE = 20;

    /** 导出后台 Worker 应用服务。 */
    private final ExportPackageWorkerService workerService;

    /**
     * 周期扫描待处理导出记录。
     */
    @Scheduled(fixedDelayString = "${listing.export-worker.fixed-delay-ms:5000}")
    public void processPendingExports() {
        workerService.processPendingExports(DEFAULT_BATCH_SIZE);
    }
}
