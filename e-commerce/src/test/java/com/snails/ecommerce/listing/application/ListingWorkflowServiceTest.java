package com.snails.ecommerce.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.snails.ecommerce.common.api.PagedResponse;
import com.snails.ecommerce.common.error.BusinessException;
import com.snails.ecommerce.common.error.ErrorCode;
import com.snails.ecommerce.common.id.IdGenerator;
import com.snails.ecommerce.common.storage.LocalFileStorage;
import com.snails.ecommerce.listing.api.ListingTaskDetailResponse;
import com.snails.ecommerce.listing.api.ListingTaskSummaryResponse;
import com.snails.ecommerce.listing.domain.BriefStatus;
import com.snails.ecommerce.listing.domain.GenerationStatus;
import com.snails.ecommerce.listing.domain.ListingBriefVersion;
import com.snails.ecommerce.listing.domain.ListingTask;
import com.snails.ecommerce.listing.domain.ListingTaskStatus;
import com.snails.ecommerce.listing.domain.ProductRawData;
import com.snails.ecommerce.listing.infrastructure.ListingBriefVersionRepository;
import com.snails.ecommerce.listing.infrastructure.ListingTaskRepository;
import com.snails.ecommerce.listing.infrastructure.ProductRawDataRepository;
import com.snails.ecommerce.template.application.CategoryTemplateService;
import com.snails.ecommerce.template.domain.CategoryTemplate;
import com.snails.ecommerce.template.infrastructure.CategoryTemplateRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Listing 工作流应用服务测试。
 *
 * <p>该测试定义第一阶段任务提交最小闭环：保存上传文件、读取 Car Stereo 模板、保存产品资料、
 * 创建占位 Brief，并让任务进入 Brief 待审核状态。</p>
 */
@SpringBootTest
class ListingWorkflowServiceTest {

    @TempDir
    Path tempDir;

    @Autowired
    private ListingTaskRepository listingTaskRepository;

    @Autowired
    private ProductRawDataRepository productRawDataRepository;

    @Autowired
    private ListingBriefVersionRepository listingBriefVersionRepository;

    @Autowired
    private CategoryTemplateRepository categoryTemplateRepository;

    private ListingWorkflowService service;

    @BeforeEach
    void setUp() {
        ProductDocumentExtractor extractor = content -> {
            ProductRawData rawData = new ProductRawData();
            rawData.setProductName("AI Extracted Car Stereo");
            rawData.setBrandName("Snails");
            rawData.setCoreFunctionsJson("[\"Wireless CarPlay\"]");
            return rawData;
        };
        MarkdownDocumentParser markdownParser = new MarkdownDocumentParser(extractor);
        CategoryTemplateService templateService = new CategoryTemplateService(categoryTemplateRepository);
        service = new ListingWorkflowService(
                listingTaskRepository,
                productRawDataRepository,
                listingBriefVersionRepository,
                templateService,
                new ProductDocumentParserFactory(List.of(new UnsupportedProductDocumentParser(), markdownParser)),
                new LocalFileStorage(tempDir.toString()),
                new IdGenerator());
    }

