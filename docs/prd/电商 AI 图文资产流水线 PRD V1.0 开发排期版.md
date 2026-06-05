# 电商 AI 图文资产流水线 PRD V1.0 开发排期版

## 0. 产品范围与目标

### 0.1 第一版范围

本系统第一版面向 **Amazon US 站点英文 Listing**，服务对象为跨境电商运营团队，核心目标是把“产品资料 + 产品原型图 + 竞品 ASIN 数据”转化为可审核、可迭代、可归档的 Amazon 图文资产包。底层按多类目模板机制设计，首批只交付并验收 `Car Stereo` 类目模板。

第一版覆盖：

- Listing 英文文案生成：Title、Bullet Points、Description、Backend Search Terms、关键词与合规提示。
- 产品图片资产制作：基于类目模板生成 7 图完整组 + 默认 1 张 A+ 模块图；每种图片类型默认只生成 1 张候选，避免无效消耗，运营可按需追加迭代。
- Brief 人工审核：运营可修改卖点、关键词、受众、合规限制和图片方向。
- 图文双轨版本迭代：文案和图片可以分别重生成、分别选版，最终一起归档并导出交付包。
- 竞品输入：Bright Data Amazon Scraper API 优先，手工补录兜底，外部数据失败不阻塞主流程。

### 0.2 非目标

第一版不直接负责 Amazon Seller Central 自动上架，不做价格、库存、广告投放和订单管理。系统输出的是可供运营审核和人工上架使用的图文资产包。

---

## 1. 角色与核心使用场景

### 1.1 角色

- 运营：提交产品资料、审核 Brief、选择最终图文版本。
- 设计/视觉审核人：检查图片是否符合主图、信息图、A+ 图要求。
- 管理员：配置模型、Bright Data API、字段映射、类目模板、类目规则、通知对象和失败重试策略。

### 1.2 主流程

1. 运营上传产品 Markdown 文档和 1-4 张原始产品图。
2. 运营填写或导入竞品 ASIN；系统通过 Bright Data 抓取竞品产品和评论信息，失败时允许手工补录。
3. 系统解析产品资料、竞品数据，生成 ListingBrief。
4. 运营修改并确认 Brief。
5. 系统并行生成 Listing 文案版本和图片资产包版本。
6. 运营分别迭代文案或图片。
7. 运营选择最终 TextVersion 和 ImageVersion，系统归档完整资产包。
8. 运营导出交付包：ZIP 必选，Excel/Markdown/Word 文档可选。

---

## 2. 全局数据模型与版本树设计

为了支持多图输入、7 图组、A+ 图和图文双轨版本树，数据库采用一主多从结构。

### 2.1 核心实体关系

- 一个 `CategoryTemplate` 定义类目级字段、Prompt、图片资产类型、尺寸策略和合规规则。
- 一个 `ListingTask` 关联一个 `CategoryTemplate`，首批默认使用 `Car Stereo` 模板。
- 一个 `ListingTask` 关联多个 `CompetitorSnapshot`。
- 一个 `CompetitorDataProviderConfig` 定义 Bright Data 的数据源配置、字段映射、额度和超时策略。
- 一个 `ListingTask` 包含多个 `ListingBriefVersion`，同一时间只有一个 Brief 被批准用于生成。
- 一个 `ListingTask` 拥有独立的 `TextVersion` 和 `ImageVersion` 版本树。
- 一个 `ImageVersion` 下挂多个 `ImageAsset`，用于表达主图、副图和 A+ 图模块。
- 一个最终归档结果必须同时选择一个 `TextVersion` 和一个 `ImageVersion`。
- 一个已归档任务可以生成多个 `ExportPackage`，ZIP 为默认交付包，Excel/Markdown/Word 为可选格式。

### 2.2 物理模型 Java POJO 表达

