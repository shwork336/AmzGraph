package com.snails.ecommerce.listing.api;

import com.snails.ecommerce.common.api.ApiResponse;
import com.snails.ecommerce.competitor.api.CompetitorSnapshotResponse;
import com.snails.ecommerce.competitor.api.SubmitManualCompetitorsRequest;
import com.snails.ecommerce.competitor.application.CompetitorSnapshotService;
import com.snails.ecommerce.listing.application.BriefReviewService;
import com.snails.ecommerce.listing.application.ListingWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
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
}
