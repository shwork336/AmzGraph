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
import com.snails.ecommerce.listing.application.BriefReviewService;
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
}