```java
public class ProductRawData {
    private String productName;
    private String brandName;
    private String categoryCode;                // 首批默认 CAR_STEREO，可配置
    private String categoryTemplateId;
    private String marketplace;                 // US
    private String language;                    // en-US
    private Map<String, String> specifications;
    private List<String> coreFunctions;
    private List<String> packageItems;
    private List<String> compatibilityInfo;
    private List<String> forbiddenClaims;       // 如 medical claim、夸大承诺、未经验证的认证等
}

public class ListingTask {
    private String taskId;
    private ListingTaskStatus status;
    private GenerationStatus textStatus;
    private GenerationStatus imageStatus;
    private BriefStatus briefStatus;
    private String categoryCode;                // 首批默认 CAR_STEREO
    private String categoryTemplateId;
    private String marketplace;                 // US
    private String language;                    // en-US
    private List<String> originalProductUrls;   // 1-4 张
    private List<String> competitorAsins;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

public enum ListingTaskStatus {
    PENDING,
    EXTRACTING_DATA,
    WAIT_BRIEF_APPROVE,
    GENERATING,
    WAIT_FINAL_APPROVE,
    COMPLETED,
    FAILED,
    CANCELLED
}

public enum GenerationStatus {
    NOT_STARTED,
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILED,
    FAILED,
    RETRYING
}

public enum BriefStatus {
    DRAFT,
    WAIT_APPROVE,
    APPROVED,
    REJECTED
}
public class CategoryTemplate {
    private String templateId;
    private String categoryCode;                // 首批交付 CAR_STEREO
    private String categoryName;
    private String marketplace;                 // US
    private String language;                    // en-US
    private List<String> requiredProductFields;
    private List<String> optionalProductFields;
    private List<ImageAssetType> defaultImageAssetTypes;
    private Map<String, String> sizeProfiles;   // MAIN_IMAGE、STANDARD_LISTING、A_PLUS_STANDARD 等
    private List<String> promptFragments;
    private List<String> complianceRules;
    private boolean enabled;
}
public class CompetitorDataProviderConfig {
    private String providerConfigId;
    private String providerName;                // BRIGHT_DATA
    private String datasetType;                 // AMAZON_PRODUCT, AMAZON_REVIEWS, AMAZON_SEARCH
    private String marketplace;                 // US
    private String endpointName;                // 逻辑端点名，不直接暴露密钥或完整 URL
    private Map<String, String> fieldMapping;   // Bright Data 字段 -> CompetitorSnapshot 字段
    private Integer timeoutSeconds;             // 默认 30s，可配置
    private Integer maxAsinsPerTask;
    private Integer maxReviewsPerAsin;
    private Integer monthlyCreditBudget;
    private boolean enabled;
}

public class CompetitorSnapshot {
    private String snapshotId;
    private String taskId;
    private String asin;
    private String marketplace;                 // US
    private String productUrl;
    private String sourceType;                  // BRIGHT_DATA, MANUAL
    private String sourceName;                  // Bright Data Amazon Scraper API 或手工来源说明
    private Integer confidenceScore;            // 0-100
    private String title;
    private String brand;
    private String category;
    private String rating;
    private Integer reviewCount;
    private List<String> imageUrls;
    private List<String> bulletPoints;
    private String description;
    private List<String> reviewPainPoints;
    private List<String> keywordSignals;
    private String rawPayloadUrl;
    private String rawPayloadHash;
    private String providerJobId;
    private String fieldMappingVersion;
    private Integer requestCostCredits;
    private LocalDateTime capturedAt;
}

public class ListingBriefVersion {
    private String briefVersionId;
    private String taskId;
    private String parentBriefVersionId;
    private String targetAudience;
    private List<String> coreSellingPoints;
    private List<String> targetKeywords;
    private List<String> forbiddenClaims;
    private List<String> imageDirectionPrompts;
    private List<String> complianceNotes;
    private boolean approved;
    private LocalDateTime createdAt;
}

public class TextVersion {
    private String versionId;                   // txt_v1, txt_v2...
    private String taskId;
    private String parentVersionId;
    private String briefVersionId;
    private String iterationPrompt;
    private String title;
    private List<String> bulletPoints;          // 5 点描述
    private String description;
    private String backendSearchTerms;
    private List<String> targetKeywords;
    private List<String> complianceWarnings;
    private Integer qualityScore;
    private boolean selected;
    private LocalDateTime createdAt;
}

public class ImageVersion {
    private String versionId;                   // img_v1, img_v2...
    private String taskId;
    private String parentVersionId;
    private String briefVersionId;
    private String iterationPrompt;
    private String referenceImageUrl;
    private List<String> inputProductUrls;
    private String imageProvider;               // OPENAI, ALIBABA, INTERNAL 等
    private String imageModel;                  // 配置项，不在 PRD 中写死具体不可用模型
    private Map<String, Object> generationParams;
    private GenerationStatus status;
    private Integer qualityScore;
    private boolean selected;
    private LocalDateTime createdAt;
}

public class ImageAsset {
    private String assetId;
    private String imageVersionId;
    private ImageAssetType type;
    private String prompt;
    private String generatedImageUrl;
    private String sourceEditableFileUrl;
    private String sizeProfile;                 // MAIN_IMAGE, STANDARD_LISTING, A_PLUS_STANDARD 等，可配置
    private Integer targetWidth;
    private Integer targetHeight;
    private String complianceStatus;            // PASS, WARNING, FAIL
    private List<String> complianceMethods;     // RULE_BASED, VISION_MODEL, MANUAL_REVIEW
    private List<String> complianceIssues;
    private String complianceReviewedBy;        // 人工确认人，可为空
    private LocalDateTime complianceReviewedAt;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}

public enum ImageAssetType {
    MAIN_IMAGE,             // Amazon 主图
    INFOGRAPHIC,            // 卖点图
    LIFESTYLE,              // 场景图
    DIMENSION,              // 尺寸图
    COMPATIBILITY,          // 兼容/适配图
    INSTALLATION,           // 安装图
    PACKAGE_CONTENTS,       // 包装清单图
    A_PLUS_MODULE           // A+ 图模块，第一版默认 1 张，可按需追加
}
public class ExportPackage {
    private String exportPackageId;
    private String taskId;
    private String selectedTextVersionId;
    private String selectedImageVersionId;
    private ExportFormat format;                // ZIP, EXCEL, MARKDOWN, WORD
    private String fileUrl;
    private String manifestUrl;
    private String status;                      // PENDING, RUNNING, SUCCEEDED, FAILED
    private List<String> includedAssetIds;
    private LocalDateTime createdAt;
}

public enum ExportFormat {
    ZIP,
    EXCEL,
    MARKDOWN,
    WORD
}
```