    @Test
    void submitsTaskAndCreatesWaitBriefApproveRecords() {
        MockMultipartFile markdownFile = new MockMultipartFile(
                "file",
                "product.md",
                "text/markdown",
                "# Product".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile productImage = new MockMultipartFile(
                "productImages",
                "product.png",
                "image/png",
                "image-bytes".getBytes(StandardCharsets.UTF_8));

        String taskId = service.submitTask(
                markdownFile,
                List.of(productImage),
                List.of("B000TEST"),
                "US",
                "en-US");

        ListingTask task = listingTaskRepository.findById(taskId).orElseThrow();
        ProductRawData rawData = productRawDataRepository.findByTaskId(taskId).orElseThrow();
        ListingBriefVersion brief = listingBriefVersionRepository
                .findTopByTaskIdOrderByCreatedAtDescBriefVersionIdDesc(taskId)
                .orElseThrow();
        CategoryTemplate template = categoryTemplateRepository.findById(task.getCategoryTemplateId()).orElseThrow();

        assertThat(task.getStatus()).isEqualTo(ListingTaskStatus.WAIT_BRIEF_APPROVE);
        assertThat(task.getBriefStatus()).isEqualTo(BriefStatus.WAIT_APPROVE);
        assertThat(task.getTextStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
        assertThat(task.getImageStatus()).isEqualTo(GenerationStatus.NOT_STARTED);
        assertThat(task.getCategoryCode()).isEqualTo("CAR_STEREO");
        assertThat(task.getMarketplace()).isEqualTo("US");
        assertThat(task.getLanguage()).isEqualTo("en-US");
        assertThat(task.getOriginalProductUrlsJson()).contains("product.png");
        assertThat(task.getCompetitorAsinsJson()).contains("B000TEST");
        assertThat(template.getCategoryCode()).isEqualTo("CAR_STEREO");
        assertThat(rawData.getProductName()).isEqualTo("AI Extracted Car Stereo");
        assertThat(rawData.getTaskId()).isEqualTo(taskId);
        assertThat(brief.getTaskId()).isEqualTo(taskId);
        assertThat(brief.getTargetAudience()).isEqualTo("Amazon US car stereo buyers");
        assertThat(brief.getCoreSellingPointsJson()).contains("Wireless CarPlay");
        assertThat(brief.isApproved()).isFalse();
        assertThat(brief.getCreatedBy()).isEqualTo("SYSTEM");
    }

    @Test
    void rejectsTaskWithoutProductImages() {
        MockMultipartFile markdownFile = new MockMultipartFile(
                "file",
                "product.md",
                "text/markdown",
                "# Product".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.submitTask(markdownFile, List.of(), List.of(), "US", "en-US"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_INVALID);
    }

    @Test
    void rejectsNonMarkdownDocument() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "product.txt",
                "text/plain",
                "Product".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile productImage = new MockMultipartFile(
                "productImages",
                "product.png",
                "image/png",
                "image-bytes".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.submitTask(textFile, List.of(productImage), List.of(), "US", "en-US"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_INVALID);
    }

    @Test
    void getsTaskDetailWithFinalSelectionIds() {
        ListingTask task = new ListingTask();
        task.setTaskId("task_detail");
        task.setStatus(ListingTaskStatus.COMPLETED);
        task.setTextStatus(GenerationStatus.SUCCEEDED);
        task.setImageStatus(GenerationStatus.SUCCEEDED);
        task.setBriefStatus(BriefStatus.APPROVED);
        task.setCategoryCode("CAR_STEREO");
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace("US");
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[\"uploads/product-images/product.png\"]");
        task.setCompetitorAsinsJson("[\"B000TEST\"]");
        task.setSelectedTextVersionId("text_001");
        task.setSelectedImageVersionId("image_001");
        listingTaskRepository.save(task);

        ListingBriefVersion brief = new ListingBriefVersion();
        brief.setBriefVersionId("brief_detail");
        brief.setTaskId("task_detail");
        brief.setTargetAudience("Amazon US car stereo buyers");
        brief.setCoreSellingPointsJson("[]");
        brief.setTargetKeywordsJson("[]");
        brief.setForbiddenClaimsJson("[]");
        brief.setImageDirectionPromptsJson("[]");
        brief.setComplianceNotesJson("[]");
        brief.setApproved(true);
        brief.setCreatedBy("operator@example.com");
        brief.setApprovedBy("reviewer@example.com");
        listingBriefVersionRepository.save(brief);

        ListingTaskDetailResponse response = service.getTaskDetail("task_detail");

        assertThat(response.selectedTextVersionId()).isEqualTo("text_001");
        assertThat(response.selectedImageVersionId()).isEqualTo("image_001");
        assertThat(response.latestBrief().briefVersionId()).isEqualTo("brief_detail");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void listsTasksWithFiltersAndPagination() {
        saveTask("task_001", ListingTaskStatus.COMPLETED, "US", "CAR_STEREO");
        saveTask("task_002", ListingTaskStatus.WAIT_BRIEF_APPROVE, "US", "CAR_STEREO");
        saveTask("task_003", ListingTaskStatus.COMPLETED, "UK", "CAR_STEREO");

        PagedResponse<ListingTaskSummaryResponse> response = service.listTasksPage(
                "COMPLETED",
                "US",
                "CAR_STEREO",
                0,
                10);

        assertThat(response.totalItems()).isEqualTo(1);
        assertThat(response.items().get(0).taskId()).isEqualTo("task_001");
        assertThat(response.items().get(0).status()).isEqualTo("COMPLETED");
        assertThat(response.items().get(0).marketplace()).isEqualTo("US");
    }

    @Test
    void rejectsInvalidTaskListPageRequest() {
        assertThatThrownBy(() -> service.listTasksPage(null, null, null, -1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.listTasksPage(null, null, null, 0, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    private ListingTask saveTask(
            String taskId,
            ListingTaskStatus status,
            String marketplace,
            String categoryCode) {
        ListingTask task = new ListingTask();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setTextStatus(status == ListingTaskStatus.COMPLETED
                ? GenerationStatus.SUCCEEDED
                : GenerationStatus.NOT_STARTED);
        task.setImageStatus(status == ListingTaskStatus.COMPLETED
                ? GenerationStatus.SUCCEEDED
                : GenerationStatus.NOT_STARTED);
        task.setBriefStatus(status == ListingTaskStatus.COMPLETED
                ? BriefStatus.APPROVED
                : BriefStatus.WAIT_APPROVE);
        task.setCategoryCode(categoryCode);
        task.setCategoryTemplateId("tpl_car_stereo_us_en");
        task.setMarketplace(marketplace);
        task.setLanguage("en-US");
        task.setOriginalProductUrlsJson("[]");
        task.setCompetitorAsinsJson("[]");
        if (status == ListingTaskStatus.COMPLETED) {
            task.setSelectedTextVersionId("text_" + taskId);
            task.setSelectedImageVersionId("image_" + taskId);
        }
        return listingTaskRepository.save(task);
    }

    /**
     * 测试上下文中的产品资料提取器替身。
     *
     * <p>真实实现后续会由大模型适配器提供；当前测试只需要让 Spring 上下文可以启动。</p>
     */
    @TestConfiguration
    static class TestExtractorConfiguration {

        @Bean
        @Primary
        ProductDocumentExtractor productDocumentExtractor() {
            ProductRawData rawData = new ProductRawData();
            rawData.setProductName("Spring Context Extracted Product");
            rawData.setCoreFunctionsJson("[\"Spring context selling point\"]");
            return content -> rawData;
        }
    }

    /**
     * 测试替身：用于证明工作流会按扩展名选择解析器，而不是默认使用列表中的第一个解析器。
     */
    private static class UnsupportedProductDocumentParser implements ProductDocumentParser {

        @Override
        public boolean supports(String fileExtension) {
            return false;
        }

        @Override
        public ProductRawData parse(java.io.InputStream inputStream) {
            throw new AssertionError("Unsupported parser should not be selected");
        }
    }
}
