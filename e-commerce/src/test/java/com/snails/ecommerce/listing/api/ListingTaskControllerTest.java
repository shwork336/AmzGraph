package com.snails.ecommerce.listing.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.error.GlobalExceptionHandler;
import com.snails.ecommerce.competitor.api.CompetitorSnapshotResponse;
import com.snails.ecommerce.competitor.application.CompetitorSnapshotService;
import com.snails.ecommerce.listing.application.BriefReviewService;
import com.snails.ecommerce.listing.application.TextGenerationService;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.application.ListingWorkflowService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
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
                new ListingTaskDetailResponse.BriefSummary(
                        "brief_123",
                        "Amazon US car stereo buyers",
                        false));
        when(workflowService.getTaskDetail("task_123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/listing/task_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.taskId").value("task_123"))
                .andExpect(jsonPath("$.data.status").value("WAIT_BRIEF_APPROVE"))
                .andExpect(jsonPath("$.data.latestBrief.briefVersionId").value("brief_123"));
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