---

## 3. 竞品数据源与 Bright Data 接入

第一版竞品数据供应商确定为 **Bright Data**，使用 Bright Data Amazon Scraper API 作为首选数据源。系统仍保留 `CompetitorDataProvider` 抽象层，避免业务流程直接依赖 Bright Data 的具体字段或接口形态。

### 3.1 数据采集范围

首批采集范围包括：

- Amazon US 产品基础信息：ASIN、标题、品牌、类目、图片、价格、评分、评论数、卖点、描述、可用性等。
- Amazon US 评论信息：评论文本、评分、评论时间、是否验证购买等，用于提炼差评痛点和用户关注点。
- 可选搜索结果信息：关键词搜索结果、类目排名或相邻竞品列表，作为后续扩展，不阻塞第一版。

### 3.2 字段映射策略

Bright Data 返回字段不得直接在 Brief Prompt 中使用。系统必须先通过 `fieldMapping` 转换为内部 `CompetitorSnapshot` 字段，并保存原始响应位置：

- 标准化字段用于生成 Brief，例如 `title`、`bulletPoints`、`reviewPainPoints`、`keywordSignals`。
- 原始响应保存为 `rawPayloadUrl`，并记录 `rawPayloadHash`，便于追溯和排查字段变化。
- 每次映射必须记录 `fieldMappingVersion`，字段映射升级后不影响历史快照。
- 竞品数据只能用于洞察提炼，不得复制竞品标题、Bullet 或图片文案。

### 3.3 额度、费用与降级

