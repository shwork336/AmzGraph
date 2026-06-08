package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.common.api.ApiResponse;
import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.competitor.api.CompetitorSnapshotResponse;
import com.snails.ecommerce.competitor.api.SubmitManualCompetitorsRequest;
import com.snails.ecommerce.competitor.application.CompetitorSnapshotService;
import com.snails.ecommerce.listing.application.BriefReviewService;
import com.snails.ecommerce.listing.application.ExportPackageService;
import com.snails.ecommerce.listing.application.FinalReviewService;
import com.snails.ecommerce.listing.application.ImageAssetComplianceService;
import com.snails.ecommerce.listing.application.ImageGenerationService;
import com.snails.ecommerce.listing.application.ListingWorkflowService;
import com.snails.ecommerce.listing.application.OperationAuditLogService;
import com.snails.ecommerce.listing.application.TextGenerationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Listing 任务接口。
 *
 * <p>第一阶段只提供任务提交接口，用于接收运营上传的产品资料、产品图和竞品 ASIN。</p>
 */
@RestController
@RequestMapping("/api/v1/listing")
@RequiredArgsConstructor
public class ListingTaskController {

    /** Listing 工作流应用服务。 */
    private final ListingWorkflowService workflowService;

    /** Brief 人工审核应用服务。 */
    private final BriefReviewService briefReviewService;

    /** 竞品快照补录与查询应用服务。 */
    private final CompetitorSnapshotService competitorSnapshotService;

    /** 文案生成应用服务。 */
    private final TextGenerationService textGenerationService;

    /** 图片生成应用服务。 */
    private final ImageGenerationService imageGenerationService;

    /** 终审选择应用服务。 */
    private final FinalReviewService finalReviewService;

    /** 导出交付包应用服务。 */
    private final ExportPackageService exportPackageService;

    /** 图片资产合规豁免应用服务。 */
    private final ImageAssetComplianceService imageAssetComplianceService;

    /** 操作审计日志应用服务。 */
    private final OperationAuditLogService operationAuditLogService;

    /**
     * 提交 Listing 资产生成任务。
     *
     * <p>请求使用 multipart/form-data：</p>
     *
     * <ul>
     *   <li>{@code file}：产品资料 Markdown 文件。</li>
     *   <li>{@code productImages}：1-4 张原始产品图。</li>
     *   <li>{@code asins}：可选竞品 ASIN 列表。</li>
     *   <li>{@code marketplace}：站点市场，默认 US。</li>
     *   <li>{@code language}：生成语言，默认 en-US。</li>
     * </ul>
     */
    @PostMapping("/submit")
    public ApiResponse<SubmitListingTaskResponse> submitTask(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productImages") List<MultipartFile> productImages,
            @RequestParam(value = "asins", required = false) List<String> asins,
            @RequestParam(value = "marketplace", defaultValue = "US") String marketplace,
            @RequestParam(value = "language", defaultValue = "en-US") String language) {
        String taskId = workflowService.submitTask(file, productImages, asins, marketplace, language);
        return ApiResponse.ok(new SubmitListingTaskResponse(taskId));
    }

    /**
     * 查询 Listing 任务详情。
     *
     * <p>该接口只读取任务当前状态和最新 Brief 摘要，不触发生成、审批、归档或导出。</p>
     */
    @GetMapping("/{taskId}")
    public ApiResponse<ListingTaskDetailResponse> getTaskDetail(@PathVariable String taskId) {
        return ApiResponse.ok(workflowService.getTaskDetail(taskId));
    }

    /**
     * 分页查询 Listing 任务列表。
     *
     * <p>任务列表用于运营工作台入口，只读取任务主状态和最终版本选择，不触发任何业务副作用。</p>
     */
    @GetMapping("/tasks")
    public ApiResponse<?> listTasks(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "marketplace", required = false) String marketplace,
            @RequestParam(value = "categoryCode", required = false) String categoryCode,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        if ((page == null) != (size == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Page and size must be provided together");
        }
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? 20 : size;
        return ApiResponse.ok(workflowService.listTasksPage(
                status,
                marketplace,
                categoryCode,
                resolvedPage,
                resolvedSize));
    }

    /**
     * 查询任务的全部 Brief 版本。
     *
     * <p>结果按创建时间和版本 ID 倒序排列，该查询不触发状态变更。</p>
     */
    @GetMapping("/{taskId}/briefs")
    public ApiResponse<List<BriefVersionResponse>> listBriefVersions(@PathVariable String taskId) {
        return ApiResponse.ok(briefReviewService.listBriefVersions(taskId));
    }

