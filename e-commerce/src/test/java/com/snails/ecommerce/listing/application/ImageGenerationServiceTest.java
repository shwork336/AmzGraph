package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.ImageAssetResponse;
import com.snails.ecommerce.listing.api.ImageVersionResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ImageAsset;
import com.snails.ecommerce.listing.domain.ImageVersion;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.infrastructure.ImageAssetRepository;
import com.snails.ecommerce.listing.infrastructure.ImageVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.template.application.CategoryTemplateService;
import com.snails.ecommerce.template.domain.ImageAssetType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * 图片生成应用服务测试。
 *
 * <p>使用真实 H2 仓储验证 Brief 批准后的图片版本追加、图片资产保存、状态流转和错误边界。</p>
 */
@SpringBootTest
class ImageGenerationServiceTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private ListingBriefVersionRepository listingBriefVersionRepository;

    @Autowired
    private ImageVersionRepository imageVersionRepository;

    @Autowired
    private ImageAssetRepository imageAssetRepository;

    @Autowired
    private CategoryTemplateService categoryTemplateService;

    @Autowired
    private ObjectMapper objectMapper;

    private ImageGenerationService service;

    @BeforeEach
    void setUp() {
        imageAssetRepository.deleteAll();
        imageVersionRepository.deleteAll();
        listingBriefVersionRepository.deleteAll();
        listingTaskRepository.deleteAll();
        service = new ImageGenerationService(
                listingTaskRepository,
                listingBriefVersionRepository,
                imageVersionRepository,
                imageAssetRepository,
                categoryTemplateService,
                new PlaceholderImageAssetGenerator(objectMapper),
                new IdGenerator(),
                objectMapper);
    }

    @Test
    void generatesImageVersionAndAssetsFromApprovedBrief() {
        ListingTask task = saveGeneratingTask("task_image_generate");
        ListingBriefVersion brief = saveApprovedBrief("brief_image_generate", task.getTaskId());

        ImageVersionResponse response = service.generateInitialImageVersion(task.getTaskId());

        assertThat(response.versionId()).startsWith("image_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.briefVersionId()).isEqualTo(brief.getBriefVersionId());
        assertThat(response.parentVersionId()).isNull();
        assertThat(response.inputProductUrls()).containsExactly("uploads/product-images/product.png");
        assertThat(response.imageProvider()).isEqualTo("PLACEHOLDER");
        assertThat(response.imageModel()).isEqualTo("placeholder-image-model");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.qualityScore()).isEqualTo(80);

        List<ImageAsset> assets = imageAssetRepository
                .findByImageVersionIdOrderBySortOrderAscAssetIdAsc(response.versionId());
        assertThat(assets).hasSize(8);
        assertThat(assets)
                .extracting(ImageAsset::getType)
                .containsExactly(
                        ImageAssetType.MAIN_IMAGE,
                        ImageAssetType.INFOGRAPHIC,
                        ImageAssetType.LIFESTYLE,
                        ImageAssetType.DIMENSION,
                        ImageAssetType.COMPATIBILITY,
                        ImageAssetType.INSTALLATION,
                        ImageAssetType.PACKAGE_CONTENTS,
                        ImageAssetType.A_PLUS_MODULE);
        assertThat(assets.get(0).getPrompt()).contains("MAIN_IMAGE");
        assertThat(assets.get(0).getGeneratedImageUrl()).contains(response.versionId(), "MAIN_IMAGE");
        assertThat(assets.get(0).getComplianceStatus()).isEqualTo("PASS");
        assertThat(assets.get(0).getTargetWidth()).isEqualTo(2000);
        assertThat(assets.get(0).getTargetHeight()).isEqualTo(2000);
        assertThat(assets.get(7).getSizeProfile()).isEqualTo("A_PLUS_STANDARD");
        assertThat(assets.get(7).getTargetWidth()).isEqualTo(1464);
        assertThat(assets.get(7).getTargetHeight()).isEqualTo(600);

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.WAIT_FINAL_APPROVE);
        assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
    }

    @Test
    void movesTaskToFinalReviewWhenTextAlreadySucceeded() {
        ListingTask task = saveGeneratingTask("task_image_final_ready");
        saveApprovedBrief("brief_image_final_ready", task.getTaskId());

        service.generateInitialImageVersion(task.getTaskId());

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.WAIT_FINAL_APPROVE);
        assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
    }

    @Test
    void keepsTaskGeneratingWhenTextHasNotSucceeded() {
        ListingTask task = saveGeneratingTask("task_image_not_final_ready");
        task.setTextStatus(GenerationStatus.RUNNING);
        listingTaskRepository.save(task);
        saveApprovedBrief("brief_image_not_final_ready", task.getTaskId());

        service.generateInitialImageVersion(task.getTaskId());

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.GENERATING);
        assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.RUNNING);
        assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
    }

    @Test
    void createsNewVersionWithParentWhenImageAlreadyExists() {
        ListingTask task = saveGeneratingTask("task_image_parent");
        saveApprovedBrief("brief_image_parent", task.getTaskId());
        ImageVersion existing = saveImageVersion("image_001", task.getTaskId());
        existing.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        imageVersionRepository.save(existing);

        ImageVersionResponse response = service.generateInitialImageVersion(task.getTaskId());

        assertThat(response.parentVersionId()).isEqualTo("image_001");
        assertThat(imageVersionRepository.count()).isEqualTo(2);
    }

    @Test
    void listsImageVersionsNewestFirst() {
        ListingTask task = saveGeneratingTask("task_image_list");
        ImageVersion first = saveImageVersion("image_001", task.getTaskId());
        first.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        imageVersionRepository.save(first);
        ImageVersion second = saveImageVersion("image_002", task.getTaskId());
        second.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        imageVersionRepository.save(second);

        List<ImageVersionResponse> versions = service.listImageVersions(task.getTaskId());

        assertThat(versions)
                .extracting(ImageVersionResponse::versionId)
                .containsExactly("image_002", "image_001");
    }

    @Test
    void listsImageAssetsBySortOrder() {
        ListingTask task = saveGeneratingTask("task_asset_list");
        ImageVersion version = saveImageVersion("image_asset_list", task.getTaskId());
        imageAssetRepository.save(baseAsset("asset_002", version.getVersionId(), ImageAssetType.INFOGRAPHIC, 2));
        imageAssetRepository.save(baseAsset("asset_001", version.getVersionId(), ImageAssetType.MAIN_IMAGE, 1));

        List<ImageAssetResponse> assets = service.listImageAssets(task.getTaskId(), version.getVersionId());

        assertThat(assets)
                .extracting(ImageAssetResponse::assetId)
                .containsExactly("asset_001", "asset_002");
        assertThat(assets.get(0).type()).isEqualTo("MAIN_IMAGE");
        assertThat(assets.get(0).complianceMethods()).containsExactly("PLACEHOLDER_RULE_CHECK");
        assertThat(assets.get(0).complianceReviewReason()).isNull();
    }

    @Test
    void rejectsListingAssetsWhenImageVersionBelongsToAnotherTask() {
        ListingTask task = saveGeneratingTask("task_asset_owner");
        ListingTask otherTask = saveGeneratingTask("task_asset_other");
        ImageVersion otherVersion = saveImageVersion("image_other_owner", otherTask.getTaskId());

        assertThatThrownBy(() -> service.listImageAssets(task.getTaskId(), otherVersion.getVersionId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsGeneratingImagesWhenTaskDoesNotExist() {
        assertThatThrownBy(() -> service.generateInitialImageVersion("task_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsGeneratingImagesWhenTaskIsNotGenerating() {
        ListingTask task = saveGeneratingTask("task_image_waiting");
        task.setStatus(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        task.setBriefStatus(BriefStatus.WAIT_APPROVE);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.generateInitialImageVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsGeneratingImagesWhenLatestBriefIsNotApproved() {
        ListingTask task = saveGeneratingTask("task_image_unapproved_brief");
        ListingBriefVersion brief = saveApprovedBrief("brief_image_unapproved", task.getTaskId());
        brief.setApproved(false);
        brief.setApprovedBy(null);
        brief.setApprovedAt(null);
        listingBriefVersionRepository.save(brief);

        assertThatThrownBy(() -> service.generateInitialImageVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsGeneratingImagesWhenBriefIsMissing() {
        ListingTask task = saveGeneratingTask("task_image_no_brief");

        assertThatThrownBy(() -> service.generateInitialImageVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void marksImageStatusAndVersionFailedWhenGeneratorFails() {
        ListingTask task = saveGeneratingTask("task_image_generator_failed");
        saveApprovedBrief("brief_image_generator_failed", task.getTaskId());
        ImageGenerationService failingService = new ImageGenerationService(
                listingTaskRepository,
                listingBriefVersionRepository,
                imageVersionRepository,
                imageAssetRepository,
                categoryTemplateService,
                (imageVersion, brief, template) -> {
                    throw new IllegalStateException("model unavailable");
                },
                new IdGenerator(),
                objectMapper);

        assertThatThrownBy(() -> failingService.generateInitialImageVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.FAILED);
        ImageVersion failedVersion = imageVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescVersionIdDesc(task.getTaskId())
                .orElseThrow();
        assertThat(failedVersion.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(imageAssetRepository.count()).isZero();
    }

    private ListingTask saveGeneratingTask(String taskId) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(ListingTaskStatus.GENERATING);
        task.setTextStatus(GenerationStatus.SUCCEEDED);
        task.setImageStatus(GenerationStatus.NOT_STARTED);
        task.setBriefStatus(BriefStatus.APPROVED);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[\"uploads/product-images/product.png\"]");
        task.setCompetitorAsinsJson("[]");
        return listingTaskRepository.save(task);
    }

    private ListingBriefVersion saveApprovedBrief(String briefVersionId, String taskId) {
        ListingBriefVersion brief = new ListingBriefVersion();
        brief.setBriefVersionId(briefVersionId);
        brief.setTaskId(taskId);
        brief.setTargetAudience("Amazon US car stereo buyers");
        brief.setCoreSellingPointsJson("[\"Wireless CarPlay\"]");
        brief.setTargetKeywordsJson("[\"car stereo\"]");
        brief.setForbiddenClaimsJson("[]");
        brief.setImageDirectionPromptsJson("[]");
        brief.setComplianceNotesJson("[]");
        brief.setApproved(true);
        brief.setCreatedBy("operator@example.com");
        brief.setApprovedBy("reviewer@example.com");
        brief.setApprovedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        return listingBriefVersionRepository.save(brief);
    }

    private ImageVersion saveImageVersion(String versionId, String taskId) {
        ImageVersion version = new ImageVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_existing");
        version.setInputProductUrlsJson("[\"uploads/product-images/product.png\"]");
        version.setImageProvider("PLACEHOLDER");
        version.setImageModel("placeholder-image-model");
        version.setGenerationParamsJson("{}");
        version.setStatus(GenerationStatus.SUCCEEDED);
        version.setSelected(false);
        return imageVersionRepository.save(version);
    }

    private ImageAsset baseAsset(
            String assetId,
            String imageVersionId,
            ImageAssetType type,
            int sortOrder) {
        ImageAsset asset = new ImageAsset();
        asset.setAssetId(assetId);
        asset.setImageVersionId(imageVersionId);
        asset.setType(type);
        asset.setPrompt("Generate " + type.name() + " image");
        asset.setRewrittenPrompt("Placeholder rewritten prompt for " + type.name());
        asset.setGeneratedImageUrl("generated-images/" + imageVersionId + "/" + type.name() + ".png");
        asset.setSizeProfile("STANDARD_LISTING");
        asset.setTargetWidth(2000);
        asset.setTargetHeight(2000);
        asset.setComplianceStatus("PASS");
        asset.setComplianceMethodsJson("[\"PLACEHOLDER_RULE_CHECK\"]");
        asset.setComplianceIssuesJson("[]");
        asset.setSortOrder(sortOrder);
        return asset;
    }
}