Bright Data 调用额度和费用预算通过 `CompetitorDataProviderConfig` 配置，不写死在业务代码中。

- 每个任务限制最大 ASIN 数量和每个 ASIN 最大评论数。
- API 超时、限流、字段缺失、额度不足或返回异常时，主流程不得失败。
- 失败时记录 provider、ASIN、错误类型、重试次数和原始响应摘要。
- 降级顺序：重试 Bright Data -> 使用已缓存快照 -> 运营手工补录 -> 无竞品数据继续生成 Brief。

---

## 4. 类目模板机制

第一版底层按多类目模板设计，但首批只交付 `Car Stereo` 模板。系统不得把 Car Stereo 的字段、Prompt 和图片规则写死在业务代码里，必须通过 `CategoryTemplate` 读取。

`CategoryTemplate` 至少配置：

- 类目基础信息：`categoryCode`、`categoryName`、`marketplace`、`language`。
- 产品资料字段：必填字段、选填字段、兼容信息、包装清单、禁用声明。
- Brief Prompt 片段：类目专家角色、卖点提炼规则、关键词规则、禁用表达。
- 图片资产配置：默认图片类型、每种类型默认生成数量、尺寸配置和 A+ 尺寸策略。
- 合规规则：主图规则、副图规则、A+ 图规则、禁用词和人工审核要求。

首批 `Car Stereo` 模板必须覆盖：

- 车机尺寸、屏幕规格、系统版本、CarPlay/Android Auto、蓝牙、倒车影像、CANBUS、适配车型/年份、接口和包装清单。
- 标准 7 图组和默认 1 张 A+ 模块图。
- Amazon US 英文 Listing 文案规则。

---

## 5. 图片资产包定义

### 5.1 标准 7 图组

每个 `ImageVersion` 默认生成一组图片资产。为控制成本和审核负担，每种 `ImageAssetType` 默认只生成 1 张候选图；运营不满意时通过迭代追加新版本，而不是一次性生成多张候选。

1. `MAIN_IMAGE`：白底主图，只展示实际售卖产品，不加文字、水印、装饰元素。
2. `INFOGRAPHIC`：核心卖点图，突出 1-2 个主要功能。
3. `LIFESTYLE`：真实使用场景图，例如车内中控安装后的使用场景。
4. `DIMENSION`：尺寸/接口/屏幕规格图。
5. `COMPATIBILITY`：车型、年份、系统或配件兼容说明图。
6. `INSTALLATION`：安装步骤或安装前后对比图。
7. `PACKAGE_CONTENTS`：包装清单图，展示主机、线束、配件、说明书等。

### 5.2 A+ 图

第一版默认生成 1 张 A+ 图模块，避免在未确认方向前产生过多图片成本。A+ 图不直接发布到 Amazon，作为 `A_PLUS_MODULE` 类型的 `ImageAsset` 存储。后续如运营需要更多 A+ 模块，可通过图片迭代追加新的 `ImageVersion`。

A+ 图默认优先生成品牌/产品价值横幅图，并匹配 Amazon A+ 常用模块尺寸。具体尺寸通过 `sizeProfile`、`targetWidth`、`targetHeight` 配置，不在业务代码中写死。

### 5.3 图片合规检查

系统必须在图片生成后执行基础合规检查，结果写入 `ImageAsset.complianceStatus`、`complianceMethods` 和 `complianceIssues`。第一版采用 **规则检测 + 视觉模型检测结合** 的方案：规则检测作为硬门槛，视觉模型检测负责语义和画面风险判断，最终仍由运营人工确认。

规则检测至少包括：

- 文件格式、文件大小、图片尺寸、长宽比和分辨率是否满足对应 `sizeProfile`。
- `MAIN_IMAGE`、`STANDARD_LISTING`、`A_PLUS_STANDARD` 等尺寸配置是否存在且可追踪。
- 每个 `ImageAsset` 是否具备类型、prompt、生成 URL、尺寸配置和版本归属。
- 文案叠字是否包含明确禁用词或未经验证的绝对化表述，如 best、No.1、guaranteed 等。