    /**
     * 查询任务当前最新 Brief。
     */
    @GetMapping("/{taskId}/briefs/latest")
    public ApiResponse<BriefVersionResponse> getLatestBrief(@PathVariable String taskId) {
        return ApiResponse.ok(briefReviewService.getLatestBrief(taskId));
    }

    /**
     * 基于当前最新 Brief 创建人工修改版本。
     */
    @PostMapping("/{taskId}/briefs")
    public ApiResponse<BriefVersionResponse> createBriefVersion(
            @PathVariable String taskId,
            @Valid @RequestBody CreateBriefVersionRequest request) {
        return ApiResponse.ok(briefReviewService.createVersion(taskId, request));
    }

    /**
     * 批准任务当前最新 Brief，并推进到图文生成阶段。
     */
    @PostMapping("/{taskId}/briefs/{briefVersionId}/approve")
    public ApiResponse<BriefVersionResponse> approveBrief(
            @PathVariable String taskId,
            @PathVariable String briefVersionId,
            @Valid @RequestBody ApproveBriefRequest request) {
        return ApiResponse.ok(briefReviewService.approveBrief(taskId, briefVersionId, request));
    }

    /**
     * 批量补录任务的手工竞品快照。
     *
     * <p>Controller 只负责请求校验和响应包装，任务状态、ASIN 范围与批量原子性由应用服务保证。</p>
     */
    @PostMapping("/{taskId}/competitors/manual")
    public ApiResponse<List<CompetitorSnapshotResponse>> submitManualCompetitors(
            @PathVariable String taskId,
            @Valid @RequestBody SubmitManualCompetitorsRequest request) {
        return ApiResponse.ok(competitorSnapshotService.submitManualSnapshots(taskId, request));
    }

    /**
     * 查询任务的全部竞品快照历史。
     */
    @GetMapping("/{taskId}/competitors")
    public ApiResponse<List<CompetitorSnapshotResponse>> listCompetitors(
            @PathVariable String taskId) {
        return ApiResponse.ok(competitorSnapshotService.listSnapshots(taskId));
    }

    /**
     * 查询任务中每个 ASIN 的最新竞品快照。
     */
    @GetMapping("/{taskId}/competitors/latest")
    public ApiResponse<List<CompetitorSnapshotResponse>> listLatestCompetitors(
            @PathVariable String taskId) {
        return ApiResponse.ok(competitorSnapshotService.listLatestSnapshots(taskId));
    }

    /**
     * 基于任务当前已批准 Brief 生成首版文案。
     *
     * <p>该接口只触发文案生成流，不触发图片生成、终审或导出。</p>
     */
    @PostMapping("/{taskId}/versions/text/generate")
    public ApiResponse<TextVersionResponse> generateInitialTextVersion(@PathVariable String taskId) {
        return ApiResponse.ok(textGenerationService.generateInitialTextVersion(taskId));
    }

    /**
     * 查询任务的全部文案版本。
     *
     * <p>查询结果按创建时间和版本 ID 倒序排列，不触发生成或状态变更。</p>
     */
    @GetMapping("/{taskId}/versions/text")
    public ApiResponse<List<TextVersionResponse>> listTextVersions(@PathVariable String taskId) {
        return ApiResponse.ok(textGenerationService.listTextVersions(taskId));
    }

    /**
     * 基于任务当前已批准 Brief 生成首版图片组。
     *
     * <p>该接口只触发图片生成流，不触发终审或导出。</p>
     */
    @PostMapping("/{taskId}/versions/image/generate")
    public ApiResponse<ImageVersionResponse> generateInitialImageVersion(@PathVariable String taskId) {
        return ApiResponse.ok(imageGenerationService.generateInitialImageVersion(taskId));
    }

    /**
     * 查询任务的全部图片版本。
     *
     * <p>查询结果按创建时间和版本 ID 倒序排列，不触发生成或状态变更。</p>
     */
    @GetMapping("/{taskId}/versions/image")
    public ApiResponse<List<ImageVersionResponse>> listImageVersions(@PathVariable String taskId) {
        return ApiResponse.ok(imageGenerationService.listImageVersions(taskId));
    }

