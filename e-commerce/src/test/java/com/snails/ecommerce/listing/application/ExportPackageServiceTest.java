package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.api.PagedResponse;
import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.common.storage.LocalFileStorage;
import com.snails.ecommerce.listing.api.CancelExportPackageRequest;
import com.snails.ecommerce.listing.api.ExportPackageResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.ExportFormat;
import com.snails.ecommerce.listing.domain.ExportPackage;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.OperationAuditLog;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.listing.infrastructure.ExportPackageRepository;
import com.snails.ecommerce.listing.infrastructure.ImageAssetRepository;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.OperationAuditLogRepository;
import com.snails.ecommerce.listing.infrastructure.TextVersionRepository;
import com.snails.ecommerce.template.domain.ImageAssetType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 导出交付包应用服务测试。
 *
 * <p>使用真实 H2 仓储和临时本地文件存储，验证已完成任务的 ZIP 默认交付包生成和错误边界。</p>
 */
@SpringBootTest
@Transactional
class ExportPackageServiceTest {

    @TempDir
    Path tempDir;

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private TextVersionRepository textVersionRepository;

    @Autowired
    private ImageVersionRepository imageVersionRepository;

    @Autowired
    private ImageAssetRepository imageAssetRepository;

    @Autowired
    private ExportPackageRepository exportPackageRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private LocalFileStorage fileStorage;
    private ExportPackageService service;
    private OperationAuditLogService operationAuditLogService;

    @BeforeEach
    void setUp() {
        operationAuditLogRepository.deleteAll();
        exportPackageRepository.deleteAll();
        imageAssetRepository.deleteAll();
        imageVersionRepository.deleteAll();
        textVersionRepository.deleteAll();
        listingTaskRepository.deleteAll();
        fileStorage = new LocalFileStorage(tempDir.toString());
        operationAuditLogService = new OperationAuditLogService(
                operationAuditLogRepository,
                new IdGenerator(),
                objectMapper);
        service = new ExportPackageService(
                listingTaskRepository,
                textVersionRepository,
                imageVersionRepository,
                imageAssetRepository,
                exportPackageRepository,
                fileStorage,
                new IdGenerator(),
                objectMapper,
                operationAuditLogService);
    }