视觉模型检测至少包括：

- 主图背景是否为纯白或接近纯白。
- 主图是否包含文字、水印、边框、促销标识、非售卖道具。
- 产品主体占画面比例是否接近 Amazon 主图要求。
- 副图和 A+ 图是否存在竞品商标、平台 Logo 或不应出现的第三方素材。
- 图片内容是否与产品资料冲突，例如参数、车型、年份、配件、安装方式不一致。

合规结果规则：

- `PASS`：规则检测通过，视觉模型未发现明显风险，可进入终审。
- `WARNING`：存在可人工判断的风险，例如轻微构图问题、卖点表达偏强、参数需复核；允许进入终审，但必须展示问题说明。
- `FAIL`：违反硬性规则或出现明显上架风险，例如主图含文字/水印、尺寸不合格、明显虚假参数；默认禁止归档，除非管理员强制豁免并记录原因。

---

## 6. 归档与交付包导出

归档完成后，系统必须支持导出运营交付包。第一版导出策略为 **ZIP 必选，Excel / Markdown / Word 文档可选**。

### 6.1 ZIP 默认交付包

ZIP 是第一版默认交付物，必须包含：

- 最终选中的 7 图组和 A+ 图图片文件。
- `listing.md`：最终英文 Listing 文案，包括 Title、Bullet Points、Description、Backend Search Terms、Target Keywords 和合规警告。
- `manifest.json`：任务 ID、类目模板、文案版本、图片版本、图片资产清单、尺寸配置、Bright Data 字段映射版本、合规检查结果和导出时间。
- `compliance_report.md`：图片和文案合规风险说明，包含 PASS/WARNING/FAIL 结果和人工确认信息。

### 6.2 可选文档导出

运营可在归档后选择额外导出以下格式：

- `Excel`：适合批量复制到运营表格，包含 Listing 文案、关键词、图片文件名、图片类型、合规结果和版本信息。
- `Markdown`：适合进入知识库、代码仓库或轻量审阅流程。
- `Word`：适合对外交付、线下审阅或管理层确认。

可选导出失败不得影响已归档任务和 ZIP 默认交付包。导出失败时记录失败原因，并允许单独重试对应格式。

### 6.3 导出规则

- 只有 `COMPLETED` 任务允许导出交付包。
- ZIP 导出必须可重复生成，同一任务允许保留多次导出记录。
- 导出文件必须引用最终选中的 `TextVersion` 和 `ImageVersion`，不得混入未选中版本。
- `WARNING` 资产可以导出，但必须在 manifest 和合规报告中标记；`FAIL` 资产默认不可导出，除非已有管理员豁免记录。

---

## 7. 核心业务工作流与状态机规则

```text
用户提交任务
  -> EXTRACTING_DATA
      -> 解析 Markdown 产品资料
      -> Bright Data 抓取 ASIN 竞品产品和评论数据
      -> Bright Data 失败时进入缓存、手工补录或无竞品数据降级模式
  -> LLM 生成 ListingBriefVersion
  -> WAIT_BRIEF_APPROVE
      -> 飞书 Bot 异步通知运营
      -> 运营修改并批准 Brief
  -> GENERATING
      -> 文案流生成 TextVersion V1
      -> 图片流生成 ImageVersion V1 + ImageAsset 资产组，每种图片类型默认 1 张
  -> WAIT_FINAL_APPROVE
      -> 运营可分别迭代 TextVersion 或 ImageVersion
      -> 运营选择最终文案版本和图片版本
  -> COMPLETED
      -> 导出 ZIP 默认交付包，可选导出 Excel / Markdown / Word
```

### 7.1 状态转移规则