    /**
     * 查询指定图片版本下的全部图片资产。
     *
     * <p>图片版本必须属于指定任务，归属校验由应用服务保证。</p>
     */
    @GetMapping("/{taskId}/versions/image/{imageVersionId}/assets")
    public ApiResponse<List<ImageAssetResponse>> listImageAssets(
            @PathVariable String taskId,
            @PathVariable String imageVersionId) {
        return ApiResponse.ok(imageGenerationService.listImageAssets(taskId, imageVersionId));
    }

    /**
     * 批准最终文案和图片版本选择，并完成任务。
     *
     * <p>Controller 只负责请求校验和响应包装，任务状态、版本归属与选中标记由应用服务保证。</p>
     */
    @PostMapping("/{taskId}/final/approve")
    public ApiResponse<FinalSelectionResponse> approveFinalSelection(
            @PathVariable String taskId,
            @Valid @RequestBody ApproveFinalSelectionRequest request) {
        return ApiResponse.ok(finalReviewService.approveFinalSelection(taskId, request));
    }

    /**
     * 为已完成任务生成 ZIP 默认交付包。
     *
     * <p>Controller 不接收版本 ID，导出服务始终读取任务最终选中的图文版本。</p>
     */
    @PostMapping("/{taskId}/export")
    public ApiResponse<ExportPackageResponse> exportDefaultZip(@PathVariable String taskId) {
        return ApiResponse.ok(exportPackageService.exportDefaultZip(taskId));
    }

    /**
     * 为已完成任务生成 Markdown 可选导出文件。
     *
     * <p>Markdown 导出用于知识库归档和轻量审阅，导出服务始终读取任务最终选中的图文版本。</p>
     */
    @PostMapping("/{taskId}/export/markdown")
    public ApiResponse<ExportPackageResponse> exportMarkdown(@PathVariable String taskId) {
        return ApiResponse.ok(exportPackageService.exportMarkdown(taskId));
    }

    /**
     * 为已完成任务生成 Excel 可选导出文件。
     *
     * <p>Excel 导出用于运营表格复制和归档，导出服务始终读取任务最终选中的图文版本。</p>
     */
    @PostMapping("/{taskId}/export/excel")
    public ApiResponse<ExportPackageResponse> exportExcel(@PathVariable String taskId) {
        return ApiResponse.ok(exportPackageService.exportExcel(taskId));
    }

    /**
     * 为已完成任务生成 Word 可选导出文件。
     *
     * <p>Word 导出用于对外交付和线下审阅，导出服务始终读取任务最终选中的图文版本。</p>
     */
    @PostMapping("/{taskId}/export/word")
    public ApiResponse<ExportPackageResponse> exportWord(@PathVariable String taskId) {
        return ApiResponse.ok(exportPackageService.exportWord(taskId));
    }

    /**
     * 创建待执行的导出记录。
     *
     * <p>该接口只创建 PENDING 记录并冻结当前最终图文选择，不生成实际文件。格式为空时默认 ZIP。</p>
     */
    @PostMapping("/{taskId}/exports")
    public ApiResponse<ExportPackageResponse> createPendingExportPackage(
            @PathVariable String taskId,
            @RequestParam(value = "format", required = false) String format) {
        return ApiResponse.ok(exportPackageService.createPendingExportPackage(taskId, format));
    }

