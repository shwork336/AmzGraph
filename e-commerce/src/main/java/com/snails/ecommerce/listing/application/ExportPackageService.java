package com.snails.ecommerce.listing.application;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.common.api.PagedResponse;
import com.snails.ecommerce.common.storage.FileStoragePort;
import com.snails.ecommerce.common.storage.StoredFile;
import com.snails.ecommerce.listing.api.CancelExportPackageRequest;
import com.snails.ecommerce.listing.api.ExportPackageResponse;
import com.snails.ecommerce.listing.domain.ExportFormat;
import com.snails.ecommerce.listing.domain.ExportPackage;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.listing.infrastructure.ExportPackageRepository;
import com.snails.ecommerce.listing.infrastructure.ImageAssetRepository;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.TextVersionRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 导出交付包应用服务。
 *
 * <p>负责基于已完成任务的最终文案和图片版本生成交付文件，并记录导出审计信息。
 * 当前阶段同步生成 ZIP 默认交付包、Markdown、Excel 和 Word 可选导出，不处理异步队列。</p>
 */
@Service
@RequiredArgsConstructor
public class ExportPackageService {

    /** Listing 任务仓储。 */
    private final ListingTaskRepository listingTaskRepository;

    /** 文案版本仓储。 */
    private final TextVersionRepository textVersionRepository;

    /** 图片版本仓储。 */
    private final ImageVersionRepository imageVersionRepository;

    /** 图片资产仓储。 */
    private final ImageAssetRepository imageAssetRepository;

    /** 导出包仓储。 */
    private final ExportPackageRepository exportPackageRepository;

    /** 文件存储端口。 */
    private final FileStoragePort fileStorage;

    /** 业务 ID 生成器。 */
    private final IdGenerator idGenerator;

    /** JSON 字段映射器。 */
    private final ObjectMapper objectMapper;

    /** 操作审计日志服务。 */
    private final OperationAuditLogService operationAuditLogService;

    /**
     * 为已完成任务生成 ZIP 默认交付包。
     *
     * @param taskId Listing 任务 ID
     * @return 导出包响应
     */
    @Transactional
    public ExportPackageResponse exportDefaultZip(String taskId) {
        return exportNow(taskId, ExportFormat.ZIP, "Failed to export ZIP package");
    }

    /**
     * 为已完成任务生成 Markdown 可选导出文件。
     *
     * <p>Markdown 文件面向知识库归档和轻量审阅，文件内直接包含最终图文版本、Listing 文案、
     * 图片资产和合规人工确认信息，因此本阶段不额外生成 manifest。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 导出包响应
     */
    @Transactional
    public ExportPackageResponse exportMarkdown(String taskId) {
        return exportNow(taskId, ExportFormat.MARKDOWN, "Failed to export Markdown package");
    }

    /**
     * 为已完成任务生成 Excel 可选导出文件。
     *
     * <p>Excel 文件面向运营复制和表格归档，包含任务信息、Listing 文案和图片合规清单。
     * 当前阶段使用标准库生成最小 Office Open XML 工作簿，不引入额外 Excel 依赖。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 导出包响应
     */
    @Transactional
    public ExportPackageResponse exportExcel(String taskId) {
        return exportNow(taskId, ExportFormat.EXCEL, "Failed to export Excel package");
    }

    /**
     * 为已完成任务生成 Word 可选导出文件。
     *
     * <p>Word 文件面向对外交付、线下审阅和管理层确认，包含任务信息、Listing 文案和图片合规清单。
     * 当前阶段使用标准库生成最小 Office Open XML 文档，不引入额外 Word 依赖。</p>
     *
     * @param taskId Listing 任务 ID
     * @return 导出包响应
     */
    @Transactional
    public ExportPackageResponse exportWord(String taskId) {
        return exportNow(taskId, ExportFormat.WORD, "Failed to export Word package");
    }

    /**
     * 创建待执行的导出记录。
     *
     * <p>该方法只冻结当前最终图文选择和待导出资产，不生成实际文件。后续可由队列、定时任务或人工触发执行该记录。</p>
     *
     * @param taskId Listing 任务 ID
     * @param format 导出格式，空白时默认 ZIP
     * @return 状态为 PENDING 的导出记录
     */
    @Transactional
    public ExportPackageResponse createPendingExportPackage(String taskId, String format) {
        ExportFormat exportFormat = parseExportFormatOrDefault(format);
        ExportSource source = loadExportSource(taskId);
        String exportPackageId = idGenerator.generate("export");
        List<String> assetIds = source.assets().stream().map(ImageAsset::getAssetId).toList();
        ExportPackage exportPackage = exportPackageRepository.save(
                createPackage(source.task(), exportPackageId, exportFormat, assetIds, "PENDING"));
        return toResponse(exportPackage);
    }

    /**
     * 查询任务下的全部导出记录。
     *
     * <p>该查询只读取导出历史，不触发导出、重试或任务状态变更。</p>
     */
    @Transactional(readOnly = true)
    public List<ExportPackageResponse> listExportPackages(String taskId) {
        return listExportPackages(taskId, null, null);
    }