- `EXTRACTING_DATA` 阶段，产品文档解析失败时任务进入 `FAILED`。
- Bright Data 失败不得导致主任务失败；系统记录 Warning，并按“重试 -> 已缓存快照 -> 手工补录 -> 无竞品数据继续”的顺序降级。
- `WAIT_BRIEF_APPROVE` 可以无限期挂起，但必须记录最近通知时间和审批人。
- `GENERATING` 阶段文案流和图片流分别记录 `textStatus`、`imageStatus`。
- 任一流生成失败时，主任务不得直接归档，进入 `WAIT_FINAL_APPROVE` 前必须至少有一个可用文案版本和一个可用图片版本。
- `WAIT_FINAL_APPROVE` 中可分别迭代文案或图片，迭代完成后仍保持该状态。
- COMPLETED 后默认不可再改；如需再改，创建新任务或从最终版本复制出新任务。
- COMPLETED 后允许多次导出交付包，导出失败不得回退主任务状态。

---

## 8. Spring Boot + Spring AI Alibaba 核心工程骨架

### 8.1 抽象输入解析器

```java
public interface ProductDocumentParser {
    boolean supports(String fileExtension);
    ProductRawData parse(InputStream inputStream) throws Exception;
}

@Component
public class MarkdownDocumentParser implements ProductDocumentParser {
    @Override
    public boolean supports(String fileExtension) {
        return "md".equalsIgnoreCase(fileExtension);
    }

    @Override
    public ProductRawData parse(InputStream inputStream) throws Exception {
        // 解析产品名、品牌、参数、功能、包装清单、兼容信息和禁用声明
        return new ProductRawData();
    }
}
```

### 8.2 流程编排 Controller

```java
@RestController
@RequestMapping("/api/v1/listing")
@RequiredArgsConstructor
public class ListingTaskController {

    private final ListingWorkflowService workflowService;


    @GetMapping("/{taskId}")
    public ResponseEntity<ListingTaskDetail> getTask(@PathVariable String taskId) {
        return ResponseEntity.ok(workflowService.getTaskDetail(taskId));
    }

    @GetMapping("/{taskId}/versions/text")
    public ResponseEntity<List<TextVersion>> listTextVersions(@PathVariable String taskId) {
        return ResponseEntity.ok(workflowService.listTextVersions(taskId));
    }

    @GetMapping("/{taskId}/versions/image")
    public ResponseEntity<List<ImageVersion>> listImageVersions(@PathVariable String taskId) {
        return ResponseEntity.ok(workflowService.listImageVersions(taskId));
    }

    @GetMapping("/{taskId}/assets")
    public ResponseEntity<List<ImageAsset>> listImageAssets(@PathVariable String taskId) {
        return ResponseEntity.ok(workflowService.listImageAssets(taskId));
    }

    @GetMapping("/export/{exportPackageId}")
    public ResponseEntity<ExportPackage> getExportPackage(@PathVariable String exportPackageId) {
        return ResponseEntity.ok(workflowService.getExportPackage(exportPackageId));
    }

    @PostMapping("/submit")
    public ResponseEntity<String> submitTask(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productImages") List<MultipartFile> images,
            @RequestParam(value = "asins", required = false) List<String> asins) {
        String taskId = workflowService.initiateWorkflow(file, images, asins, "US", "en-US");
        return ResponseEntity.ok(taskId);
    }

    @PostMapping("/competitors/manual")
    public ResponseEntity<Void> submitManualCompetitors(
            @RequestParam String taskId,
            @RequestBody List<CompetitorSnapshot> manualSnapshots) {
        workflowService.upsertManualCompetitors(taskId, manualSnapshots);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/brief/approve")
    public ResponseEntity<Void> approveBrief(
            @RequestParam String taskId,
            @RequestBody ListingBriefVersion approvedBrief) {
        workflowService.startAssetGeneration(taskId, approvedBrief);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/asset/iterate")
    public ResponseEntity<Void> iterateAsset(
            @RequestParam String taskId,
            @RequestParam AssetTrack type, // TEXT or IMAGE
            @RequestParam String prompt,
            @RequestParam(required = false) MultipartFile refImage) {
        workflowService.iterateWorkflow(taskId, type, prompt, refImage);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/export")
    public ResponseEntity<String> exportPackage(
            @RequestParam String taskId,
            @RequestParam ExportFormat format) {
        String exportPackageId = workflowService.exportPackage(taskId, format);
        return ResponseEntity.ok(exportPackageId);
    }
    @PostMapping("/final/approve")
    public ResponseEntity<Void> finalApprove(
            @RequestParam String taskId,
            @RequestParam String selectedTextVersionId,
            @RequestParam String selectedImageVersionId) {
        workflowService.completeWorkflow(taskId, selectedTextVersionId, selectedImageVersionId);
        return ResponseEntity.ok().build();
    }
}
```