    /**
     * 查询任务下的全部导出交付包。
     *
     * <p>列表用于归档导出页展示历史记录，不触发导出、重试或任务状态变更。</p>
     */
    @GetMapping("/{taskId}/exports")
    public ApiResponse<?> listExportPackages(
            @PathVariable String taskId,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        if ((page == null) != (size == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Page and size must be provided together");
        }
        if (page != null) {
            return ApiResponse.ok(exportPackageService.listExportPackagesPage(taskId, format, status, page, size));
        }
        return ApiResponse.ok(exportPackageService.listExportPackages(taskId, format, status));
    }

    /**
     * 分页查询操作审计日志。
     *
     * <p>该接口只读取统一审计日志，不触发业务状态变更。</p>
     */
    @GetMapping("/audit-logs")
    public ApiResponse<?> listAuditLogs(
            @RequestParam(value = "taskId", required = false) String taskId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "operatorId", required = false) String operatorId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        if ((page == null) != (size == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Page and size must be provided together");
        }
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? 20 : size;
        return ApiResponse.ok(operationAuditLogService.listAuditLogsPage(
                taskId,
                action,
                operatorId,
                targetType,
                targetId,
                resolvedPage,
                resolvedSize));
    }

    /**
     * 查询导出交付包状态。
     *
     * <p>查询接口不触发导出、重试或任务状态变更。</p>
     */
    @GetMapping("/export/{exportPackageId}")
    public ApiResponse<ExportPackageResponse> getExportPackage(@PathVariable String exportPackageId) {
        return ApiResponse.ok(exportPackageService.getExportPackage(exportPackageId));
    }

    /**
     * 重试失败的 ZIP 导出交付包。
     *
     * <p>重试会创建新的导出记录，原失败记录保留用于审计。</p>
     */
    @PostMapping("/export/{exportPackageId}/retry")
    public ApiResponse<ExportPackageResponse> retryExportPackage(@PathVariable String exportPackageId) {
        return ApiResponse.ok(exportPackageService.retryExportPackage(exportPackageId));
    }

    /**
     * 执行待处理的导出记录。
     *
     * <p>当前阶段由调用方显式触发执行，后续可替换为后台 worker 调用同一个服务方法。</p>
     */
    @PostMapping("/export/{exportPackageId}/run")
    public ApiResponse<ExportPackageResponse> runPendingExportPackage(@PathVariable String exportPackageId) {
        return ApiResponse.ok(exportPackageService.runPendingExportPackage(exportPackageId));
    }

    /**
     * 取消尚未执行的导出记录。
     *
     * <p>只允许取消 PENDING 状态的导出记录，取消后记录保留在导出历史中用于审计。</p>
     */
    @PostMapping("/export/{exportPackageId}/cancel")
    public ApiResponse<ExportPackageResponse> cancelPendingExportPackage(
            @PathVariable String exportPackageId,
            @RequestHeader(value = OperatorAuditContext.HEADER_OPERATOR_ID, required = false) String operatorId,
            @Valid @RequestBody CancelExportPackageRequest request) {
        CancelExportPackageRequest resolvedRequest = new CancelExportPackageRequest(
                OperatorAuditContext.resolveOperator(operatorId, request.canceledBy()),
                request.cancelReason());
        return ApiResponse.ok(exportPackageService.cancelPendingExportPackage(exportPackageId, resolvedRequest));
    }

    /**
     * 记录管理员对 FAIL 图片资产的合规豁免。
     *
     * <p>豁免不修改原始合规状态，只记录管理员、原因和时间，供导出服务判断是否允许放行。</p>
     */
    @PostMapping("/{taskId}/versions/image/{imageVersionId}/assets/{assetId}/compliance/approve")
    public ApiResponse<ImageAssetComplianceReviewResponse> approveImageAssetCompliance(
            @PathVariable String taskId,
            @PathVariable String imageVersionId,
            @PathVariable String assetId,
            @RequestHeader(value = OperatorAuditContext.HEADER_OPERATOR_ID, required = false) String operatorId,
            @Valid @RequestBody ApproveImageAssetComplianceRequest request) {
        ApproveImageAssetComplianceRequest resolvedRequest = new ApproveImageAssetComplianceRequest(
                OperatorAuditContext.resolveOperator(operatorId, request.reviewedBy()),
                request.reason());
        return ApiResponse.ok(imageAssetComplianceService.approveCompliance(
                taskId,
                imageVersionId,
                assetId,
                resolvedRequest));
    }

    /**
     * 记录运营对 WARNING 图片资产的人工确认。
     *
     * <p>确认不修改原始合规状态，只记录确认人、原因和时间，导出报告会保留这些信息。</p>
     */
    @PostMapping("/{taskId}/versions/image/{imageVersionId}/assets/{assetId}/compliance/confirm-warning")
    public ApiResponse<ImageAssetComplianceReviewResponse> confirmWarningImageAssetCompliance(
            @PathVariable String taskId,
            @PathVariable String imageVersionId,
            @PathVariable String assetId,
            @RequestHeader(value = OperatorAuditContext.HEADER_OPERATOR_ID, required = false) String operatorId,
            @Valid @RequestBody ApproveImageAssetComplianceRequest request) {
        ApproveImageAssetComplianceRequest resolvedRequest = new ApproveImageAssetComplianceRequest(
                OperatorAuditContext.resolveOperator(operatorId, request.reviewedBy()),
                request.reason());
        return ApiResponse.ok(imageAssetComplianceService.confirmWarning(
                taskId,
                imageVersionId,
                assetId,
                resolvedRequest));
    }
}