    /**
     * 按可选格式和状态查询任务下的导出记录。
     *
     * <p>空白筛选参数按未传处理。格式非法时返回稳定业务错误，避免前端展示不可识别的格式。</p>
     */
    @Transactional(readOnly = true)
    public List<ExportPackageResponse> listExportPackages(String taskId, String format, String status) {
        return findFilteredExportPackages(taskId, format, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 分页查询任务下的导出记录。
     *
     * <p>分页基于已筛选后的结果计算，页码从 0 开始。</p>
     */
    @Transactional(readOnly = true)
    public PagedResponse<ExportPackageResponse> listExportPackagesPage(
            String taskId,
            String format,
            String status,
            int page,
            int size) {
        requireValidPageRequest(page, size);
        List<ExportPackage> filteredPackages = findFilteredExportPackages(taskId, format, status);
        int totalItems = filteredPackages.size();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        int fromIndex = Math.min(page * size, totalItems);
        int toIndex = Math.min(fromIndex + size, totalItems);
        List<ExportPackageResponse> items = filteredPackages.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
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
     * 查询导出包状态。
     *
     * <p>该查询不触发导出、重试或任务状态变更。</p>
     */
    @Transactional(readOnly = true)
    public ExportPackageResponse getExportPackage(String exportPackageId) {
        ExportPackage exportPackage = exportPackageRepository.findById(exportPackageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export package not found: " + exportPackageId));
        return toResponse(exportPackage);
    }

    /**
     * 执行待处理的导出记录。
     *
     * <p>当前阶段仍在请求线程内完成文件生成，但状态边界已拆分为 PENDING -> RUNNING -> SUCCEEDED/FAILED，
     * 后续接入真正异步执行器时可复用该方法作为 worker 入口。</p>
     */
    @Transactional
    public ExportPackageResponse runPendingExportPackage(String exportPackageId) {
        ExportPackage exportPackage = requireExportPackage(exportPackageId);
        ExportPackageResponse idempotentResponse = returnOrRepairCompletedExport(exportPackage);
        if (idempotentResponse != null) {
            return idempotentResponse;
        }
        if (!"PENDING".equals(exportPackage.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Only pending export package can be run: " + exportPackageId);
        }
        if (!claimPendingExportPackage(exportPackageId)) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Only pending export package can be run: " + exportPackageId);
        }
        return runClaimedExportPackage(exportPackageId);
    }

    /**
     * worker 领取并执行待处理导出记录。
     *
     * <p>返回 {@code null} 表示记录已被其他 worker 领取或状态已变化，当前 worker 应跳过该记录。</p>
     */
    @Transactional
    public ExportPackageResponse claimAndRunPendingExportPackage(String exportPackageId) {
        if (!claimPendingExportPackage(exportPackageId)) {
            return null;
        }
        return runClaimedExportPackage(exportPackageId);
    }

    /**
     * 执行已经领取为 RUNNING 的导出记录。
     */
    private ExportPackageResponse runClaimedExportPackage(String exportPackageId) {
        ExportPackage exportPackage = exportPackageRepository.findById(exportPackageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export package not found: " + exportPackageId));
        if (!"RUNNING".equals(exportPackage.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Export package is not claimed for running: " + exportPackageId);
        }
        try {
            ExportSource source = loadExportSource(exportPackage.getTaskId());
            requirePendingSelectionStillCurrent(exportPackage, source);
            return executeExportPackage(exportPackage, source, "Failed to run export package");
        } catch (RuntimeException exception) {
            exportPackage.setStatus("FAILED");
            exportPackage.setFailureReason(exception.getMessage());
            touch(exportPackage);
            exportPackageRepository.save(exportPackage);
            throw exception;
        }
    }

    /**
     * 原子领取 PENDING 导出记录。
     */
    private boolean claimPendingExportPackage(String exportPackageId) {
        LocalDateTime now = LocalDateTime.now();
        return exportPackageRepository.updateStatusWhenCurrentStatus(
                exportPackageId,
                "PENDING",
                "RUNNING",
                now,
                now) == 1;
    }

    /**
     * 查询导出记录，不存在时返回稳定业务错误。
     */
    private ExportPackage requireExportPackage(String exportPackageId) {
        return exportPackageRepository.findById(exportPackageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export package not found: " + exportPackageId));
    }

    /**
     * 重试失败的导出包。
     *
     * <p>当前支持 ZIP、Markdown、Excel 和 Word。重试会创建新的导出记录，不覆盖原失败记录，
     * 便于保留失败审计信息。</p>
     */
    /**
     * 取消尚未执行的导出记录。
     *
     * <p>取消只允许发生在 {@code PENDING} 状态，取消后保留导出审计记录，不生成文件，也不删除历史记录。</p>
     */
    @Transactional
    public ExportPackageResponse cancelPendingExportPackage(
            String exportPackageId,
            CancelExportPackageRequest request) {
        requireValidCancelRequest(request);
        ExportPackage exportPackage = exportPackageRepository.findById(exportPackageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export package not found: " + exportPackageId));
        if (!"PENDING".equals(exportPackage.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Only pending export package can be canceled: " + exportPackageId);
        }

        exportPackage.setStatus("CANCELED");
        exportPackage.setFailureReason(null);
        exportPackage.setCanceledBy(request.canceledBy());
        exportPackage.setCancelReason(request.cancelReason());
        exportPackage.setCanceledAt(LocalDateTime.now());
        touch(exportPackage);
        ExportPackage saved = exportPackageRepository.save(exportPackage);
        operationAuditLogService.record(
                "EXPORT_PACKAGE_CANCELED",
                request.canceledBy(),
                "EXPORT_PACKAGE",
                saved.getExportPackageId(),
                saved.getTaskId(),
                request.cancelReason(),
                Map.of(
                        "format", saved.getFormat().name(),
                        "status", saved.getStatus(),
                        "selectedTextVersionId", nullToBlank(saved.getSelectedTextVersionId()),
                        "selectedImageVersionId", nullToBlank(saved.getSelectedImageVersionId())));
        return toResponse(saved);
    }

    /**
     * 校验取消导出请求，服务层保留防御性校验，避免非 HTTP 调用绕过 Bean Validation。
     */
    private void requireValidCancelRequest(CancelExportPackageRequest request) {
        if (request == null || !StringUtils.hasText(request.canceledBy())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Canceled by must not be blank");
        }
        if (!StringUtils.hasText(request.cancelReason())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Cancel reason must not be blank");
        }
    }

    @Transactional
    public ExportPackageResponse retryExportPackage(String exportPackageId) {
        ExportPackage failedPackage = exportPackageRepository.findById(exportPackageId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export package not found: " + exportPackageId));
        if (!"FAILED".equals(failedPackage.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Only failed export package can be retried: " + exportPackageId);
        }
        if (failedPackage.getFormat() == ExportFormat.ZIP) {
            return exportDefaultZip(failedPackage.getTaskId());
        }
        if (failedPackage.getFormat() == ExportFormat.MARKDOWN) {
            return exportMarkdown(failedPackage.getTaskId());
        }
        if (failedPackage.getFormat() == ExportFormat.EXCEL) {
            return exportExcel(failedPackage.getTaskId());
        }
        if (failedPackage.getFormat() == ExportFormat.WORD) {
            return exportWord(failedPackage.getTaskId());
        }
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Export package format cannot be retried yet: " + failedPackage.getFormat());
    }

    /**
     * 查询任务，不存在时返回稳定业务错误。
     */
    private ListingTask requireTask(String taskId) {
        return listingTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TASK_NOT_FOUND,
                        "Listing task not found: " + taskId));
    }

    /**
     * 查询并筛选任务导出记录。
     */
    private List<ExportPackage> findFilteredExportPackages(String taskId, String format, String status) {
        requireTask(taskId);
        ExportFormat exportFormat = parseExportFormat(format);
        return exportPackageRepository.findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(taskId)
                .stream()
                .filter(exportPackage -> exportFormat == null || exportPackage.getFormat() == exportFormat)
                .filter(exportPackage -> !StringUtils.hasText(status) || status.equals(exportPackage.getStatus()))
                .toList();
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
     * 解析导出格式筛选条件。
     */
    private ExportFormat parseExportFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return null;
        }
        try {
            return ExportFormat.valueOf(format);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Export format is invalid: " + format);
        }
    }

    /**
     * 解析导出格式，空白时默认创建 ZIP 导出。
     */
    private ExportFormat parseExportFormatOrDefault(String format) {
        ExportFormat exportFormat = parseExportFormat(format);
        return exportFormat == null ? ExportFormat.ZIP : exportFormat;
    }

    /**
     * 兼容当前同步接口的立即导出入口。
     */
    private ExportPackageResponse exportNow(String taskId, ExportFormat format, String failureMessage) {
        ExportSource source = loadExportSource(taskId);
        String exportPackageId = idGenerator.generate("export");
        List<String> assetIds = source.assets().stream().map(ImageAsset::getAssetId).toList();
        ExportPackage exportPackage = exportPackageRepository.save(
                createPackage(source.task(), exportPackageId, format, assetIds, "RUNNING"));
        return executeExportPackage(exportPackage, source, failureMessage);
    }

    /**
     * 执行导出记录并写回最终状态。
     */
    private ExportPackageResponse executeExportPackage(
            ExportPackage exportPackage,
            ExportSource source,
            String failureMessage) {
        ExportPackageResponse idempotentResponse = returnOrRepairCompletedExport(exportPackage);
        if (idempotentResponse != null) {
            return idempotentResponse;
        }
        if (!"RUNNING".equals(exportPackage.getStatus())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Export package is not running: " + exportPackage.getExportPackageId());
        }
        markRunning(exportPackage);
        exportPackage.setFailureReason(null);
        exportPackageRepository.save(exportPackage);
        try {
            ExportedFiles exportedFiles = saveExportFiles(exportPackage, source);
            exportPackage.setManifestUrl(exportedFiles.manifestUrl());
            exportPackage.setFileUrl(exportedFiles.fileUrl());
            exportPackage.setStatus("SUCCEEDED");
            exportPackage.setFailureReason(null);
            touch(exportPackage);
            ExportPackage saved = exportPackageRepository.save(exportPackage);
            return toResponse(saved);
        } catch (RuntimeException exception) {
            exportPackage.setStatus("FAILED");
            exportPackage.setFailureReason(exception.getMessage());
            touch(exportPackage);
            exportPackageRepository.save(exportPackage);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, failureMessage);
        }
    }

    /**
     * 对已经产生完整输出的导出记录做幂等返回，避免重复进入文件写入。
     */
    private ExportPackageResponse returnOrRepairCompletedExport(ExportPackage exportPackage) {
        if (!hasCompleteExportOutput(exportPackage)) {
            return null;
        }
        if ("SUCCEEDED".equals(exportPackage.getStatus())) {
            return toResponse(exportPackage);
        }
        if ("RUNNING".equals(exportPackage.getStatus())) {
            exportPackage.setStatus("SUCCEEDED");
            exportPackage.setFailureReason(null);
            touch(exportPackage);
            return toResponse(exportPackageRepository.save(exportPackage));
        }
        return null;
    }

    /**
     * 判断导出记录是否已经具备当前格式所需的完整输出路径。
     */
    private boolean hasCompleteExportOutput(ExportPackage exportPackage) {
        if (!StringUtils.hasText(exportPackage.getFileUrl())) {
            return false;
        }
        if (exportPackage.getFormat() == ExportFormat.ZIP) {
            return StringUtils.hasText(exportPackage.getManifestUrl());
        }
        return exportPackage.getFormat() == ExportFormat.MARKDOWN
                || exportPackage.getFormat() == ExportFormat.EXCEL
                || exportPackage.getFormat() == ExportFormat.WORD;
    }

    /**
     * 按导出格式生成实际文件。
     */
    private ExportedFiles saveExportFiles(ExportPackage exportPackage, ExportSource source) {
        if (exportPackage.getFormat() == ExportFormat.ZIP) {
            String manifest = buildManifest(
                    source.task(),
                    source.textVersion(),
                    source.imageVersion(),
                    source.assets());
            StoredFile manifestFile = saveTextFile(
                    "exports/" + source.task().getTaskId() + "/manifest",
                    exportPackage.getExportPackageId() + "-manifest.json",
                    "application/json",
                    manifest);
            StoredFile zipFile = saveZipPackage(
                    source.task(),
                    exportPackage.getExportPackageId(),
                    buildListingMarkdown(source.textVersion()),
                    manifest,
                    buildComplianceReport(source.assets()),
                    source.assets());
            return new ExportedFiles(zipFile.fileKey(), manifestFile.fileKey());
        }
        if (exportPackage.getFormat() == ExportFormat.MARKDOWN) {
            String markdown = buildExportMarkdown(
                    source.task(),
                    source.textVersion(),
                    source.imageVersion(),
                    source.assets());
            StoredFile markdownFile = saveTextFile(
                    "exports/" + source.task().getTaskId() + "/markdown",
                    exportPackage.getExportPackageId() + ".md",
                    "text/markdown",
                    markdown);
            return new ExportedFiles(markdownFile.fileKey(), null);
        }
        if (exportPackage.getFormat() == ExportFormat.EXCEL) {
            StoredFile excelFile = saveExcelPackage(
                    source.task(),
                    exportPackage.getExportPackageId(),
                    source.textVersion(),
                    source.imageVersion(),
                    source.assets());
            return new ExportedFiles(excelFile.fileKey(), null);
        }
        if (exportPackage.getFormat() == ExportFormat.WORD) {
            StoredFile wordFile = saveWordPackage(
                    source.task(),
                    exportPackage.getExportPackageId(),
                    source.textVersion(),
                    source.imageVersion(),
                    source.assets());
            return new ExportedFiles(wordFile.fileKey(), null);
        }
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Export package format cannot be generated: " + exportPackage.getFormat());
    }

    /**
     * 加载可导出的最终图文上下文。
     *
     * <p>所有导出格式都必须复用该边界，避免不同格式绕过终审选择或合规拦截。</p>
     */
    private ExportSource loadExportSource(String taskId) {
        ListingTask task = requireTask(taskId);
        requireCompletedTask(task);
        requireFinalSelection(task);
        TextVersion textVersion = requireSelectedTextVersion(task);
        ImageVersion imageVersion = requireSelectedImageVersion(task);
        List<ImageAsset> assets = imageAssetRepository
                .findByImageVersionIdOrderBySortOrderAscAssetIdAsc(imageVersion.getVersionId());
        requireExportableAssets(assets);
        return new ExportSource(task, textVersion, imageVersion, assets);
    }

    /**
     * 校验任务已完成终审。
     */
    private void requireCompletedTask(ListingTask task) {
        if (task.getStatus() != ListingTaskStatus.COMPLETED) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Listing task is not completed: " + task.getTaskId());
        }
    }

    /**
     * 校验任务已有最终图文选择。
     */
    private void requireFinalSelection(ListingTask task) {
        if (!StringUtils.hasText(task.getSelectedTextVersionId())
                || !StringUtils.hasText(task.getSelectedImageVersionId())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Listing task has no final selection: " + task.getTaskId());
        }
    }

    /**
     * 查询最终文案版本，并校验归属。
     */
    private TextVersion requireSelectedTextVersion(ListingTask task) {
        TextVersion version = textVersionRepository.findById(task.getSelectedTextVersionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Selected text version not found: " + task.getSelectedTextVersionId()));
        if (!task.getTaskId().equals(version.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Selected text version does not belong to task: " + version.getVersionId());
        }
        return version;
    }

    /**
     * 查询最终图片版本，并校验归属。
     */
    private ImageVersion requireSelectedImageVersion(ListingTask task) {
        ImageVersion version = imageVersionRepository.findById(task.getSelectedImageVersionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Selected image version not found: " + task.getSelectedImageVersionId()));
        if (!task.getTaskId().equals(version.getTaskId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Selected image version does not belong to task: " + version.getVersionId());
        }
        return version;
    }

    /**
     * 校验图片资产可导出。
     *
     * <p>FAIL 资产只有存在完整管理员豁免信息时才允许 ZIP 导出。</p>
     */
    private void requireExportableAssets(List<ImageAsset> assets) {
        List<String> failedAssetIds = assets.stream()
                .filter(asset -> "FAIL".equals(asset.getComplianceStatus()))
                .filter(asset -> !hasCompleteComplianceWaiver(asset))
                .map(ImageAsset::getAssetId)
                .toList();
        if (!failedAssetIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Image assets failed compliance check and cannot be exported: "
                            + String.join(",", failedAssetIds));
        }
    }

    /**
     * 判断 FAIL 资产是否已有完整管理员豁免信息。
     */
    private boolean hasCompleteComplianceWaiver(ImageAsset asset) {
        return StringUtils.hasText(asset.getComplianceReviewedBy())
                && StringUtils.hasText(asset.getComplianceReviewReason())
                && asset.getComplianceReviewedAt() != null;
    }

    /**
     * 校验待执行导出记录仍然指向任务当前最终选择。
     */
    private void requirePendingSelectionStillCurrent(ExportPackage exportPackage, ExportSource source) {
        if (!source.task().getSelectedTextVersionId().equals(exportPackage.getSelectedTextVersionId())
                || !source.task().getSelectedImageVersionId().equals(exportPackage.getSelectedImageVersionId())) {
            throw new BusinessException(
                    ErrorCode.TASK_STATUS_INVALID,
                    "Export package final selection is no longer current: "
                            + exportPackage.getExportPackageId());
        }
    }

    /**
     * 创建运行中的导出记录。
     */
    private ExportPackage createPackage(
            ListingTask task,
            String exportPackageId,
            ExportFormat format,
            List<String> assetIds,
            String status) {
        ExportPackage exportPackage = new ExportPackage();
        exportPackage.setExportPackageId(exportPackageId);
        exportPackage.setTaskId(task.getTaskId());
        exportPackage.setSelectedTextVersionId(task.getSelectedTextVersionId());
        exportPackage.setSelectedImageVersionId(task.getSelectedImageVersionId());
        exportPackage.setFormat(format);
        exportPackage.setStatus(status);
        if ("RUNNING".equals(status)) {
            markRunning(exportPackage);
        } else {
            touch(exportPackage);
        }
        exportPackage.setIncludedAssetIdsJson(writeStringList(assetIds));
        return exportPackage;
    }

    /**
     * 标记导出记录进入执行中。
     */
    private void markRunning(ExportPackage exportPackage) {
        LocalDateTime now = LocalDateTime.now();
        exportPackage.setStatus("RUNNING");
        if (exportPackage.getStartedAt() == null) {
            exportPackage.setStartedAt(now);
        }
        exportPackage.setUpdatedAt(now);
    }

    /**
     * 更新导出记录最后修改时间。
     */
    private void touch(ExportPackage exportPackage) {
        exportPackage.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * 保存文本文件。
     */
    private StoredFile saveTextFile(String namespace, String filename, String contentType, String content) {
        return fileStorage.save(
                namespace,
                filename,
                contentType,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 生成并保存 ZIP 文件。
     */
    private StoredFile saveZipPackage(
            ListingTask task,
            String exportPackageId,
            String listing,
            String manifest,
            String complianceReport,
            List<ImageAsset> assets) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                addTextEntry(zipOutputStream, "listing.md", listing);
                addTextEntry(zipOutputStream, "manifest.json", manifest);
                addTextEntry(zipOutputStream, "compliance_report.md", complianceReport);
                for (int index = 0; index < assets.size(); index++) {
                    ImageAsset asset = assets.get(index);
                    String entryName = "images/%03d-%s-%s.txt".formatted(
                            index + 1,
                            asset.getType() == null ? "IMAGE" : asset.getType().name(),
                            asset.getAssetId());
                    addTextEntry(zipOutputStream, entryName, buildImagePlaceholder(asset));
                }
            }
            return fileStorage.save(
                    "exports/" + task.getTaskId() + "/zip",
                    exportPackageId + ".zip",
                    "application/zip",
                    new ByteArrayInputStream(outputStream.toByteArray()));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate ZIP bytes", exception);
        }
    }

    /**
     * 生成并保存 Excel 文件。
     */
    private StoredFile saveExcelPackage(
            ListingTask task,
            String exportPackageId,
            TextVersion textVersion,
            ImageVersion imageVersion,
            List<ImageAsset> assets) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                addTextEntry(zipOutputStream, "[Content_Types].xml", buildExcelContentTypes());
                addTextEntry(zipOutputStream, "_rels/.rels", buildExcelRootRelationships());
                addTextEntry(zipOutputStream, "xl/workbook.xml", buildExcelWorkbook());
                addTextEntry(zipOutputStream, "xl/_rels/workbook.xml.rels", buildExcelWorkbookRelationships());
                addTextEntry(zipOutputStream, "xl/worksheets/sheet1.xml", buildSheetXml(buildTaskRows(task, textVersion, imageVersion)));
                addTextEntry(zipOutputStream, "xl/worksheets/sheet2.xml", buildSheetXml(buildListingRows(textVersion)));
                addTextEntry(zipOutputStream, "xl/worksheets/sheet3.xml", buildSheetXml(buildImageRows(assets)));
            }
            return fileStorage.save(
                    "exports/" + task.getTaskId() + "/excel",
                    exportPackageId + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new ByteArrayInputStream(outputStream.toByteArray()));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate Excel bytes", exception);
        }
    }

    /**
     * 生成并保存 Word 文件。
     */
    private StoredFile saveWordPackage(
            ListingTask task,
            String exportPackageId,
            TextVersion textVersion,
            ImageVersion imageVersion,
            List<ImageAsset> assets) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                addTextEntry(zipOutputStream, "[Content_Types].xml", buildWordContentTypes());
                addTextEntry(zipOutputStream, "_rels/.rels", buildWordRootRelationships());
                addTextEntry(
                        zipOutputStream,
                        "word/document.xml",
                        buildWordDocument(task, textVersion, imageVersion, assets));
            }
            return fileStorage.save(
                    "exports/" + task.getTaskId() + "/word",
                    exportPackageId + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    new ByteArrayInputStream(outputStream.toByteArray()));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate Word bytes", exception);
        }
    }

    /**
     * 向 ZIP 写入文本条目。
     */
    private void addTextEntry(ZipOutputStream zipOutputStream, String name, String content) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    /**
     * 生成 listing Markdown。
     */
    private String buildListingMarkdown(TextVersion textVersion) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(nullToBlank(textVersion.getTitle())).append("\n\n");
        builder.append("## Bullet Points\n\n");
        for (String bulletPoint : readStringList(textVersion.getBulletPointsJson())) {
            builder.append("- ").append(bulletPoint).append("\n");
        }
        builder.append("\n## Description\n\n");
        builder.append(nullToBlank(textVersion.getDescription())).append("\n\n");
        builder.append("## Backend Search Terms\n\n");
        builder.append(nullToBlank(textVersion.getBackendSearchTerms())).append("\n");
        return builder.toString();
    }

    /**
     * 生成 Markdown 可选导出文件。
     */
    private String buildExportMarkdown(
            ListingTask task,
            TextVersion textVersion,
            ImageVersion imageVersion,
            List<ImageAsset> assets) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Listing Export - ").append(task.getTaskId()).append("\n\n");
        builder.append("## Task\n\n");
        builder.append("- Task ID: ").append(task.getTaskId()).append("\n");
        builder.append("- Category: ").append(nullToBlank(task.getCategoryCode())).append("\n");
        builder.append("- Category Template ID: ").append(nullToBlank(task.getCategoryTemplateId())).append("\n");
        builder.append("- Marketplace: ").append(nullToBlank(task.getMarketplace())).append("\n");
        builder.append("- Language: ").append(nullToBlank(task.getLanguage())).append("\n");
        builder.append("- Selected Text Version ID: ").append(textVersion.getVersionId()).append("\n");
        builder.append("- Selected Image Version ID: ").append(imageVersion.getVersionId()).append("\n\n");
        builder.append(buildListingMarkdown(textVersion)).append("\n");
        builder.append("## Image Assets\n\n");
        for (ImageAsset asset : assets) {
            builder.append("### ").append(asset.getAssetId()).append("\n\n");
            builder.append("- Type: ").append(asset.getType() == null ? "" : asset.getType().name()).append("\n");
            builder.append("- Generated Image URL: ").append(nullToBlank(asset.getGeneratedImageUrl())).append("\n");
            builder.append("- Source Editable File URL: ").append(nullToBlank(asset.getSourceEditableFileUrl())).append("\n");
            builder.append("- Size Profile: ").append(nullToBlank(asset.getSizeProfile())).append("\n");
            builder.append("- Target Size: ")
                    .append(asset.getTargetWidth() == null ? "" : asset.getTargetWidth())
                    .append(" x ")
                    .append(asset.getTargetHeight() == null ? "" : asset.getTargetHeight())
                    .append("\n");
            builder.append("- Compliance Status: ").append(nullToBlank(asset.getComplianceStatus())).append("\n");
            builder.append("- Compliance Methods: ").append(readStringList(asset.getComplianceMethodsJson())).append("\n");
            builder.append("- Compliance Issues: ").append(readStringList(asset.getComplianceIssuesJson())).append("\n");
            if (hasCompleteComplianceWaiver(asset)) {
                builder.append("- Reviewed By: ").append(asset.getComplianceReviewedBy()).append("\n");
                builder.append("- Review Reason: ").append(asset.getComplianceReviewReason()).append("\n");
                builder.append("- Reviewed At: ").append(asset.getComplianceReviewedAt()).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    /**
     * 生成 Excel 任务工作表行。
     */
    private List<List<String>> buildTaskRows(
            ListingTask task,
            TextVersion textVersion,
            ImageVersion imageVersion) {
        return List.of(
                List.of("Field", "Value"),
                List.of("Task ID", task.getTaskId()),
                List.of("Category", nullToBlank(task.getCategoryCode())),
                List.of("Category Template ID", nullToBlank(task.getCategoryTemplateId())),
                List.of("Marketplace", nullToBlank(task.getMarketplace())),
                List.of("Language", nullToBlank(task.getLanguage())),
                List.of("Selected Text Version ID", textVersion.getVersionId()),
                List.of("Selected Image Version ID", imageVersion.getVersionId()),
                List.of("Image Provider", nullToBlank(imageVersion.getImageProvider())),
                List.of("Image Model", nullToBlank(imageVersion.getImageModel()))
        );
    }

    /**
     * 生成 Excel Listing 工作表行。
     */
    private List<List<String>> buildListingRows(TextVersion textVersion) {
        return List.of(
                List.of("Field", "Value"),
                List.of("Title", nullToBlank(textVersion.getTitle())),
                List.of("Bullet Points", String.join("\n", readStringList(textVersion.getBulletPointsJson()))),
                List.of("Description", nullToBlank(textVersion.getDescription())),
                List.of("Backend Search Terms", nullToBlank(textVersion.getBackendSearchTerms())),
                List.of("Target Keywords", String.join(", ", readStringList(textVersion.getTargetKeywordsJson()))),
                List.of("Compliance Warnings", String.join("\n", readStringList(textVersion.getComplianceWarningsJson()))),
                List.of("Quality Score", textVersion.getQualityScore() == null ? "" : textVersion.getQualityScore().toString())
        );
    }

    /**
     * 生成 Excel 图片资产工作表行。
     */
    private List<List<String>> buildImageRows(List<ImageAsset> assets) {
        List<List<String>> rows = new java.util.ArrayList<>();
        rows.add(List.of(
                "Asset ID",
                "Type",
                "Generated Image URL",
                "Source Editable File URL",
                "Size Profile",
                "Target Width",
                "Target Height",
                "Compliance Status",
                "Compliance Methods",
                "Compliance Issues",
                "Reviewed By",
                "Review Reason",
                "Reviewed At"
        ));
        for (ImageAsset asset : assets) {
            rows.add(List.of(
                    asset.getAssetId(),
                    asset.getType() == null ? "" : asset.getType().name(),
                    nullToBlank(asset.getGeneratedImageUrl()),
                    nullToBlank(asset.getSourceEditableFileUrl()),
                    nullToBlank(asset.getSizeProfile()),
                    asset.getTargetWidth() == null ? "" : asset.getTargetWidth().toString(),
                    asset.getTargetHeight() == null ? "" : asset.getTargetHeight().toString(),
                    nullToBlank(asset.getComplianceStatus()),
                    String.join(", ", readStringList(asset.getComplianceMethodsJson())),
                    String.join("\n", readStringList(asset.getComplianceIssuesJson())),
                    nullToBlank(asset.getComplianceReviewedBy()),
                    nullToBlank(asset.getComplianceReviewReason()),
                    asset.getComplianceReviewedAt() == null ? "" : asset.getComplianceReviewedAt().toString()
            ));
        }
        return rows;
    }

    /**
     * 生成最小工作表 XML。
     */
    private String buildSheetXml(List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                """.stripLeading());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            builder.append("    <row r=\"").append(rowIndex + 1).append("\">\n");
            List<String> row = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
                String cellRef = excelColumnName(columnIndex) + (rowIndex + 1);
                builder.append("      <c r=\"").append(cellRef).append("\" t=\"inlineStr\"><is><t>")
                        .append(xmlEscape(row.get(columnIndex)))
                        .append("</t></is></c>\n");
            }
            builder.append("    </row>\n");
        }
        builder.append("""
                  </sheetData>
                </worksheet>
                """);
        return builder.toString();
    }

    private String buildExcelContentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """.stripLeading();
    }

    private String buildExcelRootRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """.stripLeading();
    }

    private String buildExcelWorkbook() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Task" sheetId="1" r:id="rId1"/>
                    <sheet name="Listing" sheetId="2" r:id="rId2"/>
                    <sheet name="Images" sheetId="3" r:id="rId3"/>
                  </sheets>
                </workbook>
                """.stripLeading();
    }

    private String buildExcelWorkbookRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
                  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
                </Relationships>
                """.stripLeading();
    }

    private String buildWordContentTypes() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """.stripLeading();
    }

    private String buildWordRootRelationships() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """.stripLeading();
    }

    /**
     * 生成最小 WordprocessingML 文档正文。
     */
    private String buildWordDocument(
            ListingTask task,
            TextVersion textVersion,
            ImageVersion imageVersion,
            List<ImageAsset> assets) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                """.stripLeading());
        appendWordParagraph(builder, "Listing Export - " + task.getTaskId(), true);
        appendWordParagraph(builder, "Task", true);
        appendWordParagraph(builder, "Task ID: " + task.getTaskId(), false);
        appendWordParagraph(builder, "Category: " + nullToBlank(task.getCategoryCode()), false);
        appendWordParagraph(builder, "Category Template ID: " + nullToBlank(task.getCategoryTemplateId()), false);
        appendWordParagraph(builder, "Marketplace: " + nullToBlank(task.getMarketplace()), false);
        appendWordParagraph(builder, "Language: " + nullToBlank(task.getLanguage()), false);
        appendWordParagraph(builder, "Selected Text Version ID: " + textVersion.getVersionId(), false);
        appendWordParagraph(builder, "Selected Image Version ID: " + imageVersion.getVersionId(), false);
        appendWordParagraph(builder, "Listing", true);
        appendWordParagraph(builder, "Title: " + nullToBlank(textVersion.getTitle()), false);
        appendWordParagraph(builder, "Bullet Points:", false);
        for (String bulletPoint : readStringList(textVersion.getBulletPointsJson())) {
            appendWordParagraph(builder, "- " + bulletPoint, false);
        }
        appendWordParagraph(builder, "Description: " + nullToBlank(textVersion.getDescription()), false);
        appendWordParagraph(builder, "Backend Search Terms: " + nullToBlank(textVersion.getBackendSearchTerms()), false);
        appendWordParagraph(builder, "Target Keywords: "
                + String.join(", ", readStringList(textVersion.getTargetKeywordsJson())), false);
        appendWordParagraph(builder, "Image Assets", true);
        for (ImageAsset asset : assets) {
            appendWordParagraph(builder, asset.getAssetId(), true);
            appendWordParagraph(builder, "Type: " + (asset.getType() == null ? "" : asset.getType().name()), false);
            appendWordParagraph(builder, "Generated Image URL: " + nullToBlank(asset.getGeneratedImageUrl()), false);
            appendWordParagraph(builder, "Source Editable File URL: " + nullToBlank(asset.getSourceEditableFileUrl()), false);
            appendWordParagraph(builder, "Size Profile: " + nullToBlank(asset.getSizeProfile()), false);
            appendWordParagraph(
                    builder,
                    "Target Size: "
                            + (asset.getTargetWidth() == null ? "" : asset.getTargetWidth())
                            + " x "
                            + (asset.getTargetHeight() == null ? "" : asset.getTargetHeight()),
                    false);
            appendWordParagraph(builder, "Compliance Status: " + nullToBlank(asset.getComplianceStatus()), false);
            appendWordParagraph(
                    builder,
                    "Compliance Methods: " + String.join(", ", readStringList(asset.getComplianceMethodsJson())),
                    false);
            appendWordParagraph(
                    builder,
                    "Compliance Issues: " + String.join(", ", readStringList(asset.getComplianceIssuesJson())),
                    false);
            if (hasCompleteComplianceWaiver(asset)) {
                appendWordParagraph(builder, "Reviewed By: " + asset.getComplianceReviewedBy(), false);
                appendWordParagraph(builder, "Review Reason: " + asset.getComplianceReviewReason(), false);
                appendWordParagraph(builder, "Reviewed At: " + asset.getComplianceReviewedAt(), false);
            }
        }
        builder.append("""
                    <w:sectPr/>
                  </w:body>
                </w:document>
                """);
        return builder.toString();
    }

