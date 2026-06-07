package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.listing.api.ApproveImageAssetComplianceRequest;
import com.snails.ecommerce.listing.api.ImageAssetComplianceReviewResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.OperationAuditLog;
import com.snails.ecommerce.listing.infrastructure.ImageAssetRepository;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.OperationAuditLogRepository;
import com.snails.ecommerce.template.domain.ImageAssetType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * 图片资产合规豁免应用服务测试。
 *
 * <p>验证管理员只能对属于当前任务和图片版本的 FAIL 图片资产记录豁免原因。</p>
 */
@SpringBootTest
class ImageAssetComplianceServiceTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private ImageVersionRepository imageVersionRepository;

    @Autowired
    private ImageAssetRepository imageAssetRepository;

    @Autowired
    private OperationAuditLogRepository operationAuditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private ImageAssetComplianceService service;

    @BeforeEach
    void setUp() {
        operationAuditLogRepository.deleteAll();
        imageAssetRepository.deleteAll();
        imageVersionRepository.deleteAll();
        listingTaskRepository.deleteAll();
        OperationAuditLogService operationAuditLogService = new OperationAuditLogService(
                operationAuditLogRepository,
                new IdGenerator(),
                objectMapper);
        service = new ImageAssetComplianceService(
                listingTaskRepository,
                imageVersionRepository,
                imageAssetRepository,
                operationAuditLogService);
    }

    @Test
    void approvesFailedAssetComplianceWithReason() {
        ListingTask task = saveTask("task_compliance");
        ImageVersion imageVersion = saveImageVersion("image_001", task.getTaskId());
        ImageAsset asset = saveAsset("asset_failed", imageVersion.getVersionId(), "FAIL");

        ImageAssetComplianceReviewResponse response = service.approveCompliance(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                new ApproveImageAssetComplianceRequest(
                        "admin@example.com",
                        "Confirmed acceptable after manual review."));

        assertThat(response.assetId()).isEqualTo("asset_failed");
        assertThat(response.complianceStatus()).isEqualTo("FAIL");
        assertThat(response.complianceReviewedBy()).isEqualTo("admin@example.com");
        assertThat(response.complianceReviewReason()).isEqualTo("Confirmed acceptable after manual review.");
        assertThat(response.complianceReviewedAt()).isNotNull();

        ImageAsset saved = imageAssetRepository.findById("asset_failed").orElseThrow();
        assertThat(saved.getComplianceReviewedBy()).isEqualTo("admin@example.com");
        assertThat(saved.getComplianceReviewReason()).isEqualTo("Confirmed acceptable after manual review.");
        assertThat(saved.getComplianceReviewedAt()).isNotNull();

        List<OperationAuditLog> auditLogs = operationAuditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscAuditLogIdAsc("IMAGE_ASSET", "asset_failed");
        assertThat(auditLogs).hasSize(1);
        OperationAuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("IMAGE_ASSET_COMPLIANCE_APPROVED");
        assertThat(auditLog.getOperatorId()).isEqualTo("admin@example.com");
        assertThat(auditLog.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(auditLog.getReason()).isEqualTo("Confirmed acceptable after manual review.");
        assertThat(auditLog.getDetailJson()).contains("\"complianceStatus\":\"FAIL\"");
    }

    @Test
    void rejectsApprovingNonFailedAsset() {
        ListingTask task = saveTask("task_compliance_pass");
        ImageVersion imageVersion = saveImageVersion("image_pass", task.getTaskId());
        ImageAsset asset = saveAsset("asset_pass", imageVersion.getVersionId(), "PASS");

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
        assertThat(operationAuditLogRepository.findAll()).isEmpty();
    }

    @Test
    void confirmsWarningAssetComplianceWithReason() {
        ListingTask task = saveTask("task_warning_confirm");
        ImageVersion imageVersion = saveImageVersion("image_warning", task.getTaskId());
        ImageAsset asset = saveAsset("asset_warning", imageVersion.getVersionId(), "WARNING");

        ImageAssetComplianceReviewResponse response = service.confirmWarning(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                new ApproveImageAssetComplianceRequest(
                        "operator@example.com",
                        "Reviewed warning and accepted for final export."));

        assertThat(response.assetId()).isEqualTo("asset_warning");
        assertThat(response.complianceStatus()).isEqualTo("WARNING");
        assertThat(response.complianceReviewedBy()).isEqualTo("operator@example.com");
        assertThat(response.complianceReviewReason()).isEqualTo("Reviewed warning and accepted for final export.");
        assertThat(response.complianceReviewedAt()).isNotNull();

        List<OperationAuditLog> auditLogs = operationAuditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscAuditLogIdAsc("IMAGE_ASSET", "asset_warning");
        assertThat(auditLogs).hasSize(1);
        OperationAuditLog auditLog = auditLogs.get(0);
        assertThat(auditLog.getAction()).isEqualTo("IMAGE_ASSET_WARNING_CONFIRMED");
        assertThat(auditLog.getOperatorId()).isEqualTo("operator@example.com");
        assertThat(auditLog.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(auditLog.getReason()).isEqualTo("Reviewed warning and accepted for final export.");
        assertThat(auditLog.getDetailJson()).contains("\"complianceStatus\":\"WARNING\"");
    }

    @Test
    void rejectsConfirmingFailedAssetAsWarning() {
        ListingTask task = saveTask("task_warning_fail");
        ImageVersion imageVersion = saveImageVersion("image_warning_fail", task.getTaskId());
        ImageAsset asset = saveAsset("asset_warning_fail", imageVersion.getVersionId(), "FAIL");

        assertThatThrownBy(() -> service.confirmWarning(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsConfirmingPassedAssetAsWarning() {
        ListingTask task = saveTask("task_warning_pass");
        ImageVersion imageVersion = saveImageVersion("image_warning_pass", task.getTaskId());
        ImageAsset asset = saveAsset("asset_warning_pass", imageVersion.getVersionId(), "PASS");

        assertThatThrownBy(() -> service.confirmWarning(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsImageVersionBelongsToAnotherTask() {
        ListingTask task = saveTask("task_owner");
        ListingTask otherTask = saveTask("task_other");
        ImageVersion otherVersion = saveImageVersion("image_other", otherTask.getTaskId());
        ImageAsset asset = saveAsset("asset_other", otherVersion.getVersionId(), "FAIL");

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                otherVersion.getVersionId(),
                asset.getAssetId(),
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsAssetBelongsToAnotherImageVersion() {
        ListingTask task = saveTask("task_asset_owner");
        ImageVersion imageVersion = saveImageVersion("image_owner", task.getTaskId());
        ImageVersion otherVersion = saveImageVersion("image_other_owner", task.getTaskId());
        ImageAsset asset = saveAsset("asset_other_owner", otherVersion.getVersionId(), "FAIL");

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsMissingTask() {
        assertThatThrownBy(() -> service.approveCompliance(
                "task_missing",
                "image_missing",
                "asset_missing",
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsMissingImageVersion() {
        ListingTask task = saveTask("task_missing_image");

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                "image_missing",
                "asset_missing",
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsMissingAsset() {
        ListingTask task = saveTask("task_missing_asset");
        ImageVersion imageVersion = saveImageVersion("image_missing_asset", task.getTaskId());

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                imageVersion.getVersionId(),
                "asset_missing",
                validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsBlankReviewer() {
        ListingTask task = saveTask("task_blank_reviewer");
        ImageVersion imageVersion = saveImageVersion("image_blank_reviewer", task.getTaskId());
        ImageAsset asset = saveAsset("asset_blank_reviewer", imageVersion.getVersionId(), "FAIL");

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                new ApproveImageAssetComplianceRequest("", "Confirmed acceptable.")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsBlankReason() {
        ListingTask task = saveTask("task_blank_reason");
        ImageVersion imageVersion = saveImageVersion("image_blank_reason", task.getTaskId());
        ImageAsset asset = saveAsset("asset_blank_reason", imageVersion.getVersionId(), "FAIL");

        assertThatThrownBy(() -> service.approveCompliance(
                task.getTaskId(),
                imageVersion.getVersionId(),
                asset.getAssetId(),
                new ApproveImageAssetComplianceRequest("admin@example.com", "")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private ApproveImageAssetComplianceRequest validRequest() {
        return new ApproveImageAssetComplianceRequest(
                "admin@example.com",
                "Confirmed acceptable after manual review.");
    }

    private ListingTask saveTask(String taskId) {
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
        task.setOriginalProductUrlsJson("[]");
        task.setCompetitorAsinsJson("[]");
        task.setSelectedTextVersionId("text_001");
        task.setSelectedImageVersionId("image_001");
        return listingTaskRepository.save(task);
    }

    private ImageVersion saveImageVersion(String versionId, String taskId) {
        ImageVersion version = new ImageVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_001");
        version.setInputProductUrlsJson("[]");
        version.setImageProvider("PLACEHOLDER");
        version.setImageModel("placeholder-image-model");
        version.setGenerationParamsJson("{}");
        version.setStatus(GenerationStatus.SUCCEEDED);
        version.setSelected(true);
        return imageVersionRepository.save(version);
    }

    private ImageAsset saveAsset(String assetId, String imageVersionId, String complianceStatus) {
        ImageAsset asset = new ImageAsset();
        asset.setAssetId(assetId);
        asset.setImageVersionId(imageVersionId);
        asset.setType(ImageAssetType.MAIN_IMAGE);
        asset.setPrompt("Generate MAIN_IMAGE image");
        asset.setRewrittenPrompt("Placeholder MAIN_IMAGE image");
        asset.setGeneratedImageUrl("generated-images/" + imageVersionId + "/MAIN_IMAGE.png");
        asset.setSizeProfile("MAIN_IMAGE");
        asset.setTargetWidth(2000);
        asset.setTargetHeight(2000);
        asset.setComplianceStatus(complianceStatus);
        asset.setComplianceMethodsJson("[\"PLACEHOLDER_RULE_CHECK\"]");
        asset.setComplianceIssuesJson("[]");
        asset.setSortOrder(1);
        return imageAssetRepository.save(asset);
    }
}