### 8.3 必要查询接口

除提交、审批、迭代、归档和导出命令接口外，第一版必须提供以下查询接口，支撑运营前端和验收测试：

- `GET /api/v1/listing/{taskId}`：查询任务详情，包含主状态、Brief 状态、文案状态、图片状态、类目模板、竞品快照摘要和最终选择结果。
- `GET /api/v1/listing/{taskId}/versions/text`：查询当前任务下所有 `TextVersion`，用于版本对比和终审选择。
- `GET /api/v1/listing/{taskId}/versions/image`：查询当前任务下所有 `ImageVersion`，用于图片组版本对比和终审选择。
- `GET /api/v1/listing/{taskId}/assets`：查询当前任务下所有 `ImageAsset`，包含类型、尺寸配置、合规状态和图片 URL。
- `GET /api/v1/listing/export/{exportPackageId}`：查询导出包状态、文件 URL、manifest URL 和失败原因。

查询接口不得触发生成、审批、归档或导出副作用。

### 8.4 Brief 抽取服务

```java
@Service
@RequiredArgsConstructor
public class BriefExtractionService {

    private final ChatClient chatClient;

    public ListingBriefVersion extractBrief(ProductRawData rawData, List<CompetitorSnapshot> snapshots, CategoryTemplate template) {
        String systemPrompt = """
            You are a senior Amazon US listing strategist for the configured product category.
            Generate a structured brief in English for Amazon US using the provided CategoryTemplate.
            Use competitor data only for insight extraction. Do not copy competitor wording.
            Include selling points, target keywords, image directions, forbidden claims, and compliance notes.
            Return strictly valid JSON matching the ListingBriefVersion schema.
            """;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(ops -> ops.text("Category template: " + template + " Product data: " + rawData + " Competitor snapshots: " + snapshots))
                .call()
                .entity(ListingBriefVersion.class);
    }
}
```

### 8.5 图片资产组生成服务

```java
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final ImageClient imageClient;
    private final ImageComplianceService complianceService;

    @Async("imageExecutor")
    public void generateImageVersion(String taskId, ImageVersion imageVersion, ListingBriefVersion brief) {
        // 1. 按 MAIN_IMAGE、INFOGRAPHIC、LIFESTYLE、DIMENSION、COMPATIBILITY、INSTALLATION、PACKAGE_CONTENTS、A_PLUS_MODULE 生成 prompt，每种类型默认 1 张
        // 2. 调用配置化 imageProvider/imageModel，不在代码中写死模型名称
        // 3. 写入 ImageAsset
        // 4. 对每张图执行规则检测 + 视觉模型检测，并写入 PASS/WARNING/FAIL
        // 5. 更新 imageStatus，并在文案流也完成后发出 WAIT_FINAL_APPROVE 通知
    }
}
```

---

## 9. 异常与边界防御指南

