package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.listing.api.TextVersionResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.TextVersion;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.TextVersionRepository;
import com.snails.ecommerce.template.application.CategoryTemplateService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/**
 * 文案生成应用服务测试。
 *
 * <p>使用真实 H2 仓储验证 Brief 批准后的文案版本追加、状态流转和错误边界。</p>
 */
@SpringBootTest
class TextGenerationServiceTest {

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private ListingBriefVersionRepository listingBriefVersionRepository;

    @Autowired
    private TextVersionRepository textVersionRepository;

    @Autowired
    private CategoryTemplateService categoryTemplateService;

    @Autowired
    private ObjectMapper objectMapper;

    private TextGenerationService service;

    @BeforeEach
    void setUp() {
        textVersionRepository.deleteAll();
        listingBriefVersionRepository.deleteAll();
        listingTaskRepository.deleteAll();
        service = new TextGenerationService(
                listingTaskRepository,
                listingBriefVersionRepository,
                textVersionRepository,
                categoryTemplateService,
                this::generatedText,
                new IdGenerator(),
                objectMapper);
    }

    @Test
    void generatesTextVersionFromApprovedBrief() {
        ListingTask task = saveGeneratingTask("task_generate");
        ListingBriefVersion brief = saveApprovedBrief("brief_generate", task.getTaskId());

        TextVersionResponse response = service.generateInitialTextVersion(task.getTaskId());

        assertThat(response.versionId()).startsWith("text_");
        assertThat(response.taskId()).isEqualTo(task.getTaskId());
        assertThat(response.briefVersionId()).isEqualTo(brief.getBriefVersionId());
        assertThat(response.parentVersionId()).isNull();
        assertThat(response.title()).contains("Wireless CarPlay");
        assertThat(response.bulletPoints()).containsExactly("Wireless CarPlay", "Android Auto");
        assertThat(response.targetKeywords()).containsExactly("car stereo");
        assertThat(response.qualityScore()).isEqualTo(88);
        assertThat(response.selected()).isFalse();

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getStatus()).isEqualTo(ListingTaskStatus.GENERATING);
        assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.SUCCEEDED);
        assertThat(savedTask.getImageStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
        assertThat(textVersionRepository.count()).isEqualTo(1);
    }

    @Test
    void createsNewVersionWithParentWhenTextAlreadyExists() {
        ListingTask task = saveGeneratingTask("task_parent");
        saveApprovedBrief("brief_parent", task.getTaskId());
        TextVersion existing = saveTextVersion("text_001", task.getTaskId());
        existing.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        textVersionRepository.save(existing);

        TextVersionResponse response = service.generateInitialTextVersion(task.getTaskId());

        assertThat(response.parentVersionId()).isEqualTo("text_001");
        assertThat(textVersionRepository.count()).isEqualTo(2);
    }

    @Test
    void listsTextVersionsNewestFirst() {
        ListingTask task = saveGeneratingTask("task_list_text");
        TextVersion first = saveTextVersion("text_001", task.getTaskId());
        first.setCreatedAt(LocalDateTime.of(2026, 6, 6, 10, 0));
        textVersionRepository.save(first);
        TextVersion second = saveTextVersion("text_002", task.getTaskId());
        second.setCreatedAt(LocalDateTime.of(2026, 6, 6, 11, 0));
        textVersionRepository.save(second);

        List<TextVersionResponse> versions = service.listTextVersions(task.getTaskId());

        assertThat(versions)
                .extracting(TextVersionResponse::versionId)
                .containsExactly("text_002", "text_001");
    }

    @Test
    void rejectsGeneratingTextWhenTaskDoesNotExist() {
        assertThatThrownBy(() -> service.generateInitialTextVersion("task_missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_NOT_FOUND);
    }

    @Test
    void rejectsGeneratingTextWhenTaskIsNotGenerating() {
        ListingTask task = saveGeneratingTask("task_waiting");
        task.setStatus(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        task.setBriefStatus(BriefStatus.WAIT_APPROVE);
        listingTaskRepository.save(task);

        assertThatThrownBy(() -> service.generateInitialTextVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsGeneratingTextWhenLatestBriefIsNotApproved() {
        ListingTask task = saveGeneratingTask("task_unapproved_brief");
        ListingBriefVersion brief = saveApprovedBrief("brief_unapproved", task.getTaskId());
        brief.setApproved(false);
        brief.setApprovedBy(null);
        brief.setApprovedAt(null);
        listingBriefVersionRepository.save(brief);

        assertThatThrownBy(() -> service.generateInitialTextVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TASK_STATUS_INVALID);
    }

    @Test
    void rejectsGeneratingTextWhenBriefIsMissing() {
        ListingTask task = saveGeneratingTask("task_no_brief");

        assertThatThrownBy(() -> service.generateInitialTextVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void marksTextStatusFailedWhenGeneratorFails() {
        ListingTask task = saveGeneratingTask("task_generator_failed");
        saveApprovedBrief("brief_generator_failed", task.getTaskId());
        TextGenerationService failingService = new TextGenerationService(
                listingTaskRepository,
                listingBriefVersionRepository,
                textVersionRepository,
                categoryTemplateService,
                (brief, template, iterationPrompt) -> {
                    throw new IllegalStateException("model unavailable");
                },
                new IdGenerator(),
                objectMapper);

        assertThatThrownBy(() -> failingService.generateInitialTextVersion(task.getTaskId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);

        ListingTask savedTask = listingTaskRepository.findById(task.getTaskId()).orElseThrow();
        assertThat(savedTask.getTextStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(textVersionRepository.count()).isZero();
    }

    private TextVersion generatedText(
            ListingBriefVersion brief,
            com.snails.ecommerce.template.domain.CategoryTemplate template,
            String iterationPrompt) {
        TextVersion version = new TextVersion();
        version.setIterationPrompt(iterationPrompt);
        version.setTitle("Wireless CarPlay Stereo for Amazon US");
        version.setBulletPointsJson("[\"Wireless CarPlay\",\"Android Auto\"]");
        version.setDescription("A review-ready car stereo listing.");
        version.setBackendSearchTerms("wireless carplay stereo");
        version.setTargetKeywordsJson("[\"car stereo\"]");
        version.setComplianceWarningsJson("[]");
        version.setQualityScore(88);
        return version;
    }

    private ListingTask saveGeneratingTask(String taskId) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(ListingTaskStatus.GENERATING);
        task.setTextStatus(GenerationStatus.NOT_STARTED);
        task.setImageStatus(GenerationStatus.NOT_STARTED);
        task.setBriefStatus(BriefStatus.APPROVED);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[]");
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

    private TextVersion saveTextVersion(String versionId, String taskId) {
        TextVersion version = new TextVersion();
        version.setVersionId(versionId);
        version.setTaskId(taskId);
        version.setBriefVersionId("brief_existing");
        version.setTitle("Existing text version");
        version.setBulletPointsJson("[\"Existing bullet\"]");
        version.setDescription("Existing description.");
        version.setBackendSearchTerms("existing terms");
        version.setTargetKeywordsJson("[\"existing\"]");
        version.setComplianceWarningsJson("[]");
        version.setSelected(false);
        return textVersionRepository.save(version);
    }
}
