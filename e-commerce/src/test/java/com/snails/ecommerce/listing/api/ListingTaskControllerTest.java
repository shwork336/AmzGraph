package com.snails.ecommerce.listing.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snails.ecommerce.common.api.PagedResponse;
import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.error.GlobalExceptionHandler;
import com.snails.ecommerce.competitor.api.CompetitorSnapshotResponse;
import com.snails.ecommerce.competitor.application.CompetitorSnapshotService;
import com.snails.ecommerce.listing.application.BriefReviewService;
import com.snails.ecommerce.listing.application.ExportPackageService;
import com.snails.ecommerce.listing.application.FinalReviewService;
import com.snails.ecommerce.listing.application.ImageAssetComplianceService;
import com.snails.ecommerce.listing.application.ImageGenerationService;
import com.snails.ecommerce.listing.application.ListingWorkflowService;
import com.snails.ecommerce.listing.application.OperationAuditLogService;
import com.snails.ecommerce.listing.application.TextGenerationService;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

/**
 * Listing 任务接口测试。
 *
 * <p>该测试只固定 HTTP 层契约：前端通过 multipart 提交 Markdown 产品资料、产品图和可选 ASIN，
 * Controller 调用工作流服务后返回统一响应结构。</p>
 */
@WebMvcTest(controllers = ListingTaskController.class)
@Import(GlobalExceptionHandler.class)
class ListingTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListingWorkflowService workflowService;

    @MockitoBean
    private BriefReviewService briefReviewService;

    @MockitoBean
    private CompetitorSnapshotService competitorSnapshotService;

    @MockitoBean
    private TextGenerationService textGenerationService;

    @MockitoBean
    private ImageGenerationService imageGenerationService;

    @MockitoBean
    private FinalReviewService finalReviewService;

    @MockitoBean
    private ExportPackageService exportPackageService;

    @MockitoBean
    private ImageAssetComplianceService imageAssetComplianceService;

    @MockitoBean
    private OperationAuditLogService operationAuditLogService;

    @Test
    void submitsListingTask() throws Exception {
        MockMultipartFile documentFile = new MockMultipartFile(
                "file",
                "product.md",
                "text/markdown",
                "# Product".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile productImage = new MockMultipartFile(
                "productImages",
                "product.png",
                "image/png",
                "image-bytes".getBytes(StandardCharsets.UTF_8));

        when(workflowService.submitTask(
                any(MultipartFile.class),
                anyList(),
                eq(List.of("B000TEST")),
                eq("US"),
                eq("en-US")))
                .thenReturn("task_123");

        mockMvc.perform(multipart("/api/v1/listing/submit")
                        .file(documentFile)
                        .file(productImage)
                        .param("asins", "B000TEST")
                        .param("marketplace", "US")
                        .param("language", "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"));
    }

    @Test
    void submitsManualCompetitorSnapshots() throws Exception {
        when(competitorSnapshotService.submitManualSnapshots(eq("task_123"), any()))
                .thenReturn(List.of(competitorResponse()));

        mockMvc.perform(post("/api/v1/listing/task_123/competitors/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdBy": "operator@example.com",
                                  "snapshots": [{
                                    "asin": "B0FIRST",
                                    "title": "First Car Stereo",
                                    "bulletPoints": [],
                                    "rating": 4.5,
                                    "reviewCount": 120,
                                    "reviewPainPoints": [],
                                    "keywordSignals": [],
                                    "sourceName": "Manual Entry"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].asin").value("B0FIRST"))
                .andExpect(jsonPath("$.data[0].sourceType").value("MANUAL"));
    }

    @Test
    void listsCompetitorSnapshotHistory() throws Exception {
        when(competitorSnapshotService.listSnapshots("task_123"))
                .thenReturn(List.of(competitorResponse()));

        mockMvc.perform(get("/api/v1/listing/task_123/competitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].asin").value("B0FIRST"));
    }

    @Test
    void listsLatestCompetitorSnapshots() throws Exception {
        when(competitorSnapshotService.listLatestSnapshots("task_123"))
                .thenReturn(List.of(competitorResponse()));

        mockMvc.perform(get("/api/v1/listing/task_123/competitors/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sourceType").value("MANUAL"));
    }

    @Test
    void rejectsInvalidManualCompetitorRequest() throws Exception {
        mockMvc.perform(post("/api/v1/listing/task_123/competitors/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdBy": "",
                                  "snapshots": [{
                                    "asin": "",
                                    "title": "Invalid",
                                    "bulletPoints": [],
                                    "rating": 5.1,
                                    "reviewPainPoints": [],
                                    "keywordSignals": []
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsEmptyManualCompetitorSnapshots() throws Exception {
        mockMvc.perform(post("/api/v1/listing/task_123/competitors/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdBy": "operator@example.com",
                                  "snapshots": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesCompetitorServiceBusinessError() throws Exception {
        when(competitorSnapshotService.submitManualSnapshots(eq("task_123"), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task does not allow competitor input: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/competitors/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "createdBy": "operator@example.com",
                                  "snapshots": [{
                                    "asin": "B0FIRST",
                                    "title": "First Car Stereo",
                                    "bulletPoints": [],
                                    "reviewPainPoints": [],
                                    "keywordSignals": []
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void getsListingTaskDetail() throws Exception {
        ListingTaskDetailResponse response = new ListingTaskDetailResponse(
                "task_123",
                ListingTaskStatus.WAIT_BRIEF_APPROVE.name(),
                GenerationStatus.NOT_STARTED.name(),
                GenerationStatus.NOT_STARTED.name(),
                BriefStatus.WAIT_APPROVE.name(),
                "CAR_STEREO",
                "tpl_car_stereo_us_en",
                "US",
                "en-US",
                List.of("uploads/product-images/product.png"),
                List.of("B000TEST"),
                "text_001",
                "image_001",
                new ListingTaskDetailResponse.BriefSummary(
                        "brief_123",
                        "Amazon US car stereo buyers",
                        false),
                LocalDateTime.of(2026, 6, 7, 10, 0),
                LocalDateTime.of(2026, 6, 7, 11, 0));
        when(workflowService.getTaskDetail("task_123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/listing/task_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.status").value("WAIT_BRIEF_APPROVE"))
                .andExpect(jsonPath("$.data.selectedTextVersionId").value("text_001"))
                .andExpect(jsonPath("$.data.selectedImageVersionId").value("image_001"))
                .andExpect(jsonPath("$.data.latestBrief.briefVersionId").value("brief_123"));
    }

    @Test
    void listsTasksPage() throws Exception {
        when(workflowService.listTasksPage("COMPLETED", "US", "CAR_STEREO", 0, 20))
                .thenReturn(new PagedResponse<>(
                        List.of(taskSummaryResponse("task_123", "COMPLETED")),
                        0,
                        20,
                        1,
                        1,
                        false,
                        false));

        mockMvc.perform(get("/api/v1/listing/tasks")
                        .param("status", "COMPLETED")
                        .param("marketplace", "US")
                        .param("categoryCode", "CAR_STEREO")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].taskId").value("task_123"))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void rejectsTaskListPageWithoutSize() throws Exception {
        mockMvc.perform(get("/api/v1/listing/tasks")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listsBriefVersions() throws Exception {
        when(briefReviewService.listBriefVersions("task_123"))
                .thenReturn(List.of(briefResponse("brief_002", "brief_001", "operator@example.com", false)));

        mockMvc.perform(get("/api/v1/listing/task_123/briefs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].briefVersionId").value("brief_002"))
                .andExpect(jsonPath("$.data[0].createdBy").value("operator@example.com"));
    }

    @Test
    void getsLatestBrief() throws Exception {
        when(briefReviewService.getLatestBrief("task_123"))
                .thenReturn(briefResponse("brief_002", "brief_001", "operator@example.com", false));

        mockMvc.perform(get("/api/v1/listing/task_123/briefs/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.briefVersionId").value("brief_002"));
    }

    @Test
    void createsBriefVersion() throws Exception {
        when(briefReviewService.createVersion(eq("task_123"), any(CreateBriefVersionRequest.class)))
                .thenReturn(briefResponse("brief_002", "brief_001", "operator@example.com", false));

        mockMvc.perform(post("/api/v1/listing/task_123/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseBriefVersionId": "brief_001",
                                  "createdBy": "operator@example.com",
                                  "targetAudience": "Drivers upgrading an older car stereo",
                                  "coreSellingPoints": ["Wireless CarPlay"],
                                  "targetKeywords": ["car stereo"],
                                  "forbiddenClaims": [],
                                  "imageDirectionPrompts": [],
                                  "complianceNotes": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.briefVersionId").value("brief_002"))
                .andExpect(jsonPath("$.data.createdBy").value("operator@example.com"));
    }

    @Test
    void approvesBrief() throws Exception {
        when(briefReviewService.approveBrief(
                eq("task_123"),
                eq("brief_002"),
                any(ApproveBriefRequest.class)))
                .thenReturn(briefResponse("brief_002", "brief_001", "operator@example.com", true));

        mockMvc.perform(post("/api/v1/listing/task_123/briefs/brief_002/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvedBy": "reviewer@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.briefVersionId").value("brief_002"))
                .andExpect(jsonPath("$.data.approved").value(true));
    }

    @Test
    void rejectsCreateBriefWithoutCreatedBy() throws Exception {
        mockMvc.perform(post("/api/v1/listing/task_123/briefs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseBriefVersionId": "brief_001",
                                  "createdBy": "",
                                  "targetAudience": "Drivers upgrading an older car stereo",
                                  "coreSellingPoints": [],
                                  "targetKeywords": [],
                                  "forbiddenClaims": [],
                                  "imageDirectionPrompts": [],
                                  "complianceNotes": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsApproveBriefWithoutApprovedBy() throws Exception {
        mockMvc.perform(post("/api/v1/listing/task_123/briefs/brief_002/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvedBy": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsTaskStatusInvalidFromBriefService() throws Exception {
        when(briefReviewService.approveBrief(
                eq("task_123"),
                eq("brief_001"),
                any(ApproveBriefRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Brief version is not the latest version: brief_001"));

        mockMvc.perform(post("/api/v1/listing/task_123/briefs/brief_001/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approvedBy": "reviewer@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void generatesInitialTextVersion() throws Exception {
        when(textGenerationService.generateInitialTextVersion("task_123"))
                .thenReturn(textResponse("text_001", null));

        mockMvc.perform(post("/api/v1/listing/task_123/versions/text/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionId").value("text_001"))
                .andExpect(jsonPath("$.data.briefVersionId").value("brief_123"))
                .andExpect(jsonPath("$.data.title").value("Wireless CarPlay Stereo for Amazon US"))
                .andExpect(jsonPath("$.data.bulletPoints[0]").value("Wireless CarPlay"));
    }

    @Test
    void listsTextVersions() throws Exception {
        when(textGenerationService.listTextVersions("task_123"))
                .thenReturn(List.of(textResponse("text_002", "text_001")));

        mockMvc.perform(get("/api/v1/listing/task_123/versions/text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].versionId").value("text_002"))
                .andExpect(jsonPath("$.data[0].parentVersionId").value("text_001"))
                .andExpect(jsonPath("$.data[0].targetKeywords[0]").value("car stereo"));
    }

    @Test
    void returnsTaskStatusInvalidFromTextGenerationService() throws Exception {
        when(textGenerationService.generateInitialTextVersion("task_123"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not ready for text generation: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/versions/text/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void generatesInitialImageVersion() throws Exception {
        when(imageGenerationService.generateInitialImageVersion("task_123"))
                .thenReturn(imageVersionResponse("image_001", null));

        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionId").value("image_001"))
                .andExpect(jsonPath("$.data.briefVersionId").value("brief_123"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.inputProductUrls[0]").value("uploads/product-images/product.png"));
    }

    @Test
    void listsImageVersions() throws Exception {
        when(imageGenerationService.listImageVersions("task_123"))
                .thenReturn(List.of(imageVersionResponse("image_002", "image_001")));

        mockMvc.perform(get("/api/v1/listing/task_123/versions/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].versionId").value("image_002"))
                .andExpect(jsonPath("$.data[0].parentVersionId").value("image_001"))
                .andExpect(jsonPath("$.data[0].imageProvider").value("PLACEHOLDER"));
    }

    @Test
    void listsImageAssets() throws Exception {
        when(imageGenerationService.listImageAssets("task_123", "image_001"))
                .thenReturn(List.of(imageAssetResponse("asset_001", "image_001")));

        mockMvc.perform(get("/api/v1/listing/task_123/versions/image/image_001/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assetId").value("asset_001"))
                .andExpect(jsonPath("$.data[0].type").value("MAIN_IMAGE"))
                .andExpect(jsonPath("$.data[0].complianceMethods[0]").value("PLACEHOLDER_RULE_CHECK"));
    }

    @Test
    void returnsTaskStatusInvalidFromImageGenerationService() throws Exception {
        when(imageGenerationService.generateInitialImageVersion("task_123"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not ready for image generation: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void approvesFinalSelection() throws Exception {
        when(finalReviewService.approveFinalSelection(eq("task_123"), any(ApproveFinalSelectionRequest.class)))
                .thenReturn(finalSelectionResponse());

        mockMvc.perform(post("/api/v1/listing/task_123/final/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedTextVersionId": "text_001",
                                  "selectedImageVersionId": "image_001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.selectedTextVersionId").value("text_001"))
                .andExpect(jsonPath("$.data.selectedImageVersionId").value("image_001"));
    }

    @Test
    void rejectsFinalSelectionWithoutVersionIds() throws Exception {
        mockMvc.perform(post("/api/v1/listing/task_123/final/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedTextVersionId": "",
                                  "selectedImageVersionId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesFinalReviewServiceBusinessError() throws Exception {
        when(finalReviewService.approveFinalSelection(eq("task_123"), any(ApproveFinalSelectionRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not waiting for final approval: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/final/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedTextVersionId": "text_001",
                                  "selectedImageVersionId": "image_001"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void exportsDefaultZipPackage() throws Exception {
        when(exportPackageService.exportDefaultZip("task_123"))
                .thenReturn(exportPackageResponse("export_001", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/task_123/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_001"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.format").value("ZIP"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_001.zip"))
                .andExpect(jsonPath("$.data.manifestUrl").value("exports/task_123/export_001-manifest.json"))
                .andExpect(jsonPath("$.data.includedAssetIds[0]").value("asset_001"));
    }

    @Test
    void exportsMarkdownPackage() throws Exception {
        when(exportPackageService.exportMarkdown("task_123"))
                .thenReturn(markdownExportPackageResponse("export_markdown_001", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/task_123/export/markdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_markdown_001"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_markdown_001.md"))
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist())
                .andExpect(jsonPath("$.data.includedAssetIds[0]").value("asset_001"));
    }

    @Test
    void exportsExcelPackage() throws Exception {
        when(exportPackageService.exportExcel("task_123"))
                .thenReturn(excelExportPackageResponse("export_excel_001", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/task_123/export/excel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_excel_001"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.format").value("EXCEL"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_excel_001.xlsx"))
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist())
                .andExpect(jsonPath("$.data.includedAssetIds[0]").value("asset_001"));
    }

    @Test
    void exportsWordPackage() throws Exception {
        when(exportPackageService.exportWord("task_123"))
                .thenReturn(wordExportPackageResponse("export_word_001", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/task_123/export/word"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_word_001"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.format").value("WORD"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_word_001.docx"))
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist())
                .andExpect(jsonPath("$.data.includedAssetIds[0]").value("asset_001"));
    }

    @Test
    void createsPendingExportPackage() throws Exception {
        when(exportPackageService.createPendingExportPackage("task_123", "WORD"))
                .thenReturn(wordExportPackageResponse("export_word_pending", "PENDING", null));

        mockMvc.perform(post("/api/v1/listing/task_123/exports")
                        .param("format", "WORD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_word_pending"))
                .andExpect(jsonPath("$.data.format").value("WORD"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void createsPendingExportPackageWithDefaultFormat() throws Exception {
        when(exportPackageService.createPendingExportPackage("task_123", null))
                .thenReturn(exportPackageResponse("export_zip_pending", "PENDING", null));

        mockMvc.perform(post("/api/v1/listing/task_123/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_zip_pending"))
                .andExpect(jsonPath("$.data.format").value("ZIP"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void preservesCreatePendingExportPackageBusinessError() throws Exception {
        when(exportPackageService.createPendingExportPackage("task_123", "PDF"))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export format is invalid: PDF"));

        mockMvc.perform(post("/api/v1/listing/task_123/exports")
                        .param("format", "PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesExportServiceBusinessError() throws Exception {
        when(exportPackageService.exportDefaultZip("task_123"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not completed: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void preservesMarkdownExportServiceBusinessError() throws Exception {
        when(exportPackageService.exportMarkdown("task_123"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not completed: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/export/markdown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void preservesExcelExportServiceBusinessError() throws Exception {
        when(exportPackageService.exportExcel("task_123"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not completed: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/export/excel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void preservesWordExportServiceBusinessError() throws Exception {
        when(exportPackageService.exportWord("task_123"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Listing task is not completed: task_123"));

        mockMvc.perform(post("/api/v1/listing/task_123/export/word"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void listsExportPackages() throws Exception {
        when(exportPackageService.listExportPackages("task_123", null, null))
                .thenReturn(List.of(
                        wordExportPackageResponse("export_word_001", "SUCCEEDED", null),
                        exportPackageResponse("export_zip_001", "FAILED", "zip failed")));

        mockMvc.perform(get("/api/v1/listing/task_123/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].exportPackageId").value("export_word_001"))
                .andExpect(jsonPath("$.data[0].format").value("WORD"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data[1].exportPackageId").value("export_zip_001"))
                .andExpect(jsonPath("$.data[1].format").value("ZIP"))
                .andExpect(jsonPath("$.data[1].failureReason").value("zip failed"));
    }

    @Test
    void listsExportPackagesWithFilters() throws Exception {
        when(exportPackageService.listExportPackages("task_123", "ZIP", "FAILED"))
                .thenReturn(List.of(exportPackageResponse("export_zip_failed", "FAILED", "zip failed")));

        mockMvc.perform(get("/api/v1/listing/task_123/exports")
                        .param("format", "ZIP")
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].exportPackageId").value("export_zip_failed"))
                .andExpect(jsonPath("$.data[0].format").value("ZIP"))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"));
    }

    @Test
    void listsExportPackagesPage() throws Exception {
        when(exportPackageService.listExportPackagesPage("task_123", "ZIP", "FAILED", 0, 20))
                .thenReturn(new PagedResponse<>(
                        List.of(exportPackageResponse("export_zip_failed", "FAILED", "zip failed")),
                        0,
                        20,
                        1,
                        1,
                        false,
                        false));

        mockMvc.perform(get("/api/v1/listing/task_123/exports")
                        .param("format", "ZIP")
                        .param("status", "FAILED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].exportPackageId").value("export_zip_failed"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.hasPrevious").value(false));
    }

    @Test
    void listsAuditLogsPage() throws Exception {
        when(operationAuditLogService.listAuditLogsPage(
                "task_123",
                "EXPORT_PACKAGE_CANCELED",
                "operator@example.com",
                "EXPORT_PACKAGE",
                "export_001",
                0,
                20))
                .thenReturn(new PagedResponse<>(
                        List.of(operationAuditLogResponse()),
                        0,
                        20,
                        1,
                        1,
                        false,
                        false));

        mockMvc.perform(get("/api/v1/listing/audit-logs")
                        .param("taskId", "task_123")
                        .param("action", "EXPORT_PACKAGE_CANCELED")
                        .param("operatorId", "operator@example.com")
                        .param("targetType", "EXPORT_PACKAGE")
                        .param("targetId", "export_001")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].auditLogId").value("audit_001"))
                .andExpect(jsonPath("$.data.items[0].action").value("EXPORT_PACKAGE_CANCELED"))
                .andExpect(jsonPath("$.data.items[0].operatorId").value("operator@example.com"))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void rejectsAuditLogPageWithoutSize() throws Exception {
        mockMvc.perform(get("/api/v1/listing/audit-logs")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsExportPackagePageWhenOnlyPageIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/listing/task_123/exports")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsExportPackagePageWhenOnlySizeIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/listing/task_123/exports")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listsEmptyExportPackages() throws Exception {
        when(exportPackageService.listExportPackages("task_123", null, null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/listing/task_123/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void preservesListExportPackagesBusinessError() throws Exception {
        when(exportPackageService.listExportPackages("task_missing", null, null))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_NOT_FOUND,
                        "Listing task not found: task_missing"));

        mockMvc.perform(get("/api/v1/listing/task_missing/exports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    @Test
    void preservesListExportPackagesInvalidFormatError() throws Exception {
        when(exportPackageService.listExportPackages("task_123", "PDF", null))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export format is invalid: PDF"));

        mockMvc.perform(get("/api/v1/listing/task_123/exports")
                        .param("format", "PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getsExportPackage() throws Exception {
        when(exportPackageService.getExportPackage("export_001"))
                .thenReturn(exportPackageResponse("export_001", "FAILED", "zip failed"));

        mockMvc.perform(get("/api/v1/listing/export/export_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_001"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value("zip failed"));
    }

    @Test
    void preservesGetExportPackageBusinessError() throws Exception {
        when(exportPackageService.getExportPackage("export_missing"))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "Export package not found: export_missing"));

        mockMvc.perform(get("/api/v1/listing/export/export_missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void retriesFailedExportPackage() throws Exception {
        when(exportPackageService.retryExportPackage("export_failed"))
                .thenReturn(exportPackageResponse("export_retry", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/export/export_failed/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_retry"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_retry.zip"));
    }

    @Test
    void runsPendingExportPackage() throws Exception {
        when(exportPackageService.runPendingExportPackage("export_pending"))
                .thenReturn(excelExportPackageResponse("export_pending", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/export/export_pending/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_pending"))
                .andExpect(jsonPath("$.data.format").value("EXCEL"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_pending.xlsx"));
    }

    @Test
    void preservesRunPendingExportPackageBusinessError() throws Exception {
        when(exportPackageService.runPendingExportPackage("export_succeeded"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Only pending export package can be run: export_succeeded"));

        mockMvc.perform(post("/api/v1/listing/export/export_succeeded/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void cancelsPendingExportPackage() throws Exception {
        when(exportPackageService.cancelPendingExportPackage(eq("export_pending"), any(CancelExportPackageRequest.class)))
                .thenReturn(exportPackageResponse("export_pending", "CANCELED", null));

        mockMvc.perform(post("/api/v1/listing/export/export_pending/cancel")
                        .header(OperatorAuditContext.HEADER_OPERATOR_ID, "header-operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canceledBy": "body-operator",
                                  "cancelReason": "Duplicate export request"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_pending"))
                .andExpect(jsonPath("$.data.format").value("ZIP"))
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.canceledBy").value("ops-user"))
                .andExpect(jsonPath("$.data.cancelReason").value("Duplicate export request"))
                .andExpect(jsonPath("$.data.canceledAt").exists());

        ArgumentCaptor<CancelExportPackageRequest> requestCaptor =
                ArgumentCaptor.forClass(CancelExportPackageRequest.class);
        verify(exportPackageService).cancelPendingExportPackage(eq("export_pending"), requestCaptor.capture());
        Assertions.assertEquals("header-operator", requestCaptor.getValue().canceledBy());
        Assertions.assertEquals("Duplicate export request", requestCaptor.getValue().cancelReason());
    }

    @Test
    void rejectsCancelPendingExportPackageWithoutReason() throws Exception {
        mockMvc.perform(post("/api/v1/listing/export/export_pending/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canceledBy": "ops-user",
                                  "cancelReason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesCancelPendingExportPackageBusinessError() throws Exception {
        when(exportPackageService.cancelPendingExportPackage(
                eq("export_succeeded"),
                any(CancelExportPackageRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Only pending export package can be canceled: export_succeeded"));

        mockMvc.perform(post("/api/v1/listing/export/export_succeeded/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canceledBy": "ops-user",
                                  "cancelReason": "No longer needed"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void retriesFailedMarkdownExportPackage() throws Exception {
        when(exportPackageService.retryExportPackage("export_markdown_failed"))
                .thenReturn(markdownExportPackageResponse("export_markdown_retry", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/export/export_markdown_failed/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_markdown_retry"))
                .andExpect(jsonPath("$.data.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_markdown_retry.md"))
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist());
    }

    @Test
    void retriesFailedExcelExportPackage() throws Exception {
        when(exportPackageService.retryExportPackage("export_excel_failed"))
                .thenReturn(excelExportPackageResponse("export_excel_retry", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/export/export_excel_failed/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_excel_retry"))
                .andExpect(jsonPath("$.data.format").value("EXCEL"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_excel_retry.xlsx"))
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist());
    }

    @Test
    void retriesFailedWordExportPackage() throws Exception {
        when(exportPackageService.retryExportPackage("export_word_failed"))
                .thenReturn(wordExportPackageResponse("export_word_retry", "SUCCEEDED", null));

        mockMvc.perform(post("/api/v1/listing/export/export_word_failed/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.exportPackageId").value("export_word_retry"))
                .andExpect(jsonPath("$.data.format").value("WORD"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.fileUrl").value("exports/task_123/export_word_retry.docx"))
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist());
    }

    @Test
    void preservesRetryExportPackageBusinessError() throws Exception {
        when(exportPackageService.retryExportPackage("export_succeeded"))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Only failed export package can be retried: export_succeeded"));

        mockMvc.perform(post("/api/v1/listing/export/export_succeeded/retry"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void approvesImageAssetCompliance() throws Exception {
        when(imageAssetComplianceService.approveCompliance(
                eq("task_123"),
                eq("image_001"),
                eq("asset_001"),
                any(ApproveImageAssetComplianceRequest.class)))
                .thenReturn(complianceReviewResponse());

        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/image_001/assets/asset_001/compliance/approve")
                        .header(OperatorAuditContext.HEADER_OPERATOR_ID, "header-admin@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "body-admin@example.com",
                                  "reason": "Confirmed acceptable after manual review."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value("asset_001"))
                .andExpect(jsonPath("$.data.complianceStatus").value("FAIL"))
                .andExpect(jsonPath("$.data.complianceReviewedBy").value("admin@example.com"))
                .andExpect(jsonPath("$.data.complianceReviewReason")
                        .value("Confirmed acceptable after manual review."));

        ArgumentCaptor<ApproveImageAssetComplianceRequest> requestCaptor =
                ArgumentCaptor.forClass(ApproveImageAssetComplianceRequest.class);
        verify(imageAssetComplianceService).approveCompliance(
                eq("task_123"),
                eq("image_001"),
                eq("asset_001"),
                requestCaptor.capture());
        Assertions.assertEquals("header-admin@example.com", requestCaptor.getValue().reviewedBy());
        Assertions.assertEquals("Confirmed acceptable after manual review.", requestCaptor.getValue().reason());
    }

    @Test
    void rejectsImageAssetComplianceApprovalWithoutReason() throws Exception {
        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/image_001/assets/asset_001/compliance/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "admin@example.com",
                                  "reason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void preservesImageAssetComplianceBusinessError() throws Exception {
        when(imageAssetComplianceService.approveCompliance(
                eq("task_123"),
                eq("image_001"),
                eq("asset_001"),
                any(ApproveImageAssetComplianceRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Only failed image asset can be approved by admin: asset_001"));

        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/image_001/assets/asset_001/compliance/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "admin@example.com",
                                  "reason": "Confirmed acceptable after manual review."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    @Test
    void confirmsWarningImageAssetCompliance() throws Exception {
        when(imageAssetComplianceService.confirmWarning(
                eq("task_123"),
                eq("image_001"),
                eq("asset_warning"),
                any(ApproveImageAssetComplianceRequest.class)))
                .thenReturn(warningComplianceReviewResponse());

        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/image_001/assets/asset_warning/compliance/confirm-warning")
                        .header(OperatorAuditContext.HEADER_OPERATOR_ID, "header-operator@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "body-operator@example.com",
                                  "reason": "Reviewed warning and accepted for final export."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value("asset_warning"))
                .andExpect(jsonPath("$.data.complianceStatus").value("WARNING"))
                .andExpect(jsonPath("$.data.complianceReviewedBy").value("operator@example.com"))
                .andExpect(jsonPath("$.data.complianceReviewReason")
                        .value("Reviewed warning and accepted for final export."));

        ArgumentCaptor<ApproveImageAssetComplianceRequest> requestCaptor =
                ArgumentCaptor.forClass(ApproveImageAssetComplianceRequest.class);
        verify(imageAssetComplianceService).confirmWarning(
                eq("task_123"),
                eq("image_001"),
                eq("asset_warning"),
                requestCaptor.capture());
        Assertions.assertEquals("header-operator@example.com", requestCaptor.getValue().reviewedBy());
        Assertions.assertEquals("Reviewed warning and accepted for final export.", requestCaptor.getValue().reason());
    }

    @Test
    void preservesConfirmWarningBusinessError() throws Exception {
        when(imageAssetComplianceService.confirmWarning(
                eq("task_123"),
                eq("image_001"),
                eq("asset_001"),
                any(ApproveImageAssetComplianceRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.TASK_STATUS_INVALID,
                        "Only warning image asset can be confirmed by operator: asset_001"));

        mockMvc.perform(post("/api/v1/listing/task_123/versions/image/image_001/assets/asset_001/compliance/confirm-warning")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewedBy": "operator@example.com",
                                  "reason": "Reviewed warning and accepted for final export."
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_STATUS_INVALID"));
    }

    private BriefVersionResponse briefResponse(
            String briefVersionId,
            String parentBriefVersionId,
            String createdBy,
            boolean approved) {
        return new BriefVersionResponse(
                briefVersionId,
                "task_123",
                parentBriefVersionId,
                "Drivers upgrading an older car stereo",
                List.of("Wireless CarPlay"),
                List.of("car stereo"),
                List.of(),
                List.of(),
                List.of(),
                approved,
                createdBy,
                approved ? "reviewer@example.com" : null,
                approved ? LocalDateTime.of(2026, 6, 6, 12, 0) : null,
                LocalDateTime.of(2026, 6, 6, 11, 0));
    }

    private TextVersionResponse textResponse(String versionId, String parentVersionId) {
        return new TextVersionResponse(
                versionId,
                "task_123",
                parentVersionId,
                "brief_123",
                null,
                "Wireless CarPlay Stereo for Amazon US",
                List.of("Wireless CarPlay", "Android Auto"),
                "A review-ready car stereo listing.",
                "wireless carplay stereo",
                List.of("car stereo"),
                List.of(),
                88,
                false,
                LocalDateTime.of(2026, 6, 6, 12, 0));
    }

    private ImageVersionResponse imageVersionResponse(String versionId, String parentVersionId) {
        return new ImageVersionResponse(
                versionId,
                "task_123",
                parentVersionId,
                "brief_123",
                null,
                null,
                List.of("uploads/product-images/product.png"),
                "PLACEHOLDER",
                "placeholder-image-model",
                "{}",
                "SUCCEEDED",
                80,
                false,
                LocalDateTime.of(2026, 6, 6, 12, 0));
    }

    private ImageAssetResponse imageAssetResponse(String assetId, String imageVersionId) {
        return new ImageAssetResponse(
                assetId,
                imageVersionId,
                "MAIN_IMAGE",
                "Generate MAIN_IMAGE image",
                "Placeholder MAIN_IMAGE image",
                "generated-images/" + imageVersionId + "/MAIN_IMAGE.png",
                null,
                "MAIN_IMAGE",
                2000,
                2000,
                "PASS",
                List.of("PLACEHOLDER_RULE_CHECK"),
                List.of(),
                null,
                null,
                null,
                1,
                LocalDateTime.of(2026, 6, 6, 12, 0));
    }

    private FinalSelectionResponse finalSelectionResponse() {
        return new FinalSelectionResponse(
                "task_123",
                "COMPLETED",
                "text_001",
                "image_001",
                LocalDateTime.of(2026, 6, 6, 13, 0));
    }

    private OperationAuditLogResponse operationAuditLogResponse() {
        return new OperationAuditLogResponse(
                "audit_001",
                "EXPORT_PACKAGE_CANCELED",
                "operator@example.com",
                "EXPORT_PACKAGE",
                "export_001",
                "task_123",
                "Duplicate export request",
                "{\"format\":\"ZIP\"}",
                LocalDateTime.of(2026, 6, 7, 13, 0));
    }

    private ListingTaskSummaryResponse taskSummaryResponse(String taskId, String status) {
        return new ListingTaskSummaryResponse(
                taskId,
                status,
                "SUCCEEDED",
                "SUCCEEDED",
                "APPROVED",
                "CAR_STEREO",
                "tpl_car_stereo_us_en",
                "US",
                "en-US",
                "text_001",
                "image_001",
                LocalDateTime.of(2026, 6, 7, 10, 0),
                LocalDateTime.of(2026, 6, 7, 11, 0));
    }

    private ExportPackageResponse exportPackageResponse(
            String exportPackageId,
            String status,
            String failureReason) {
        return new ExportPackageResponse(
                exportPackageId,
                "task_123",
                "text_001",
                "image_001",
                "ZIP",
                status,
                "exports/task_123/" + exportPackageId + ".zip",
                "exports/task_123/" + exportPackageId + "-manifest.json",
                failureReason,
                "CANCELED".equals(status) ? "ops-user" : null,
                "CANCELED".equals(status) ? "Duplicate export request" : null,
                "CANCELED".equals(status) ? LocalDateTime.of(2026, 6, 7, 12, 3) : null,
                List.of("asset_001"),
                LocalDateTime.of(2026, 6, 7, 12, 0),
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));
    }

    private ExportPackageResponse markdownExportPackageResponse(
            String exportPackageId,
            String status,
            String failureReason) {
        return new ExportPackageResponse(
                exportPackageId,
                "task_123",
                "text_001",
                "image_001",
                "MARKDOWN",
                status,
                "exports/task_123/" + exportPackageId + ".md",
                null,
                failureReason,
                null,
                null,
                null,
                List.of("asset_001"),
                LocalDateTime.of(2026, 6, 7, 12, 0),
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));
    }

    private ExportPackageResponse excelExportPackageResponse(
            String exportPackageId,
            String status,
            String failureReason) {
        return new ExportPackageResponse(
                exportPackageId,
                "task_123",
                "text_001",
                "image_001",
                "EXCEL",
                status,
                "exports/task_123/" + exportPackageId + ".xlsx",
                null,
                failureReason,
                null,
                null,
                null,
                List.of("asset_001"),
                LocalDateTime.of(2026, 6, 7, 12, 0),
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));
    }

    private ExportPackageResponse wordExportPackageResponse(
            String exportPackageId,
            String status,
            String failureReason) {
        return new ExportPackageResponse(
                exportPackageId,
                "task_123",
                "text_001",
                "image_001",
                "WORD",
                status,
                "exports/task_123/" + exportPackageId + ".docx",
                null,
                failureReason,
                null,
                null,
                null,
                List.of("asset_001"),
                LocalDateTime.of(2026, 6, 7, 12, 0),
                LocalDateTime.of(2026, 6, 7, 12, 1),
                LocalDateTime.of(2026, 6, 7, 12, 2));
    }

    private ImageAssetComplianceReviewResponse complianceReviewResponse() {
        return new ImageAssetComplianceReviewResponse(
                "asset_001",
                "image_001",
                "FAIL",
                "admin@example.com",
                "Confirmed acceptable after manual review.",
                LocalDateTime.of(2026, 6, 7, 12, 0));
    }

    private ImageAssetComplianceReviewResponse warningComplianceReviewResponse() {
        return new ImageAssetComplianceReviewResponse(
                "asset_warning",
                "image_001",
                "WARNING",
                "operator@example.com",
                "Reviewed warning and accepted for final export.",
                LocalDateTime.of(2026, 6, 7, 12, 0));
    }

    private CompetitorSnapshotResponse competitorResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 6, 12, 0);
        return new CompetitorSnapshotResponse(
                "competitor_123",
                "task_123",
                "B0FIRST",
                "First Car Stereo",
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                "MANUAL",
                "Manual Entry",
                null,
                now,
                "operator@example.com",
                now);
    }
}