    @Test
    void exportsDefaultZipForCompletedTask() throws Exception {
        ListingTask task = saveCompletedTask("task_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        saveAsset("asset_002", task.getSelectedImageVersionId(), ImageAssetType.INFOGRAPHIC, "WARNING", 2);
        saveAsset("asset_other", "image_other", ImageAssetType.LIFESTYLE, "PASS", 1);

        ExportPackageResponse response = service.exportDefaultZip(task.getTaskId());

        assertThat(response.exportPackageId()).startsWith("export_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.format()).isEqualTo("ZIP");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.selectedTextVersionId()).isEqualTo("text_001");
        assertThat(response.selectedImageVersionId()).isEqualTo("image_001");
        assertThat(response.fileUrl()).endsWith(".zip");
        assertThat(response.manifestUrl()).endsWith(".json");
        assertThat(response.failureReason()).isNull();
        assertThat(response.includedAssetIds()).containsExactly("asset_001", "asset_002");

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        assertThat(saved.getFormat()).isEqualTo(ExportFormat.ZIP);
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(saved.getIncludedAssetIdsJson()).contains("asset_001", "asset_002");

        assertThat(zipEntries(saved.getFileUrl()))
                .contains(
                        "listing.md",
                        "manifest.json",
                        "compliance_report.md",
                        "images/001-MAIN_IMAGE-asset_001.txt",
                        "images/002-INFOGRAPHIC-asset_002.txt");
        String manifest = readStoredFile(saved.getManifestUrl());
        assertThat(manifest)
                .contains("\"taskId\" : \"task_export\"")
                .contains("\"selectedTextVersionId\" : \"text_001\"")
                .contains("\"asset_001\"")
                .contains("\"WARNING\"");
    }

    @Test
    void exportsMarkdownForCompletedTask() throws Exception {
        ListingTask task = saveCompletedTask("task_markdown_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_markdown_main", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ImageAsset warningAsset = saveAsset(
                "asset_markdown_warning",
                task.getSelectedImageVersionId(),
                ImageAssetType.INFOGRAPHIC,
                "WARNING",
                2);
        warningAsset.setComplianceReviewedBy("operator@example.com");
        warningAsset.setComplianceReviewReason("Reviewed warning for Markdown export.");
        warningAsset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(warningAsset);

        ExportPackageResponse response = service.exportMarkdown(task.getTaskId());

        assertThat(response.exportPackageId()).startsWith("export_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.format()).isEqualTo("MARKDOWN");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.selectedTextVersionId()).isEqualTo("text_001");
        assertThat(response.selectedImageVersionId()).isEqualTo("image_001");
        assertThat(response.fileUrl()).endsWith(".md");
        assertThat(response.manifestUrl()).isNull();
        assertThat(response.failureReason()).isNull();
        assertThat(response.includedAssetIds()).containsExactly("asset_markdown_main", "asset_markdown_warning");

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        assertThat(saved.getFormat()).isEqualTo(ExportFormat.MARKDOWN);
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");

        String markdown = readStoredFile(saved.getFileUrl());
        assertThat(markdown)
                .contains("# Listing Export - task_markdown_export")
                .contains("- Selected Text Version ID: text_001")
                .contains("- Selected Image Version ID: image_001")
                .contains("# Wireless CarPlay Stereo for Amazon US")
                .contains("- Wireless CarPlay")
                .contains("A review-ready car stereo listing.")
                .contains("### asset_markdown_main")
                .contains("### asset_markdown_warning")
                .contains("- Compliance Status: WARNING")
                .contains("- Reviewed By: operator@example.com")
                .contains("- Review Reason: Reviewed warning for Markdown export.");
    }

    @Test
    void exportsExcelForCompletedTask() throws Exception {
        ListingTask task = saveCompletedTask("task_excel_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_excel_main", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ImageAsset warningAsset = saveAsset(
                "asset_excel_warning",
                task.getSelectedImageVersionId(),
                ImageAssetType.INFOGRAPHIC,
                "WARNING",
                2);
        warningAsset.setComplianceReviewedBy("operator@example.com");
        warningAsset.setComplianceReviewReason("Reviewed warning for Excel export.");
        warningAsset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(warningAsset);

        ExportPackageResponse response = service.exportExcel(task.getTaskId());

        assertThat(response.exportPackageId()).startsWith("export_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.format()).isEqualTo("EXCEL");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.selectedTextVersionId()).isEqualTo("text_001");
        assertThat(response.selectedImageVersionId()).isEqualTo("image_001");
        assertThat(response.fileUrl()).endsWith(".xlsx");
        assertThat(response.manifestUrl()).isNull();
        assertThat(response.failureReason()).isNull();
        assertThat(response.includedAssetIds()).containsExactly("asset_excel_main", "asset_excel_warning");

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        assertThat(saved.getFormat()).isEqualTo(ExportFormat.EXCEL);
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(zipEntries(saved.getFileUrl()))
                .contains(
                        "[Content_Types].xml",
                        "_rels/.rels",
                        "xl/workbook.xml",
                        "xl/_rels/workbook.xml.rels",
                        "xl/worksheets/sheet1.xml",
                        "xl/worksheets/sheet2.xml",
                        "xl/worksheets/sheet3.xml");

        String workbook = readZipEntry(saved.getFileUrl(), "xl/workbook.xml");
        String taskSheet = readZipEntry(saved.getFileUrl(), "xl/worksheets/sheet1.xml");
        String listingSheet = readZipEntry(saved.getFileUrl(), "xl/worksheets/sheet2.xml");
        String imagesSheet = readZipEntry(saved.getFileUrl(), "xl/worksheets/sheet3.xml");
        assertThat(workbook)
                .contains("name=\"Task\"")
                .contains("name=\"Listing\"")
                .contains("name=\"Images\"");
        assertThat(taskSheet)
                .contains("task_excel_export")
                .contains("Selected Text Version ID")
                .contains("text_001")
                .contains("image_001");
        assertThat(listingSheet)
                .contains("Wireless CarPlay Stereo for Amazon US")
                .contains("Wireless CarPlay")
                .contains("wireless carplay stereo")
                .contains("car stereo");
        assertThat(imagesSheet)
                .contains("asset_excel_main")
                .contains("asset_excel_warning")
                .contains("WARNING")
                .contains("operator@example.com")
                .contains("Reviewed warning for Excel export.");
    }

    @Test
    void exportsWordForCompletedTask() throws Exception {
        ListingTask task = saveCompletedTask("task_word_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_word_main", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ImageAsset warningAsset = saveAsset(
                "asset_word_warning",
                task.getSelectedImageVersionId(),
                ImageAssetType.INFOGRAPHIC,
                "WARNING",
                2);
        warningAsset.setComplianceReviewedBy("operator@example.com");
        warningAsset.setComplianceReviewReason("Reviewed warning for Word export.");
        warningAsset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(warningAsset);

        ExportPackageResponse response = service.exportWord(task.getTaskId());

        assertThat(response.exportPackageId()).startsWith("export_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.format()).isEqualTo("WORD");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.selectedTextVersionId()).isEqualTo("text_001");
        assertThat(response.selectedImageVersionId()).isEqualTo("image_001");
        assertThat(response.fileUrl()).endsWith(".docx");
        assertThat(response.manifestUrl()).isNull();
        assertThat(response.failureReason()).isNull();
        assertThat(response.includedAssetIds()).containsExactly("asset_word_main", "asset_word_warning");

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        assertThat(saved.getFormat()).isEqualTo(ExportFormat.WORD);
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(zipEntries(saved.getFileUrl()))
                .contains(
                        "[Content_Types].xml",
                        "_rels/.rels",
                        "word/document.xml");

        String document = readZipEntry(saved.getFileUrl(), "word/document.xml");
        assertThat(document)
                .contains("Listing Export - task_word_export")
                .contains("Selected Text Version ID: text_001")
                .contains("Selected Image Version ID: image_001")
                .contains("Wireless CarPlay Stereo for Amazon US")
                .contains("- Wireless CarPlay")
                .contains("wireless carplay stereo")
                .contains("asset_word_main")
                .contains("asset_word_warning")
                .contains("Compliance Status: WARNING")
                .contains("operator@example.com")
                .contains("Reviewed warning for Word export.");
    }

    @Test
    void createsPendingExportPackageWithDefaultZipFormat() {
        ListingTask task = saveCompletedTask("task_pending_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_pending_main", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);

        ExportPackageResponse response = service.createPendingExportPackage(task.getTaskId(), null);

        assertThat(response.exportPackageId()).startsWith("export_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.format()).isEqualTo("ZIP");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.fileUrl()).isNull();
        assertThat(response.manifestUrl()).isNull();
        assertThat(response.failureReason()).isNull();
        assertThat(response.includedAssetIds()).containsExactly("asset_pending_main");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.startedAt()).isNull();
        assertThat(response.updatedAt()).isNotNull();

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        assertThat(saved.getSelectedTextVersionId()).isEqualTo("text_001");
        assertThat(saved.getSelectedImageVersionId()).isEqualTo("image_001");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getStartedAt()).isNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void createsPendingExportPackageWithRequestedFormat() {
        ListingTask task = saveCompletedTask("task_pending_markdown_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_pending_markdown", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);

        ExportPackageResponse response = service.createPendingExportPackage(task.getTaskId(), "MARKDOWN");

        assertThat(response.format()).isEqualTo("MARKDOWN");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void runsPendingExportPackage() {
        ListingTask task = saveCompletedTask("task_run_pending_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_run_pending", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse pending = service.createPendingExportPackage(task.getTaskId(), "EXCEL");

        ExportPackageResponse response = service.runPendingExportPackage(pending.exportPackageId());

        assertThat(response.exportPackageId()).isEqualTo(pending.exportPackageId());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.format()).isEqualTo("EXCEL");
        assertThat(response.fileUrl()).endsWith(".xlsx");
        assertThat(response.manifestUrl()).isNull();
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        ExportPackage saved = exportPackageRepository.findById(pending.exportPackageId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(saved.getFileUrl()).endsWith(".xlsx");
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void returnsSucceededExportPackageWithoutRewritingFiles() {
        ListingTask task = saveCompletedTask("task_run_idempotent_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_run_idempotent", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse first = service.createPendingExportPackage(task.getTaskId(), "EXCEL");
        ExportPackageResponse succeeded = service.runPendingExportPackage(first.exportPackageId());

        ExportPackageResponse repeated = service.runPendingExportPackage(first.exportPackageId());

        assertThat(repeated.status()).isEqualTo("SUCCEEDED");
        assertThat(repeated.fileUrl()).isEqualTo(succeeded.fileUrl());
        assertThat(repeated.manifestUrl()).isEqualTo(succeeded.manifestUrl());
    }

    @Test
    void repairsRunningExportPackageWithCompleteOutput() {
        ListingTask task = saveCompletedTask("task_repair_running_output");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_repair_running", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse pending = service.createPendingExportPackage(task.getTaskId(), "ZIP");
        ExportPackage exportPackage = exportPackageRepository.findById(pending.exportPackageId()).orElseThrow();
        exportPackage.setStatus("RUNNING");
        exportPackage.setFileUrl("exports/task_repair_running_output/zip/existing.zip");
        exportPackage.setManifestUrl("exports/task_repair_running_output/manifest/existing-manifest.json");
        exportPackageRepository.save(exportPackage);

        ExportPackageResponse response = service.runPendingExportPackage(pending.exportPackageId());

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.fileUrl()).isEqualTo("exports/task_repair_running_output/zip/existing.zip");
        assertThat(response.manifestUrl()).isEqualTo("exports/task_repair_running_output/manifest/existing-manifest.json");
        ExportPackage saved = exportPackageRepository.findById(pending.exportPackageId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void rejectsRunningExportPackageWithoutCompleteOutput() {
        ListingTask task = saveCompletedTask("task_running_without_output");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_running_without_output", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse pending = service.createPendingExportPackage(task.getTaskId(), "MARKDOWN");
        ExportPackage exportPackage = exportPackageRepository.findById(pending.exportPackageId()).orElseThrow();
        exportPackage.setStatus("RUNNING");
        exportPackageRepository.save(exportPackage);

        assertThatThrownBy(() -> service.runPendingExportPackage(pending.exportPackageId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void claimAndRunsPendingExportPackage() {
        ListingTask task = saveCompletedTask("task_claim_run_pending_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_claim_run_pending", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse pending = service.createPendingExportPackage(task.getTaskId(), "WORD");

        ExportPackageResponse response = service.claimAndRunPendingExportPackage(pending.exportPackageId());

        assertThat(response.exportPackageId()).isEqualTo(pending.exportPackageId());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.format()).isEqualTo("WORD");
        assertThat(response.fileUrl()).endsWith(".docx");
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void returnsNullWhenClaimingNonPendingExportPackage() {
        ExportPackage exportPackage = saveExportPackage(
                "export_claim_succeeded",
                "task_claim_invalid",
                ExportFormat.ZIP,
                "SUCCEEDED");

        ExportPackageResponse response = service.claimAndRunPendingExportPackage(exportPackage.getExportPackageId());

        assertThat(response).isNull();
    }

    @Test
    void rejectsRunningNonPendingExportPackage() {
        ExportPackage exportPackage = saveExportPackage(
                "export_not_pending",
                "task_run_invalid",
                ExportFormat.ZIP,
                "SUCCEEDED");

        assertThatThrownBy(() -> service.runPendingExportPackage(exportPackage.getExportPackageId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsRunningPendingExportWhenFinalSelectionChanged() {
        ListingTask task = saveCompletedTask("task_run_stale_pending_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_run_stale_pending", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse pending = service.createPendingExportPackage(task.getTaskId(), "ZIP");

        TextVersion newTextVersion = saveTextVersion("text_002", task.getTaskId());
        task.setSelectedTextVersionId(newTextVersion.getVersionId());
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.runPendingExportPackage(pending.exportPackageId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);

        ExportPackage saved = exportPackageRepository.findById(pending.exportPackageId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getFailureReason()).contains("final selection");
    }

    @Test
    void cancelsPendingExportPackage() {
        ListingTask task = saveCompletedTask("task_cancel_pending_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_cancel_pending", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackageResponse pending = service.createPendingExportPackage(task.getTaskId(), "ZIP");

        ExportPackageResponse response = service.cancelPendingExportPackage(
                pending.exportPackageId(),
                new CancelExportPackageRequest("ops-user", "Duplicate export request"));

        assertThat(response.exportPackageId()).isEqualTo(pending.exportPackageId());
        assertThat(response.status()).isEqualTo("CANCELED");
        assertThat(response.fileUrl()).isNull();
        assertThat(response.manifestUrl()).isNull();
        assertThat(response.failureReason()).isNull();
        assertThat(response.canceledBy()).isEqualTo("ops-user");
        assertThat(response.cancelReason()).isEqualTo("Duplicate export request");
        assertThat(response.canceledAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        ExportPackage saved = exportPackageRepository.findById(pending.exportPackageId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("CANCELED");
        assertThat(saved.getCanceledBy()).isEqualTo("ops-user");
        assertThat(saved.getCancelReason()).isEqualTo("Duplicate export request");
        assertThat(saved.getCanceledAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        List<OperationAuditLog> auditLogs = operationAuditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscAuditLogIdAsc("EXPORT_PACKAGE", pending.exportPackageId());
        assertThat(auditLogs).hasSize(1);
        OperationAuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("EXPORT_PACKAGE_CANCELED");
        assertThat(auditLog.getOperatorId()).isEqualTo("ops-user");
        assertThat(auditLog.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(auditLog.getReason()).isEqualTo("Duplicate export request");
        assertThat(auditLog.getDetailJson()).contains("\"format\":\"ZIP\"");
    }

    @Test
    void rejectsCancelingNonPendingExportPackage() {
        ExportPackage exportPackage = saveExportPackage(
                "export_cancel_succeeded",
                "task_cancel_invalid",
                ExportFormat.ZIP,
                "SUCCEEDED");

        assertThatThrownBy(() -> service.cancelPendingExportPackage(
                exportPackage.getExportPackageId(),
                new CancelExportPackageRequest("ops-user", "No longer needed")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
        assertThat(operationAuditLogRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsCancelingMissingExportPackage() {
        assertThatThrownBy(() -> service.cancelPendingExportPackage(
                "export_cancel_missing",
                new CancelExportPackageRequest("ops-user", "No longer needed")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsBlankCancelAuditRequest() {
        assertThatThrownBy(() -> service.cancelPendingExportPackage(
                "export_cancel_missing",
                new CancelExportPackageRequest("ops-user", " ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void getsExportPackage() {
        ExportPackage exportPackage = new ExportPackage();
        exportPackage.setExportPackageId("export_query");
        exportPackage.setTaskId("task_export");
        exportPackage.setSelectedTextVersionId("text_001");
        exportPackage.setSelectedImageVersionId("image_001");
        exportPackage.setFormat(ExportFormat.ZIP);
        exportPackage.setStatus("FAILED");
        exportPackage.setFailureReason("zip failed");
        exportPackage.setIncludedAssetIdsJson("[\"asset_001\"]");
        exportPackageRepository.save(exportPackage);

        ExportPackageResponse response = service.getExportPackage("export_query");

        assertThat(response.exportPackageId()).isEqualTo("export_query");
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).isEqualTo("zip failed");
        assertThat(response.includedAssetIds()).containsExactly("asset_001");
    }

    @Test
    void listsExportPackagesByTask() {
        ListingTask task = saveCompletedTask("task_list_exports");
        saveExportPackage("export_001", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        ExportPackage failedPackage = saveExportPackage("export_002", task.getTaskId(), ExportFormat.MARKDOWN, "FAILED");
        failedPackage.setFailureReason("markdown failed");
        exportPackageRepository.save(failedPackage);
        saveExportPackage("export_other", "task_other", ExportFormat.EXCEL, "SUCCEEDED");

        List<ExportPackageResponse> responses = service.listExportPackages(task.getTaskId());

        assertThat(responses)
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_002", "export_001");
        assertThat(responses)
                .extracting(ExportPackageResponse::format)
                .containsExactly("MARKDOWN", "ZIP");
        assertThat(responses.get(0).failureReason()).isEqualTo("markdown failed");
    }

    @Test
    void listsExportPackagesFilteredByFormat() {
        ListingTask task = saveCompletedTask("task_list_exports_by_format");
        saveExportPackage("export_zip", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        saveExportPackage("export_markdown", task.getTaskId(), ExportFormat.MARKDOWN, "SUCCEEDED");
        saveExportPackage("export_word", task.getTaskId(), ExportFormat.WORD, "FAILED");

        List<ExportPackageResponse> responses = service.listExportPackages(task.getTaskId(), "MARKDOWN", null);

        assertThat(responses)
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_markdown");
        assertThat(responses.get(0).format()).isEqualTo("MARKDOWN");
    }

    @Test
    void listsExportPackagesFilteredByStatus() {
        ListingTask task = saveCompletedTask("task_list_exports_by_status");
        saveExportPackage("export_succeeded", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        saveExportPackage("export_failed", task.getTaskId(), ExportFormat.EXCEL, "FAILED");

        List<ExportPackageResponse> responses = service.listExportPackages(task.getTaskId(), null, "FAILED");

        assertThat(responses)
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_failed");
        assertThat(responses.get(0).status()).isEqualTo("FAILED");
    }

    @Test
    void listsExportPackagesFilteredByFormatAndStatus() {
        ListingTask task = saveCompletedTask("task_list_exports_by_format_status");
        saveExportPackage("export_markdown_succeeded", task.getTaskId(), ExportFormat.MARKDOWN, "SUCCEEDED");
        saveExportPackage("export_markdown_failed", task.getTaskId(), ExportFormat.MARKDOWN, "FAILED");
        saveExportPackage("export_word_failed", task.getTaskId(), ExportFormat.WORD, "FAILED");

        List<ExportPackageResponse> responses = service.listExportPackages(task.getTaskId(), "MARKDOWN", "FAILED");

        assertThat(responses)
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_markdown_failed");
    }

    @Test
    void listsExportPackagesTreatsBlankFiltersAsAbsent() {
        ListingTask task = saveCompletedTask("task_list_exports_blank_filters");
        saveExportPackage("export_zip", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        saveExportPackage("export_word", task.getTaskId(), ExportFormat.WORD, "FAILED");

        List<ExportPackageResponse> responses = service.listExportPackages(task.getTaskId(), " ", "");

        assertThat(responses)
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactlyInAnyOrder("export_word", "export_zip");
    }

    @Test
    void rejectsListingExportPackagesWithInvalidFormat() {
        ListingTask task = saveCompletedTask("task_invalid_export_format");

        assertThatThrownBy(() -> service.listExportPackages(task.getTaskId(), "PDF", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void listsExportPackagesPage() {
        ListingTask task = saveCompletedTask("task_list_exports_page");
        saveExportPackage("export_001", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        saveExportPackage("export_002", task.getTaskId(), ExportFormat.MARKDOWN, "SUCCEEDED");
        saveExportPackage("export_003", task.getTaskId(), ExportFormat.EXCEL, "FAILED");

        PagedResponse<ExportPackageResponse> response = service.listExportPackagesPage(
                task.getTaskId(),
                null,
                null,
                0,
                2);

        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.hasPrevious()).isFalse();
        assertThat(response.items())
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_003", "export_002");
    }

    @Test
    void listsExportPackagesSecondPage() {
        ListingTask task = saveCompletedTask("task_list_exports_second_page");
        saveExportPackage("export_001", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        saveExportPackage("export_002", task.getTaskId(), ExportFormat.MARKDOWN, "SUCCEEDED");
        saveExportPackage("export_003", task.getTaskId(), ExportFormat.EXCEL, "FAILED");

        PagedResponse<ExportPackageResponse> response = service.listExportPackagesPage(
                task.getTaskId(),
                null,
                null,
                1,
                2);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalItems()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
        assertThat(response.items())
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_001");
    }

    @Test
    void listsExportPackagesPageWithFilters() {
        ListingTask task = saveCompletedTask("task_list_exports_page_filters");
        saveExportPackage("export_zip_failed", task.getTaskId(), ExportFormat.ZIP, "FAILED");
        saveExportPackage("export_zip_succeeded", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");
        saveExportPackage("export_word_failed", task.getTaskId(), ExportFormat.WORD, "FAILED");

        PagedResponse<ExportPackageResponse> response = service.listExportPackagesPage(
                task.getTaskId(),
                "ZIP",
                "FAILED",
                0,
                10);

        assertThat(response.totalItems()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items())
                .extracting(ExportPackageResponse::exportPackageId)
                .containsExactly("export_zip_failed");
    }

    @Test
    void listsEmptyExportPackagesPageWhenPageIsOutOfRange() {
        ListingTask task = saveCompletedTask("task_list_exports_page_out_of_range");
        saveExportPackage("export_001", task.getTaskId(), ExportFormat.ZIP, "SUCCEEDED");

        PagedResponse<ExportPackageResponse> response = service.listExportPackagesPage(
                task.getTaskId(),
                null,
                null,
                2,
                10);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.hasPrevious()).isTrue();
    }

    @Test
    void rejectsExportPackagePageWhenPageIsNegative() {
        ListingTask task = saveCompletedTask("task_negative_page");

        assertThatThrownBy(() -> service.listExportPackagesPage(task.getTaskId(), null, null, -1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsExportPackagePageWhenSizeIsInvalid() {
        ListingTask task = saveCompletedTask("task_invalid_page_size");

        assertThatThrownBy(() -> service.listExportPackagesPage(task.getTaskId(), null, null, 0, 101))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void listsEmptyExportPackagesForTaskWithoutExports() {
        ListingTask task = saveCompletedTask("task_empty_exports");

        List<ExportPackageResponse> responses = service.listExportPackages(task.getTaskId());

        assertThat(responses).isEmpty();
    }

    @Test
    void rejectsListingExportPackagesWhenTaskDoesNotExist() {
        assertThatThrownBy(() -> service.listExportPackages("task_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsExportWhenTaskDoesNotExist() {
        assertThatThrownBy(() -> service.exportDefaultZip("task_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsExportWhenTaskIsNotCompleted() {
        ListingTask task = saveCompletedTask("task_not_completed");
        task.setStatus(ListingTaskStatus.WAIT_FINAL_APPROVE);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.exportDefaultZip(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsExportWhenFinalSelectionIsMissing() {
        ListingTask task = saveCompletedTask("task_missing_selection");
        task.setSelectedTextVersionId(null);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.exportDefaultZip(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsExportWhenSelectedTextVersionDoesNotExist() {
        ListingTask task = saveCompletedTask("task_missing_text");
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());

        assertThatThrownBy(() -> service.exportDefaultZip(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsExportWhenSelectedImageVersionDoesNotExist() {
        ListingTask task = saveCompletedTask("task_missing_image");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());

        assertThatThrownBy(() -> service.exportDefaultZip(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsExportWhenFinalImageVersionContainsFailedAsset() {
        ListingTask task = saveCompletedTask("task_failed_asset_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_failed", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "FAIL", 1);

        assertThatThrownBy(() -> service.exportDefaultZip(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .isEmpty();
    }

    @Test
    void exportsFailedAssetWhenAdminWaiverExists() throws Exception {
        ListingTask task = saveCompletedTask("task_waived_failed_asset_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        ImageAsset asset = saveAsset(
                "asset_waived_failed",
                task.getSelectedImageVersionId(),
                ImageAssetType.MAIN_IMAGE,
                "FAIL",
                1);
        asset.setComplianceReviewedBy("admin@example.com");
        asset.setComplianceReviewReason("Admin confirmed this asset is acceptable for export.");
        asset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(asset);

        ExportPackageResponse response = service.exportDefaultZip(task.getTaskId());

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        String manifest = readStoredFile(saved.getManifestUrl());
        assertThat(manifest)
                .contains("\"complianceStatus\" : \"FAIL\"")
                .contains("\"complianceReviewedBy\" : \"admin@example.com\"")
                .contains("\"complianceReviewReason\" : \"Admin confirmed this asset is acceptable for export.\"");
        assertThat(zipEntries(saved.getFileUrl()))
                .contains("compliance_report.md");
    }

    @Test
    void exportsMarkdownFailedAssetWhenAdminWaiverExists() throws Exception {
        ListingTask task = saveCompletedTask("task_markdown_waived_failed");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        ImageAsset asset = saveAsset(
                "asset_markdown_waived_failed",
                task.getSelectedImageVersionId(),
                ImageAssetType.MAIN_IMAGE,
                "FAIL",
                1);
        asset.setComplianceReviewedBy("admin@example.com");
        asset.setComplianceReviewReason("Admin approved this asset for Markdown export.");
        asset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(asset);

        ExportPackageResponse response = service.exportMarkdown(task.getTaskId());

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        String markdown = readStoredFile(saved.getFileUrl());
        assertThat(response.format()).isEqualTo("MARKDOWN");
        assertThat(markdown)
                .contains("### asset_markdown_waived_failed")
                .contains("- Compliance Status: FAIL")
                .contains("- Reviewed By: admin@example.com")
                .contains("- Review Reason: Admin approved this asset for Markdown export.");
    }

    @Test
    void exportsExcelFailedAssetWhenAdminWaiverExists() throws Exception {
        ListingTask task = saveCompletedTask("task_excel_waived_failed");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        ImageAsset asset = saveAsset(
                "asset_excel_waived_failed",
                task.getSelectedImageVersionId(),
                ImageAssetType.MAIN_IMAGE,
                "FAIL",
                1);
        asset.setComplianceReviewedBy("admin@example.com");
        asset.setComplianceReviewReason("Admin approved this asset for Excel export.");
        asset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(asset);

        ExportPackageResponse response = service.exportExcel(task.getTaskId());

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        String imagesSheet = readZipEntry(saved.getFileUrl(), "xl/worksheets/sheet3.xml");
        assertThat(response.format()).isEqualTo("EXCEL");
        assertThat(imagesSheet)
                .contains("asset_excel_waived_failed")
                .contains("FAIL")
                .contains("admin@example.com")
                .contains("Admin approved this asset for Excel export.");
    }

    @Test
    void exportsWordFailedAssetWhenAdminWaiverExists() throws Exception {
        ListingTask task = saveCompletedTask("task_word_waived_failed");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        ImageAsset asset = saveAsset(
                "asset_word_waived_failed",
                task.getSelectedImageVersionId(),
                ImageAssetType.MAIN_IMAGE,
                "FAIL",
                1);
        asset.setComplianceReviewedBy("admin@example.com");
        asset.setComplianceReviewReason("Admin approved this asset for Word export.");
        asset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(asset);

        ExportPackageResponse response = service.exportWord(task.getTaskId());

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        String document = readZipEntry(saved.getFileUrl(), "word/document.xml");
        assertThat(response.format()).isEqualTo("WORD");
        assertThat(document)
                .contains("asset_word_waived_failed")
                .contains("Compliance Status: FAIL")
                .contains("admin@example.com")
                .contains("Admin approved this asset for Word export.");
    }

    @Test
    void exportsConfirmedWarningReviewInformation() throws Exception {
        ListingTask task = saveCompletedTask("task_confirmed_warning_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        ImageAsset asset = saveAsset(
                "asset_confirmed_warning",
                task.getSelectedImageVersionId(),
                ImageAssetType.INFOGRAPHIC,
                "WARNING",
                1);
        asset.setComplianceReviewedBy("operator@example.com");
        asset.setComplianceReviewReason("Reviewed warning and accepted for final export.");
        asset.setComplianceReviewedAt(java.time.LocalDateTime.of(2026, 6, 7, 12, 0));
        imageAssetRepository.save(asset);

        ExportPackageResponse response = service.exportDefaultZip(task.getTaskId());

        ExportPackage saved = exportPackageRepository.findById(response.exportPackageId()).orElseThrow();
        String manifest = readStoredFile(saved.getManifestUrl());
        String complianceReport = readZipEntry(saved.getFileUrl(), "compliance_report.md");
        assertThat(manifest)
                .contains("\"complianceStatus\" : \"WARNING\"")
                .contains("\"complianceReviewedBy\" : \"operator@example.com\"")
                .contains("\"complianceReviewReason\" : \"Reviewed warning and accepted for final export.\"");
        assertThat(complianceReport)
                .contains("Reviewed By: operator@example.com")
                .contains("Review Reason: Reviewed warning and accepted for final export.");
    }

    @Test
    void createsMultipleExportRecordsForSameTask() {
        ListingTask task = saveCompletedTask("task_repeat_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);

        ExportPackageResponse first = service.exportDefaultZip(task.getTaskId());
        ExportPackageResponse second = service.exportDefaultZip(task.getTaskId());

        assertThat(first.exportPackageId()).isNotEqualTo(second.exportPackageId());
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void createsMultipleMarkdownExportRecordsForSameTask() {
        ListingTask task = saveCompletedTask("task_repeat_markdown_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);

        ExportPackageResponse first = service.exportMarkdown(task.getTaskId());
        ExportPackageResponse second = service.exportMarkdown(task.getTaskId());

        assertThat(first.exportPackageId()).isNotEqualTo(second.exportPackageId());
        assertThat(first.format()).isEqualTo("MARKDOWN");
        assertThat(second.format()).isEqualTo("MARKDOWN");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void createsMultipleExcelExportRecordsForSameTask() {
        ListingTask task = saveCompletedTask("task_repeat_excel_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);

        ExportPackageResponse first = service.exportExcel(task.getTaskId());
        ExportPackageResponse second = service.exportExcel(task.getTaskId());

        assertThat(first.exportPackageId()).isNotEqualTo(second.exportPackageId());
        assertThat(first.format()).isEqualTo("EXCEL");
        assertThat(second.format()).isEqualTo("EXCEL");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void createsMultipleWordExportRecordsForSameTask() {
        ListingTask task = saveCompletedTask("task_repeat_word_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);

        ExportPackageResponse first = service.exportWord(task.getTaskId());
        ExportPackageResponse second = service.exportWord(task.getTaskId());

        assertThat(first.exportPackageId()).isNotEqualTo(second.exportPackageId());
        assertThat(first.format()).isEqualTo("WORD");
        assertThat(second.format()).isEqualTo("WORD");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void rejectsMarkdownExportWhenTaskIsNotCompleted() {
        ListingTask task = saveCompletedTask("task_markdown_not_completed");
        task.setStatus(ListingTaskStatus.WAIT_FINAL_APPROVE);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.exportMarkdown(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsExcelExportWhenTaskIsNotCompleted() {
        ListingTask task = saveCompletedTask("task_excel_not_completed");
        task.setStatus(ListingTaskStatus.WAIT_FINAL_APPROVE);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.exportExcel(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsWordExportWhenTaskIsNotCompleted() {
        ListingTask task = saveCompletedTask("task_word_not_completed");
        task.setStatus(ListingTaskStatus.WAIT_FINAL_APPROVE);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.exportWord(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsMarkdownExportWhenFinalImageVersionContainsFailedAsset() {
        ListingTask task = saveCompletedTask("task_markdown_failed_asset");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_markdown_failed", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "FAIL", 1);

        assertThatThrownBy(() -> service.exportMarkdown(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .isEmpty();
    }

    @Test
    void rejectsExcelExportWhenFinalImageVersionContainsFailedAsset() {
        ListingTask task = saveCompletedTask("task_excel_failed_asset");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_excel_failed", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "FAIL", 1);

        assertThatThrownBy(() -> service.exportExcel(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .isEmpty();
    }

    @Test
    void rejectsWordExportWhenFinalImageVersionContainsFailedAsset() {
        ListingTask task = saveCompletedTask("task_word_failed_asset");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_word_failed", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "FAIL", 1);

        assertThatThrownBy(() -> service.exportWord(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .isEmpty();
    }

    @Test
    void retriesFailedZipExportPackage() {
        ListingTask task = saveCompletedTask("task_retry_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackage failedPackage = saveExportPackage(
                "export_failed",
                task.getTaskId(),
                ExportFormat.ZIP,
                "FAILED");
        failedPackage.setFailureReason("zip failed");
        exportPackageRepository.save(failedPackage);

        ExportPackageResponse response = service.retryExportPackage("export_failed");

        assertThat(response.exportPackageId()).isNotEqualTo("export_failed");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.format()).isEqualTo("ZIP");

        ExportPackage original = exportPackageRepository.findById("export_failed").orElseThrow();
        assertThat(original.getStatus()).isEqualTo("FAILED");
        assertThat(original.getFailureReason()).isEqualTo("zip failed");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void retriesFailedMarkdownExportPackage() {
        ListingTask task = saveCompletedTask("task_retry_markdown_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackage failedPackage = saveExportPackage(
                "export_markdown_failed",
                task.getTaskId(),
                ExportFormat.MARKDOWN,
                "FAILED");
        failedPackage.setFailureReason("markdown failed");
        exportPackageRepository.save(failedPackage);

        ExportPackageResponse response = service.retryExportPackage("export_markdown_failed");

        assertThat(response.exportPackageId()).isNotEqualTo("export_markdown_failed");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.format()).isEqualTo("MARKDOWN");
        assertThat(response.fileUrl()).endsWith(".md");
        assertThat(response.manifestUrl()).isNull();

        ExportPackage original = exportPackageRepository.findById("export_markdown_failed").orElseThrow();
        assertThat(original.getStatus()).isEqualTo("FAILED");
        assertThat(original.getFailureReason()).isEqualTo("markdown failed");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void retriesFailedExcelExportPackage() {
        ListingTask task = saveCompletedTask("task_retry_excel_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackage failedPackage = saveExportPackage(
                "export_excel_failed",
                task.getTaskId(),
                ExportFormat.EXCEL,
                "FAILED");
        failedPackage.setFailureReason("excel failed");
        exportPackageRepository.save(failedPackage);

        ExportPackageResponse response = service.retryExportPackage("export_excel_failed");

        assertThat(response.exportPackageId()).isNotEqualTo("export_excel_failed");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.format()).isEqualTo("EXCEL");
        assertThat(response.fileUrl()).endsWith(".xlsx");
        assertThat(response.manifestUrl()).isNull();

        ExportPackage original = exportPackageRepository.findById("export_excel_failed").orElseThrow();
        assertThat(original.getStatus()).isEqualTo("FAILED");
        assertThat(original.getFailureReason()).isEqualTo("excel failed");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void retriesFailedWordExportPackage() {
        ListingTask task = saveCompletedTask("task_retry_word_export");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_001", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "PASS", 1);
        ExportPackage failedPackage = saveExportPackage(
                "export_word_failed",
                task.getTaskId(),
                ExportFormat.WORD,
                "FAILED");
        failedPackage.setFailureReason("word failed");
        exportPackageRepository.save(failedPackage);

        ExportPackageResponse response = service.retryExportPackage("export_word_failed");

        assertThat(response.exportPackageId()).isNotEqualTo("export_word_failed");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.format()).isEqualTo("WORD");
        assertThat(response.fileUrl()).endsWith(".docx");
        assertThat(response.manifestUrl()).isNull();

        ExportPackage original = exportPackageRepository.findById("export_word_failed").orElseThrow();
        assertThat(original.getStatus()).isEqualTo("FAILED");
        assertThat(original.getFailureReason()).isEqualTo("word failed");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(2);
    }

    @Test
    void rejectsRetryWhenFinalImageVersionContainsFailedAsset() {
        ListingTask task = saveCompletedTask("task_retry_failed_asset");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        saveAsset("asset_failed", task.getSelectedImageVersionId(), ImageAssetType.MAIN_IMAGE, "FAIL", 1);
        ExportPackage failedPackage = saveExportPackage(
                "export_retry_failed_asset",
                task.getTaskId(),
                ExportFormat.ZIP,
                "FAILED");
        failedPackage.setFailureReason("zip failed");
        exportPackageRepository.save(failedPackage);

        assertThatThrownBy(() -> service.retryExportPackage("export_retry_failed_asset"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);

        ExportPackage original = exportPackageRepository.findById("export_retry_failed_asset").orElseThrow();
        assertThat(original.getStatus()).isEqualTo("FAILED");
        assertThat(exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(task.getTaskId()))
                .hasSize(1);
    }

    @Test
    void rejectsExportWhenFailedAssetWaiverIsIncomplete() {
        ListingTask task = saveCompletedTask("task_incomplete_waiver");
        saveTextVersion(task.getSelectedTextVersionId(), task.getTaskId());
        saveImageVersion(task.getSelectedImageVersionId(), task.getTaskId());
        ImageAsset asset = saveAsset(
                "asset_incomplete_waiver",
                task.getSelectedImageVersionId(),
                ImageAssetType.MAIN_IMAGE,
                "FAIL",
                1);
        asset.setComplianceReviewedBy("admin@example.com");
        imageAssetRepository.save(asset);

        assertThatThrownBy(() -> service.exportDefaultZip(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsRetryingSucceededExportPackage() {
        ExportPackage exportPackage = saveExportPackage(
                "export_succeeded",
                "task_retry_invalid",
                ExportFormat.ZIP,
                "SUCCEEDED");

        assertThatThrownBy(() -> service.retryExportPackage(exportPackage.getExportPackageId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsRetryingExcelExportPackageWhenTaskIsMissing() {
        ExportPackage exportPackage = saveExportPackage(
                "export_excel_failed",
                "task_retry_invalid",
                ExportFormat.EXCEL,
                "FAILED");

        assertThatThrownBy(() -> service.retryExportPackage(exportPackage.getExportPackageId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsRetryingWordExportPackageWhenTaskIsMissing() {
        ExportPackage exportPackage = saveExportPackage(
                "export_word_failed",
                "task_retry_invalid",
                ExportFormat.WORD,
                "FAILED");

        assertThatThrownBy(() -> service.retryExportPackage(exportPackage.getExportPackageId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsRetryingMissingExportPackage() {
        assertThatThrownBy(() -> service.retryExportPackage("export_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsGettingMissingExportPackage() {
        assertThatThrownBy(() -> service.getExportPackage("export_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private ListingTask saveCompletedTask(String taskId) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(ListingTaskStatus.COMPLETED);
        task.setTextStatus(GenerationStatus.SUCCEEDED);
        task.setImageStatus(GenerationStatus.SUCCEEDED);
        task.setBriefStatus(BriefStatus.APPROVED);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[\"uploads/product-images/product.png\"]");
        task.setCompetitorAsinsJson("[\"B000TEST\"]");
        task.setSelectedTextVersionId("text_001");
        task.setSelectedImageVersionId("image_001");
        return listingTaskRepository.save(task);
    }

    private TextVersion saveTextVersion(String versionId, String taskId) {
        TextVersion version = new TextVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_001");
        version.setTitle("Wireless CarPlay Stereo for Amazon US");
        version.setBulletPointsJson("[\"Wireless CarPlay\",\"Android Auto\"]");
        version.setDescription("A review-ready car stereo listing.");
        version.setBackendSearchTerms("wireless carplay stereo");
        version.setTargetKeywordsJson("[\"car stereo\"]");
        version.setComplianceWarningsJson("[]");
        version.setQualityScore(88);
        version.setSelected(true);
        return textVersionRepository.save(version);
    }

    private ImageVersion saveImageVersion(String versionId, String taskId) {
        ImageVersion version = new ImageVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_001");
        version.setInputProductUrlsJson("[\"uploads/product-images/product.png\"]");
        version.setImageProvider("PLACEHOLDER");
        version.setImageModel("placeholder-image-model");
        version.setGenerationParamsJson("{}");
        version.setStatus(GenerationStatus.SUCCEEDED);
        version.setQualityScore(80);
        version.setSelected(true);
        return imageVersionRepository.save(version);
    }

    private ImageAsset saveAsset(
            String assetId,
            String imageVersionId,
            ImageAssetType type,
            String complianceStatus,
            int sortOrder) {
        ImageAsset asset = new ImageAsset();
        asset.setAssetId(assetId);
        asset.setImageVersionId(imageVersionId);
        asset.setType(type);
        asset.setPrompt("Generate " + type.name() + " image");
        asset.setRewrittenPrompt("Placeholder rewritten prompt for " + type.name());
        asset.setGeneratedImageUrl("generated-images/" + imageVersionId + "/" + type.name() + ".png");
        asset.setSizeProfile(type.name());
        asset.setTargetWidth(2000);
        asset.setTargetHeight(2000);
        asset.setComplianceStatus(complianceStatus);
        asset.setComplianceMethodsJson("[\"PLACEHOLDER_RULE_CHECK\"]");
        asset.setComplianceIssuesJson("[]");
        asset.setSortOrder(sortOrder);
        return imageAssetRepository.save(asset);
    }

    private ExportPackage saveExportPackage(
            String exportPackageId,
            String taskId,
            ExportFormat format,
            String status) {
        ExportPackage exportPackage = new ExportPackage();
        exportPackage.setExportPackageId(exportPackageId);
        exportPackage.setTaskId(taskId);
        exportPackage.setSelectedTextVersionId("text_001");
        exportPackage.setSelectedImageVersionId("image_001");
        exportPackage.setFormat(format);
        exportPackage.setStatus(status);
        exportPackage.setIncludedAssetIdsJson("[]");
        return exportPackageRepository.save(exportPackage);
    }

    private List<String> zipEntries(String fileKey) throws Exception {
        try (InputStream inputStream = fileStorage.read(fileKey);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            List<String> entries = new java.util.ArrayList<>();
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
            return entries;
        }
    }

    private String readStoredFile(String fileKey) throws Exception {
        try (InputStream inputStream = fileStorage.read(fileKey)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readZipEntry(String fileKey, String entryName) throws Exception {
        try (InputStream inputStream = fileStorage.read(fileKey);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            throw new AssertionError("ZIP entry not found: " + entryName);
        }
    }
}
