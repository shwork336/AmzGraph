package com.snails.ecommerce.listing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.snails.ecommerce.listing.domain.ExportFormat;
import com.snails.ecommerce.listing.domain.ExportPackage;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 瀵煎嚭浜や粯鍖呮寔涔呭寲娴嬭瘯銆? *
 * <p>楠岃瘉瀵煎嚭璁板綍淇濆瓨銆佸け璐ュ師鍥犺褰曪紝浠ュ強鎸変换鍔℃煡璇㈠鍑哄巻鍙茬殑绋冲畾鎺掑簭銆?/p>
 */
@SpringBootTest
@Transactional
class ExportPackageRepositoryTest {

    @Autowired
    private ExportPackageRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void savesExportPackage() {
        ExportPackage exportPackage = basePackage("export_001", "task_export");
        exportPackage.setFileUrl("exports/task_export/export_001.zip");
        exportPackage.setManifestUrl("exports/task_export/export_001-manifest.json");
        exportPackage.setStatus("SUCCEEDED");
        exportPackage.setIncludedAssetIdsJson("[\"asset_001\"]");

        repository.save(exportPackage);

        ExportPackage saved = repository.findById("export_001").orElseThrow();
        assertThat(saved.getTaskId()).isEqualTo("task_export");
        assertThat(saved.getFormat()).isEqualTo(ExportFormat.ZIP);
        assertThat(saved.getSelectedTextVersionId()).isEqualTo("text_001");
        assertThat(saved.getSelectedImageVersionId()).isEqualTo("image_001");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void savesFailureReason() {
        ExportPackage exportPackage = basePackage("export_failed", "task_export");
        exportPackage.setStatus("FAILED");
        exportPackage.setFailureReason("Failed to generate ZIP");

        repository.save(exportPackage);

        ExportPackage saved = repository.findById("export_failed").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getFailureReason()).isEqualTo("Failed to generate ZIP");
    }

    @Test
    void listsPackagesByCreatedAtAndExportPackageIdDescending() {
        ExportPackage first = basePackage("export_001", "task_export");
        first.setCreatedAt(LocalDateTime.of(2026, 6, 7, 10, 0));
        repository.save(first);

        ExportPackage second = basePackage("export_002", "task_export");
        second.setCreatedAt(LocalDateTime.of(2026, 6, 7, 11, 0));
        repository.save(second);

        ExportPackage sameTimeHigherId = basePackage("export_003", "task_export");
        sameTimeHigherId.setCreatedAt(LocalDateTime.of(2026, 6, 7, 11, 0));
        repository.save(sameTimeHigherId);

        ExportPackage otherTask = basePackage("export_other", "task_other");
        otherTask.setCreatedAt(LocalDateTime.of(2026, 6, 7, 12, 0));
        repository.save(otherTask);

        List<ExportPackage> packages =
                repository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc("task_export");

        assertThat(packages)
                .extracting(ExportPackage::getExportPackageId)
                .containsExactly("export_003", "export_002", "export_001");
    }

    @Test
    void listsPendingPackagesByCreatedAtAndExportPackageIdAscending() {
        ExportPackage laterPending = basePackage("export_003", "task_export");
        laterPending.setStatus("PENDING");
        saveWithCreatedAt(laterPending, LocalDateTime.of(2026, 6, 7, 12, 0));

        ExportPackage firstPending = basePackage("export_001", "task_export");
        firstPending.setStatus("PENDING");
        saveWithCreatedAt(firstPending, LocalDateTime.of(2026, 6, 7, 10, 0));

        ExportPackage sameTimeHigherId = basePackage("export_002", "task_export");
        sameTimeHigherId.setStatus("PENDING");
        saveWithCreatedAt(sameTimeHigherId, LocalDateTime.of(2026, 6, 7, 10, 0));

        ExportPackage running = basePackage("export_running", "task_export");
        running.setStatus("RUNNING");
        saveWithCreatedAt(running, LocalDateTime.of(2026, 6, 7, 9, 0));

        List<ExportPackage> packages = repository.findByStatusOrderByCreatedAtAscExportPackageIdAsc(
                "PENDING",
                PageRequest.of(0, 2));

        assertThat(packages)
                .extracting(ExportPackage::getExportPackageId)
                .containsExactly("export_001", "export_002");
    }

    @Test
    void listsTimedOutPackagesByStatusAndCreatedAtAscending() {
        ExportPackage oldPending = basePackage("export_old_pending", "task_export");
        oldPending.setStatus("PENDING");
        saveWithCreatedAt(oldPending, LocalDateTime.of(2026, 6, 7, 10, 0));

        ExportPackage sameTimeHigherId = basePackage("export_old_pending_2", "task_export");
        sameTimeHigherId.setStatus("PENDING");
        saveWithCreatedAt(sameTimeHigherId, LocalDateTime.of(2026, 6, 7, 10, 0));

        ExportPackage recentPending = basePackage("export_recent_pending", "task_export");
        recentPending.setStatus("PENDING");
        saveWithCreatedAt(recentPending, LocalDateTime.of(2026, 6, 7, 12, 0));

        ExportPackage oldRunning = basePackage("export_old_running", "task_export");
        oldRunning.setStatus("RUNNING");
        saveWithCreatedAt(oldRunning, LocalDateTime.of(2026, 6, 7, 9, 0));

        List<ExportPackage> packages = repository
                .findByStatusAndUpdatedAtBeforeOrderByUpdatedAtAscExportPackageIdAsc(
                        "PENDING",
                        LocalDateTime.of(2026, 6, 7, 11, 0));

        assertThat(packages)
                .extracting(ExportPackage::getExportPackageId)
                .containsExactly("export_old_pending", "export_old_pending_2");
    }

    @Test
    void updatesStatusWhenCurrentStatusMatches() {
        ExportPackage exportPackage = basePackage("export_claim_pending", "task_export");
        exportPackage.setStatus("PENDING");
        repository.save(exportPackage);

        int updated = repository.updateStatusWhenCurrentStatus(
                "export_claim_pending",
                "PENDING",
                "RUNNING",
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));

        assertThat(updated).isEqualTo(1);
        ExportPackage saved = repository.findById("export_claim_pending").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
        assertThat(saved.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 6, 7, 12, 1));
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 7, 12, 2));
    }

    @Test
    void doesNotUpdateStatusWhenCurrentStatusDiffers() {
        ExportPackage exportPackage = basePackage("export_claim_succeeded", "task_export");
        exportPackage.setStatus("SUCCEEDED");
        repository.save(exportPackage);

        int updated = repository.updateStatusWhenCurrentStatus(
                "export_claim_succeeded",
                "PENDING",
                "RUNNING",
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));

        assertThat(updated).isZero();
        ExportPackage saved = repository.findById("export_claim_succeeded").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
    }

    private ExportPackage basePackage(String exportPackageId, String taskId) {
        ExportPackage exportPackage = new ExportPackage();
        exportPackage.setExportPackageId(exportPackageId);
        exportPackage.setTaskId(taskId);
        exportPackage.setSelectedTextVersionId("text_001");
        exportPackage.setSelectedImageVersionId("image_001");
        exportPackage.setFormat(ExportFormat.ZIP);
        exportPackage.setStatus("SUCCEEDED");
        exportPackage.setIncludedAssetIdsJson("[]");
        return exportPackage;
    }

    private void saveWithCreatedAt(ExportPackage exportPackage, LocalDateTime createdAt) {
        ExportPackage saved = repository.save(exportPackage);
        saved.setCreatedAt(createdAt);
        saved.setUpdatedAt(createdAt);
        repository.save(saved);
    }
}