1. **多图入参校验**：前端和后端都必须限制原始产品图数量为 1-4 张；超过限制直接拒绝任务提交。
2. **文件校验**：Markdown 文档不能为空；图片必须校验格式、大小、分辨率和 MIME 类型。
3. **Bright Data 降级**：Bright Data 超时、限流、额度不足或字段缺失时，不阻塞主流程。系统记录 Warning，按“重试 -> 已缓存快照 -> 手工补录 -> 无竞品数据继续”的顺序降级。
4. **手工补录兜底**：手工录入的竞品信息必须标记 `sourceType = MANUAL`；Bright Data 数据必须标记 `sourceType = BRIGHT_DATA`，二者都可参与后续 Brief 生成。
5. **异步通知解耦**：飞书通知使用 `ApplicationEventPublisher` 抛出事件，由 `LarkNotificationListener` 异步消费。通知失败只影响 `notificationStatus`，不得阻塞主业务状态推进。
6. **模型失败重试**：文案生成和图片生成分别设置重试次数、超时时间和失败原因。图片组默认每种类型只生成 1 张候选；失败后优先重试失败的 `ImageAsset`，避免重跑整个图片组。进入终审前必须有完整可审核的图片资产组，或由运营明确接受缺失项。
7. **合规风险提示**：系统通过规则检测和视觉模型检测提供基础合规判断，但最终上架前仍需运营人工审核。`FAIL` 结果默认禁止归档和导出，管理员强制豁免时必须记录原因。
8. **导出失败隔离**：ZIP、Excel、Markdown、Word 导出失败不得影响 `COMPLETED` 状态；失败格式允许单独重试。

---

## 10. 验收标准

### 10.1 提交流程

- 运营可以上传 1-4 张产品图和 1 个 Markdown 产品文档。
- 系统自动创建 `ListingTask`，默认 `marketplace = US`、`language = en-US`、`categoryCode = CAR_STEREO`，并关联首批内置的 `Car Stereo` 类目模板。
- Bright Data 抓取 ASIN 失败时，任务不失败，并提示可使用缓存快照或手工补录。

### 10.2 Brief 审批

- 系统能基于 `CategoryTemplate` 和标准化后的 `CompetitorSnapshot` 生成结构化 `ListingBriefVersion`，首批验收 `Car Stereo` 模板。
- 运营可以修改卖点、关键词、图片方向和禁用声明。
- Brief 批准后才允许进入图文生成阶段。

### 10.3 文案生成

- 系统生成英文 Amazon Listing 文案，包含 Title、5 Bullet Points、Description、Backend Search Terms、Target Keywords 和 Compliance Warnings。
- 文案可多轮迭代，并保留 `parentVersionId`。
- 最终只能选择一个 `TextVersion` 归档。

### 10.4 图片生成

- 系统基于 `Car Stereo` 类目模板生成 7 图完整组：主图、卖点图、场景图、尺寸图、兼容图、安装图、包装清单图。
- 系统默认生成 1 张 A+ 模块图，尺寸匹配 Amazon A+ 常用模块尺寸，且尺寸配置可调整。
- 每张图都有独立的 `ImageAsset` 记录、类型、prompt、URL、尺寸配置和合规检查结果。`PASS/WARNING/FAIL` 必须包含检测方法和问题说明。
- 图片可按整组版本进行迭代，并保留 `parentVersionId`。`FAIL` 图片默认不可归档，`WARNING` 图片必须由运营确认后才能归档。
- 最终只能选择一个 `ImageVersion` 归档。

### 10.5 归档与导出

- 归档时必须同时选择一个文案版本和一个图片版本。
- 归档后可查看完整资产包、版本来源、生成参数、竞品快照来源、Bright Data 字段映射版本和合规警告。
- 已归档任务默认不可直接修改。
- 系统必须能导出 ZIP 默认交付包，ZIP 内必须包含最终图片资产、`listing.md`、`manifest.json` 和 `compliance_report.md`。
- `manifest.json` 必须包含任务 ID、类目模板、选中文案版本、选中图片版本、图片资产清单、尺寸配置、Bright Data 字段映射版本、合规检查结果和导出时间。
- `listing.md` 必须包含 Title、5 Bullet Points、Description、Backend Search Terms、Target Keywords 和 Compliance Warnings。
- `compliance_report.md` 必须包含图片和文案的 PASS/WARNING/FAIL 结果、问题说明和人工确认信息。
- 系统可选导出 Excel、Markdown、Word 文档；任一可选格式失败不得影响 ZIP 和归档状态。
- 导出文件必须只包含最终选中的 `TextVersion` 和 `ImageVersion`，不得混入未选中版本。

---

## 11. 仍需后续确认的问题

当前 PRD 范围内暂无阻塞第一版底座开发的待确认问题。