    /**
     * 向 Word 文档追加段落。
     */
    private void appendWordParagraph(StringBuilder builder, String text, boolean bold) {
        builder.append("    <w:p><w:r>");
        if (bold) {
            builder.append("<w:rPr><w:b/></w:rPr>");
        }
        builder.append("<w:t>")
                .append(xmlEscape(text))
                .append("</w:t></w:r></w:p>\n");
    }

    private String excelColumnName(int zeroBasedIndex) {
        StringBuilder builder = new StringBuilder();
        int value = zeroBasedIndex + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return builder.toString();
    }

    /**
     * 生成 manifest JSON。
     */
    private String buildManifest(
            ListingTask task,
            TextVersion textVersion,
            ImageVersion imageVersion,
            List<ImageAsset> assets) {
        ManifestData manifest = new ManifestData(
                task.getTaskId(),
                task.getCategoryCode(),
                task.getCategoryTemplateId(),
                task.getMarketplace(),
                task.getLanguage(),
                textVersion.getVersionId(),
                imageVersion.getVersionId(),
                imageVersion.getImageProvider(),
                imageVersion.getImageModel(),
                assets.stream().map(this::toAssetManifestData).toList(),
                LocalDateTime.now());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to build export manifest");
        }
    }

    /**
     * 生成合规报告。
     */
    private String buildComplianceReport(List<ImageAsset> assets) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Compliance Report\n\n");
        for (ImageAsset asset : assets) {
            builder.append("## ").append(asset.getAssetId()).append("\n\n");
            builder.append("- Type: ").append(asset.getType() == null ? "" : asset.getType().name()).append("\n");
            builder.append("- Status: ").append(nullToBlank(asset.getComplianceStatus())).append("\n");
            builder.append("- Methods: ").append(readStringList(asset.getComplianceMethodsJson())).append("\n");
            builder.append("- Issues: ").append(readStringList(asset.getComplianceIssuesJson())).append("\n\n");
            if (hasCompleteComplianceWaiver(asset)) {
                builder.append("- Reviewed By: ").append(asset.getComplianceReviewedBy()).append("\n");
                builder.append("- Review Reason: ").append(asset.getComplianceReviewReason()).append("\n");
                builder.append("- Reviewed At: ").append(asset.getComplianceReviewedAt()).append("\n\n");
            }
        }
        return builder.toString();
    }

    /**
     * 创建 manifest 图片资产条目。
     */
    private AssetManifestData toAssetManifestData(ImageAsset asset) {
        return new AssetManifestData(
                asset.getAssetId(),
                asset.getType() == null ? null : asset.getType().name(),
                asset.getGeneratedImageUrl(),
                asset.getSizeProfile(),
                asset.getTargetWidth(),
                asset.getTargetHeight(),
                asset.getComplianceStatus(),
                readStringList(asset.getComplianceMethodsJson()),
                readStringList(asset.getComplianceIssuesJson()),
                asset.getComplianceReviewedBy(),
                asset.getComplianceReviewReason(),
                asset.getComplianceReviewedAt());
    }

    /**
     * 图片文件占位内容。
     */
    private String buildImagePlaceholder(ImageAsset asset) {
        return """
                Placeholder image export
                assetId: %s
                generatedImageUrl: %s
                sourceEditableFileUrl: %s
                complianceStatus: %s
                """.formatted(
                asset.getAssetId(),
                nullToBlank(asset.getGeneratedImageUrl()),
                nullToBlank(asset.getSourceEditableFileUrl()),
                nullToBlank(asset.getComplianceStatus()));
    }

    /**
     * 将导出记录转换为响应。
     */
    private ExportPackageResponse toResponse(ExportPackage exportPackage) {
        return ExportPackageResponse.from(
                exportPackage,
                readStringList(exportPackage.getIncludedAssetIdsJson()));
    }

    /**
     * 读取 JSON 字符串数组，空值按空数组处理。
     */
    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(
                    json == null || json.isBlank() ? "[]" : json,
                    new TypeReference<List<String>>() {
                    });
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to process export JSON fields");
        }
    }

    /**
     * 写入 JSON 字符串数组。
     */
    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to write export JSON fields");
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String xmlEscape(String value) {
        return nullToBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * manifest 顶层数据。
     */
    private record ManifestData(
            String taskId,
            String categoryCode,
            String categoryTemplateId,
            String marketplace,
            String language,
            String selectedTextVersionId,
            String selectedImageVersionId,
            String imageProvider,
            String imageModel,
            List<AssetManifestData> assets,
            LocalDateTime exportedAt
    ) {
    }

    /**
     * manifest 图片资产数据。
     */
    private record AssetManifestData(
            String assetId,
            String type,
            String generatedImageUrl,
            String sizeProfile,
            Integer targetWidth,
            Integer targetHeight,
            String complianceStatus,
            List<String> complianceMethods,
            List<String> complianceIssues,
            String complianceReviewedBy,
            String complianceReviewReason,
            LocalDateTime complianceReviewedAt
    ) {

    }

    /**
     * 导出源数据。
     */
    /**
     * 导出生成后的文件位置。
     */
    private record ExportedFiles(
            String fileUrl,
            String manifestUrl
    ) {
    }

    private record ExportSource(
            ListingTask task,
            TextVersion textVersion,
            ImageVersion imageVersion,
            List<ImageAsset> assets
    ) {
    }
}
