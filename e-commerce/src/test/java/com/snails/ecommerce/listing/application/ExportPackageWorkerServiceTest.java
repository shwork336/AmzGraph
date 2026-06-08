package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.api.ExportPackageResponse;
import com.snails.ecommerce.listing.domain.ExportFormat;
import com.snails.ecommerce.listing.domain.ExportPackage;
import com.snails.ecommerce.listing.infrastructure.ExportPackageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/**
 * 瀵煎嚭鍚庡彴 Worker 搴旂敤鏈嶅姟娴嬭瘯銆? *
 * <p>璇ユ祴璇曞浐瀹?Worker 鐨勬壒閲忔壂鎻忋€佸崟鏉℃墽琛屽鎵樺拰澶辫触闅旂杈圭晫锛屼笉渚濊禆鐪熷疄鏂囦欢瀵煎嚭銆?/p>
 */
class ExportPackageWorkerServiceTest {

    private final ExportPackageRepository exportPackageRepository =
            org.mockito.Mockito.mock(ExportPackageRepository.class);
    private final ExportPackageService exportPackageService =
            org.mockito.Mockito.mock(ExportPackageService.class);
    private final ExportPackageWorkerService service =
            new ExportPackageWorkerService(exportPackageRepository, exportPackageService);

    @Test
    void processesPendingExports() {
        ExportPackage first = pendingPackage("export_001");
        ExportPackage second = pendingPackage("export_002");
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("RUNNING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(exportPackageRepository.findByStatusOrderByCreatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(first, second));
        when(exportPackageService.claimAndRunPendingExportPackage("export_001"))
                .thenReturn(exportPackageResponse("export_001"));
        when(exportPackageService.claimAndRunPendingExportPackage("export_002"))
                .thenReturn(exportPackageResponse("export_002"));

        ExportPackageWorkerResult result = service.processPendingExports(2);

        assertThat(result.recovered()).isZero();
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(exportPackageService).claimAndRunPendingExportPackage("export_001");
        verify(exportPackageService).claimAndRunPendingExportPackage("export_002");
    }

    @Test
    void continuesWhenSingleExportFails() {
        ExportPackage first = pendingPackage("export_001");
        ExportPackage second = pendingPackage("export_002");
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("RUNNING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(exportPackageRepository.findByStatusOrderByCreatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(first, second));
        doThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "export failed"))
                .when(exportPackageService)
                .claimAndRunPendingExportPackage("export_001");
        when(exportPackageService.claimAndRunPendingExportPackage("export_002"))
                .thenReturn(exportPackageResponse("export_002"));

        ExportPackageWorkerResult result = service.processPendingExports(10);

        assertThat(result.recovered()).isZero();
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.claimed()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(exportPackageService).claimAndRunPendingExportPackage("export_002");
    }

    @Test
    void skipsExportWhenClaimFails() {
        ExportPackage first = pendingPackage("export_001");
        ExportPackage second = pendingPackage("export_002");
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("RUNNING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(exportPackageRepository.findByStatusOrderByCreatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(first, second));
        when(exportPackageService.claimAndRunPendingExportPackage("export_001"))
                .thenReturn(null);
        when(exportPackageService.claimAndRunPendingExportPackage("export_002"))
                .thenReturn(exportPackageResponse("export_002"));

        ExportPackageWorkerResult result = service.processPendingExports(10);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isZero();
    }

    @Test
    void recoversTimedOutPendingAndRunningExportsBeforeProcessing() {
        ExportPackage timedOutPending = pendingPackage("export_timeout_pending");
        ExportPackage timedOutRunning = pendingPackage("export_timeout_running");
        timedOutRunning.setStatus("RUNNING");
        ExportPackage activePending = pendingPackage("export_active_pending");
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of(timedOutPending));
        when(exportPackageRepository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                eq("RUNNING"),
                any(LocalDateTime.class)))
                .thenReturn(List.of(timedOutRunning));
        when(exportPackageRepository.findByStatusOrderByCreatedAtAscExportPackageIdAsc(
                eq("PENDING"),
                eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(activePending));
        when(exportPackageService.claimAndRunPendingExportPackage("export_active_pending"))
                .thenReturn(exportPackageResponse("export_active_pending"));

        ExportPackageWorkerResult result = service.processPendingExports(1, Duration.ofMinutes(30));

        assertThat(result.recovered()).isEqualTo(2);
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.claimed()).isEqualTo(1);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(timedOutPending.getStatus()).isEqualTo("FAILED");
        assertThat(timedOutPending.getFailureReason())
                .isEqualTo("Pending export package timed out before worker execution");
        assertThat(timedOutRunning.getStatus()).isEqualTo("FAILED");
        assertThat(timedOutRunning.getFailureReason())
                .isEqualTo("Running export package timed out before completion");
        verify(exportPackageRepository).save(timedOutPending);
        verify(exportPackageRepository).save(timedOutRunning);
        verify(exportPackageService).claimAndRunPendingExportPackage("export_active_pending");
    }

    @Test
    void rejectsInvalidBatchSize() {
        assertThatThrownBy(() -> service.processPendingExports(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsInvalidTimeout() {
        assertThatThrownBy(() -> service.processPendingExports(1, Duration.ZERO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private ExportPackage pendingPackage(String exportPackageId) {
        ExportPackage exportPackage = new ExportPackage();
        exportPackage.setExportPackageId(exportPackageId);
        exportPackage.setTaskId("task_123");
        exportPackage.setSelectedTextVersionId("text_001");
        exportPackage.setSelectedImageVersionId("image_001");
        exportPackage.setFormat(ExportFormat.ZIP);
        exportPackage.setStatus("PENDING");
        exportPackage.setIncludedAssetIdsJson("[]");
        return exportPackage;
    }

    private ExportPackageResponse exportPackageResponse(String exportPackageId) {
        return new ExportPackageResponse(
                exportPackageId,
                "task_123",
                "text_001",
                "image_001",
                "ZIP",
                "SUCCEEDED",
                "exports/task_123/" + exportPackageId + ".zip",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                LocalDateTime.of(2026, 6, 7, 12, 0),
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));
    }
}

