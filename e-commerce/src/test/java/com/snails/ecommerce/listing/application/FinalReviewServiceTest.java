package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.api.ApproveFinalSelectionRequest;
import com.snails.ecommerce.listing.api.FinalSelectionResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.TextVersionRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 终审选择应用服务测试。
 *
 * <p>使用真实 H2 仓储验证最终版本选择、任务完成状态和跨任务边界。</p>
 */
@SpringBootTest
class FinalReviewServiceTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private TextVersionRepository textVersionRepository;

    @Autowired
    private ImageVersionRepository imageVersionRepository;

    private FinalReviewService service;

    @BeforeEach
    void setUp() {
        imageVersionRepository.deleteAll();
        textVersionRepository.deleteAll();
        listingTaskRepository.deleteAll();
        service = new FinalReviewService(
                listingTaskRepository,
                textVersionRepository,
                imageVersionRepository);
    }

    @Test
    void approvesFinalSelectionAndCompletesTask() {
        ListingTask task = saveWaitingFinalTask("task_final");
        TextVersion firstText = saveTextVersion("text_001", task.getTaskId(), true);
        TextVersion selectedText = saveTextVersion("text_002", task.getTaskId(), false);
        ImageVersion firstImage = saveImageVersion("image_001", task.getTaskId(), true, GenerationStatus.SUCCEEDED);
        ImageVersion selectedImage = saveImageVersion("image_002", task.getTaskId(), false, GenerationStatus.SUCCEEDED);

        FinalSelectionResponse response = service.approveFinalSelection(
                task.getTaskId(),
                new ApproveFinalSelectionRequest(selectedText.getVersionId(), selectedImage.getVersionId()));

        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.selectedTextVersionId()).isEqualTo(selectedText.getVersionId());
        assertThat(response.selectedImageVersionId()).isEqualTo(selectedImage.getVersionId());
        assertThat(response.updatedAt()).isNotNull();

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.COMPLETED);
        assertThat(savedTask.getSelectedTextVersionId()).isEqualTo(selectedText.getVersionId());
        assertThat(savedTask.getSelectedImageVersionId()).isEqualTo(selectedImage.getVersionId());
        assertThat(textVersionRepository.findById(firstText.getVersionId()).orElseThrow().isSelected()).isFalse();
        assertThat(textVersionRepository.findById(selectedText.getVersionId()).orElseThrow().isSelected()).isTrue();
        assertThat(imageVersionRepository.findById(firstImage.getVersionId()).orElseThrow().isSelected()).isFalse();
        assertThat(imageVersionRepository.findById(selectedImage.getVersionId()).orElseThrow().isSelected()).isTrue();
    }

    @Test
    void rejectsFinalSelectionWhenTaskDoesNotExist() {
        assertThatThrownBy(() -> service.approveFinalSelection(
                        "task_missing",
                        new ApproveFinalSelectionRequest("text_001", "image_001")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsFinalSelectionWhenTaskIsNotWaitingFinalApprove() {
        ListingTask task = saveWaitingFinalTask("task_generating");
        task.setStatus(ListingTaskStatus.GENERATING);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_001", "image_001")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsMissingTextVersion() {
        ListingTask task = saveWaitingFinalTask("task_missing_text");
        saveImageVersion("image_001", task.getTaskId(), false, GenerationStatus.SUCCEEDED);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_missing", "image_001")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsMissingImageVersion() {
        ListingTask task = saveWaitingFinalTask("task_missing_image");
        saveTextVersion("text_001", task.getTaskId(), false);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_001", "image_missing")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsTextVersionOwnedByAnotherTask() {
        ListingTask task = saveWaitingFinalTask("task_text_owner");
        ListingTask otherTask = saveWaitingFinalTask("task_text_other");
        saveTextVersion("text_other", otherTask.getTaskId(), false);
        saveImageVersion("image_001", task.getTaskId(), false, GenerationStatus.SUCCEEDED);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_other", "image_001")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsImageVersionOwnedByAnotherTask() {
        ListingTask task = saveWaitingFinalTask("task_image_owner");
        ListingTask otherTask = saveWaitingFinalTask("task_image_other");
        saveTextVersion("text_001", task.getTaskId(), false);
        saveImageVersion("image_other", otherTask.getTaskId(), false, GenerationStatus.SUCCEEDED);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_001", "image_other")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsImageVersionThatHasNotSucceeded() {
        ListingTask task = saveWaitingFinalTask("task_image_failed");
        saveTextVersion("text_001", task.getTaskId(), false);
        saveImageVersion("image_failed", task.getTaskId(), false, GenerationStatus.FAILED);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_001", "image_failed")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsRepeatedFinalApprovalAfterCompleted() {
        ListingTask task = saveWaitingFinalTask("task_completed");
        task.setStatus(ListingTaskStatus.COMPLETED);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.approveFinalSelection(
                        task.getTaskId(),
                        new ApproveFinalSelectionRequest("text_001", "image_001")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    private ListingTask saveWaitingFinalTask(String taskId) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(ListingTaskStatus.WAIT_FINAL_APPROVE);
        task.setTextStatus(GenerationStatus.SUCCEEDED);
        task.setImageStatus(GenerationStatus.SUCCEEDED);
        task.setBriefStatus(BriefStatus.APPROVED);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[\"uploads/product-images/product.png\"]");
        task.setCompetitorAsinsJson("[]");
        return listingTaskRepository.save(task);
    }

    private TextVersion saveTextVersion(String versionId, String taskId, boolean selected) {
        TextVersion version = new TextVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_final");
        version.setTitle("Wireless CarPlay Stereo");
        version.setBulletPointsJson("[\"Wireless CarPlay\"]");
        version.setDescription("A review-ready car stereo listing.");
        version.setBackendSearchTerms("wireless carplay stereo");
        version.setTargetKeywordsJson("[\"car stereo\"]");
        version.setComplianceWarningsJson("[]");
        version.setSelected(selected);
        return textVersionRepository.save(version);
    }

    private ImageVersion saveImageVersion(
            String versionId,
            String taskId,
            boolean selected,
            GenerationStatus status) {
        ImageVersion version = new ImageVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_final");
        version.setInputProductUrlsJson("[\"uploads/product-images/product.png\"]");
        version.setImageProvider("PLACEHOLDER");
        version.setImageModel("placeholder-image-model");
        version.setGenerationParamsJson("{}");
        version.setStatus(status);
        version.setSelected(selected);
        version.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        return imageVersionRepository.save(version);
    }
}
