package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.ApproveBriefRequest;
import com.snails.ecommerce.listing.api.BriefVersionResponse;
import com.snails.ecommerce.listing.api.CreateBriefVersionRequest;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Brief 人工审核应用服务测试。
 *
 * <p>使用真实 H2 仓储验证 Brief 版本顺序、线性版本关系和任务状态边界。</p>
 */
@SpringBootTest
class BriefReviewServiceTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private ListingBriefVersionRepository listingBriefVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private BriefReviewService service;

    @BeforeEach
    void setUp() {
        listingBriefVersionRepository.deleteAll();
        listingTaskRepository.deleteAll();
        service = new BriefReviewService(
                listingTaskRepository,
                listingBriefVersionRepository,
                new IdGenerator(),
                objectMapper);
    }

    @Test
    void listsBriefVersionsNewestFirst() {
        ListingTask task = saveWaitingTask("task_list");
        ListingBriefVersion first = saveBrief("brief_001", task.getTaskId(), null, "SYSTEM");
        first.setCreatedAt(LocalDateTime.of(2026, 6, 5, 10, 0));
        listingBriefVersionRepository.save(first);
        ListingBriefVersion second = saveBrief("brief_002", task.getTaskId(), first.getBriefVersionId(), "operator@example.com");
        second.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        listingBriefVersionRepository.save(second);

        List<BriefVersionResponse> versions = service.listBriefVersions(task.getTaskId());

        assertThat(versions)
                .extracting(BriefVersionResponse::briefVersionId)
                .containsExactly("brief_002", "brief_001");
    }

    @Test
    void getsLatestBrief() {
        ListingTask task = saveWaitingTask("task_latest");
        ListingBriefVersion brief = saveBrief("brief_latest", task.getTaskId(), null, "SYSTEM");

        BriefVersionResponse response = service.getLatestBrief(task.getTaskId());

        assertThat(response.briefVersionId()).isEqualTo(brief.getBriefVersionId());
        assertThat(response.coreSellingPoints()).containsExactly("Wireless CarPlay");
        assertThat(response.createdBy()).isEqualTo("SYSTEM");
    }

    @Test
    void createsVersionFromLatestBrief() {
        ListingTask task = saveWaitingTask("task_create");
        ListingBriefVersion original = saveBrief("brief_001", task.getTaskId(), null, "SYSTEM");
        CreateBriefVersionRequest request = createRequest(original.getBriefVersionId());

        BriefVersionResponse created = service.createVersion(task.getTaskId(), request);

        assertThat(created.briefVersionId()).startsWith("brief_");
        assertThat(created.parentBriefVersionId()).isEqualTo(original.getBriefVersionId());
        assertThat(created.createdBy()).isEqualTo("operator@example.com");
        assertThat(created.targetAudience()).isEqualTo("Drivers upgrading an older car stereo");
        assertThat(created.coreSellingPoints()).containsExactly("Wireless CarPlay", "Android Auto");
        assertThat(created.approved()).isFalse();
        assertThat(listingBriefVersionRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsCreatingVersionFromHistoricalBrief() {
        ListingTask task = saveWaitingTask("task_historical");
        ListingBriefVersion first = saveBrief("brief_001", task.getTaskId(), null, "SYSTEM");
        saveBrief("brief_002", task.getTaskId(), first.getBriefVersionId(), "operator@example.com");

        assertThatThrownBy(() -> service.createVersion(task.getTaskId(), createRequest(first.getBriefVersionId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsCreatingVersionWhenTaskIsNotWaitingForApproval() {
        ListingTask task = saveWaitingTask("task_generating");
        task.setStatus(ListingTaskStatus.GENERATING);
        task.setBriefStatus(BriefStatus.APPROVED);
        listingTaskRepository.save(task);
        ListingBriefVersion brief = saveBrief("brief_001", task.getTaskId(), null, "SYSTEM");

        assertThatThrownBy(() -> service.createVersion(task.getTaskId(), createRequest(brief.getBriefVersionId())))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void approvesLatestBriefAndMovesTaskToGenerating() {
        ListingTask task = saveWaitingTask("task_approve");
        ListingBriefVersion brief = saveBrief("brief_approve", task.getTaskId(), null, "operator@example.com");

        BriefVersionResponse approved = service.approveBrief(
                task.getTaskId(),
                brief.getBriefVersionId(),
                new ApproveBriefRequest("reviewer@example.com"));

        assertThat(approved.approved()).isTrue();
        assertThat(approved.approvedBy()).isEqualTo("reviewer@example.com");
        assertThat(approved.approvedAt()).isNotNull();

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.GENERATING);
        assertThat(savedTask.getBriefStatus()).isEqualTo(BriefStatus.APPROVED);
        assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
        assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
    }

    @Test
    void rejectsApprovingHistoricalBrief() {
        ListingTask task = saveWaitingTask("task_approve_historical");
        ListingBriefVersion first = saveBrief("brief_001", task.getTaskId(), null, "SYSTEM");
        saveBrief("brief_002", task.getTaskId(), first.getBriefVersionId(), "operator@example.com");

        assertThatThrownBy(() -> service.approveBrief(
                        task.getTaskId(),
                        first.getBriefVersionId(),
                        new ApproveBriefRequest("reviewer@example.com")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsRepeatedApproval() {
        ListingTask task = saveWaitingTask("task_repeat_approve");
        ListingBriefVersion brief = saveBrief("brief_repeat", task.getTaskId(), null, "operator@example.com");
        service.approveBrief(
                task.getTaskId(),
                brief.getBriefVersionId(),
                new ApproveBriefRequest("reviewer@example.com"));

        assertThatThrownBy(() -> service.approveBrief(
                        task.getTaskId(),
                        brief.getBriefVersionId(),
                        new ApproveBriefRequest("reviewer@example.com")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsBriefOwnedByAnotherTask() {
        ListingTask targetTask = saveWaitingTask("task_target");
        ListingTask otherTask = saveWaitingTask("task_other");
        ListingBriefVersion otherBrief = saveBrief(
                "brief_other",
                otherTask.getTaskId(),
                null,
                "operator@example.com");

        assertThatThrownBy(() -> service.approveBrief(
                        targetTask.getTaskId(),
                        otherBrief.getBriefVersionId(),
                        new ApproveBriefRequest("reviewer@example.com")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private ListingTask saveWaitingTask(String taskId) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        task.setTextStatus(GenerationStatus.NOT_STARTED);
        task.setImageStatus(GenerationStatus.NOT_STARTED);
        task.setBriefStatus(BriefStatus.WAIT_APPROVE);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[]");
        task.setCompetitorAsinsJson("[]");
        return listingTaskRepository.save(task);
    }

    private ListingBriefVersion saveBrief(
            String briefVersionId,
            String taskId,
            String parentBriefVersionId,
            String createdBy) {
        ListingBriefVersion brief = new ListingBriefVersion();
        brief.setBriefVersionId(briefVersionId);
        brief.setTaskId(taskId);
        brief.setParentBriefVersionId(parentBriefVersionId);
        brief.setTargetAudience("Amazon US car stereo buyers");
        brief.setCoreSellingPointsJson("[\"Wireless CarPlay\"]");
        brief.setTargetKeywordsJson("[\"car stereo\"]");
        brief.setForbiddenClaimsJson("[]");
        brief.setImageDirectionPromptsJson("[]");
        brief.setComplianceNotesJson("[]");
        brief.setApproved(false);
        brief.setCreatedBy(createdBy);
        return listingBriefVersionRepository.save(brief);
    }

    private CreateBriefVersionRequest createRequest(String baseBriefVersionId) {
        return new CreateBriefVersionRequest(
                baseBriefVersionId,
                "operator@example.com",
                "Drivers upgrading an older car stereo",
                List.of("Wireless CarPlay", "Android Auto"),
                List.of("car stereo", "wireless carplay"),
                List.of("Do not claim universal compatibility"),
                List.of("Show dashboard installation"),
                List.of("Verify vehicle compatibility"));
    }
}
