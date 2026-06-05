# 电商 AI 图文资产流水线架构设计 V1.0

## 1. 架构结论

第一版采用 **模块化单体架构**。系统当前体量较小，主要面向企业内部运营团队使用，核心复杂度集中在 AI 图文生成流程、人工审核、版本树、合规检查、导出归档和外部数据源降级，而不是高并发交易处理。因此不在第一版拆分微服务。

后端采用 Spring Boot + Spring AI Alibaba，数据库采用 PostgreSQL。前端采用 Vue 3 + TypeScript + Naive UI，定位为内部运营工作台。

关键原则：

- 业务模块清晰隔离，但部署为一个后端应用。
- AI 模型、Bright Data、文件存储、飞书通知、导出能力全部通过适配层接入。
- 类目模板、Prompt、图片规格、合规规则配置化，避免把 Car Stereo 规则写死在业务代码中。
- 文案版本和图片版本分轨管理，最终归档时组合一个 `TextVersion` 和一个 `ImageVersion`。
- 外部数据失败不阻断主流程，模型生成和导出失败只影响对应子流程。

## 2. 后端总体架构

### 2.1 分层方式

后端采用四层结构：

- `api`：Controller、请求 DTO、响应 DTO、参数校验。
- `application`：用例编排、事务边界、状态流转、跨模块协调。
- `domain`：领域实体、枚举、领域服务、状态机规则。
- `infrastructure`：JPA、外部 API、文件存储、模型调用、通知、导出实现。

推荐包结构：

```text
com.snails.ecommerce
  common
    api
    error
    id
    storage
    validation
  listing
    api
    application
    domain
    infrastructure
  template
    api
    application
    domain
    infrastructure
  competitor
    application
    domain
    infrastructure
  generation
    application
    domain
    infrastructure
  compliance
    application
    domain
    infrastructure
  export
    application
    domain
    infrastructure
  notification
    application
    infrastructure
  admin
    api
    application
```

### 2.2 模块职责

`listing` 是主流程模块，负责 `ListingTask` 生命周期、Brief 审批、图文终审、归档状态和任务详情查询。

`template` 负责类目模板。第一版内置 `CAR_STEREO`，但模板字段、Prompt 片段、图片类型、尺寸配置、合规规则必须从模板读取。

`competitor` 负责竞品数据。它提供统一的 `CompetitorDataProvider` 端口，Bright Data 是第一版默认实现，手工补录和缓存快照作为降级来源。

`generation` 负责 AI 生成。文案生成、图片生成分别记录状态，图片资产按 `ImageVersion -> ImageAsset` 保存。

`compliance` 负责规则检查和视觉模型检查。规则检查作为硬门槛，视觉模型检查作为风险提示，最终仍由运营确认。

`export` 负责 ZIP、Markdown、Excel、Word 导出。ZIP 是默认交付包，可选格式失败不得影响任务归档状态。

`notification` 负责飞书通知。通知失败只记录通知状态和失败原因，不阻塞业务状态推进。

`admin` 负责内部配置管理，包括模型配置、Bright Data 配置、字段映射、类目模板和失败重试策略。

## 3. 技术选型

### 3.1 后端

- Java：建议使用当前项目配置的 Java 版本继续开发；如果团队运行环境不稳定，后续可降到 Java 21 LTS。
- Spring Boot：保留当前 Spring Boot 4 配置。
- Spring AI Alibaba：用于 LLM 文案生成、Brief 抽取、可选视觉模型检查。
- Spring Web MVC：提供 REST API。
- Spring Data JPA：第一版足够，便于快速落地 PostgreSQL 表关系。
- PostgreSQL：本地开发和第一版部署数据库。
- Lombok：保留，用于 DTO、实体和配置类减轻样板代码。
- 文件存储：第一版可使用本地目录，必须通过 `FileStoragePort` 抽象，后续可替换为 OSS 或 S3。

### 3.2 前端

- Vue 3
- TypeScript
- Vite
- Naive UI
- Pinia
- Vue Router
- Axios 或基于 Fetch 的统一请求封装

Naive UI 适合内部工具，组件覆盖表格、表单、抽屉、弹窗、步骤条、上传、图片预览和状态标签，第一版可以减少自研 UI 成本。

## 4. 数据库设计方向

PostgreSQL 表以 PRD 中的数据模型为基础，建议按以下核心表落地：

```text
category_template
competitor_provider_config
listing_task
product_raw_data
competitor_snapshot
listing_brief_version
text_version
image_version
image_asset
compliance_check_result
export_package
notification_record
```

设计要点：

- 主键使用字符串 ID 或 UUID，避免前后端暴露数据库自增序列。
- `listing_task` 保存主状态、文案状态、图片状态、Brief 状态和最终选中版本。
- `category_template` 中的字段、Prompt、图片配置、合规规则可以使用 JSONB 保存。
- `competitor_snapshot.raw_payload_url` 指向原始响应文件，`raw_payload_hash` 用于追溯字段变化。
- `text_version.parent_version_id` 和 `image_version.parent_version_id` 支持版本树。
- `image_asset` 必须记录资产类型、原始 Prompt、模型改写后 Prompt、尺寸配置、合规状态和排序。
- `export_package` 只引用最终选中的版本，避免导出混入未选中版本。
- `notification_record` 记录通知对象、通知类型、发送状态和失败原因。

JSONB 适合保存模板配置、模型生成参数、字段映射、合规问题列表和 manifest 快照。核心查询字段仍应拆成普通列，避免所有业务都依赖 JSON 查询。

## 5. 工作流设计

第一版工作流由 `ListingWorkflowService` 统一编排：

```text
SUBMITTED
  -> EXTRACTING_DATA
  -> WAIT_BRIEF_APPROVE
  -> GENERATING
  -> WAIT_FINAL_APPROVE
  -> COMPLETED
```

失败策略：

- Markdown 产品资料解析失败：主任务进入 `FAILED`。
- Bright Data 失败：记录 Warning，按重试、缓存快照、手工补录、无竞品继续降级。
- 文案生成失败：只更新 `textStatus`，允许单独重试。
- 图片生成失败：只更新 `imageStatus` 或具体 `ImageAsset` 失败状态，优先重试失败资产。
- 通知失败：只记录通知失败，不回滚业务状态。
- 可选导出失败：只影响对应 `ExportPackage`。

异步方式：

- 第一版使用 `ApplicationEventPublisher` + `@Async`。
- 事件包括 `TaskSubmittedEvent`、`BriefApprovedEvent`、`TextGeneratedEvent`、`ImageGeneratedEvent`、`TaskCompletedEvent`、`ExportRequestedEvent`。
- 后续如果任务量增加，再把事件消费者替换为 MQ，不改变应用层用例接口。

## 6. 外部能力适配

### 6.1 AI 文案与 Brief

定义端口：

```java
public interface ListingTextGenerator {
    ListingBriefVersion generateBrief(ProductRawData rawData, List<CompetitorSnapshot> competitors, CategoryTemplate template);
    TextVersion generateText(ListingBriefVersion brief, CategoryTemplate template, String iterationPrompt);
}
```

Spring AI Alibaba 实现放在 `generation.infrastructure`。业务层只依赖端口，不直接依赖具体模型调用代码。

### 6.2 图片生成

定义端口：

```java
public interface ImageAssetGenerator {
    List<ImageAsset> generateImageAssets(ImageVersion imageVersion, ListingBriefVersion brief, CategoryTemplate template);
}
```

图片模型名称、尺寸、质量参数和供应商从配置读取，不写死在业务代码中。图片供应商或大模型如果返回改写后的 Prompt，必须写入 `ImageAsset.rewrittenPrompt`；系统提交给供应商的原始 Prompt 保留在 `ImageAsset.prompt`，用于审计、复现和后续 Prompt 优化。

### 6.3 Bright Data

定义端口：

```java
public interface CompetitorDataProvider {
    List<CompetitorSnapshot> fetchSnapshots(List<String> asins, CompetitorProviderConfig config);
}
```

Bright Data 返回字段必须先映射为内部 `CompetitorSnapshot`，再进入 Brief Prompt。原始响应单独保存，不直接参与业务拼接。

### 6.4 文件存储

定义端口：

```java
public interface FileStoragePort {
    StoredFile save(FileStorageCommand command);
    InputStream read(String fileKey);
    URI resolveUrl(String fileKey);
}
```

第一版本地存储目录建议分为：

```text
storage
  uploads
  generated-images
  raw-payloads
  exports
```

## 7. API 设计边界

第一版 API 按任务主流程设计，不按数据库表机械暴露 CRUD。

核心命令接口：

```text
POST /api/v1/listing/submit
POST /api/v1/listing/{taskId}/competitors/manual
POST /api/v1/listing/{taskId}/brief/approve
POST /api/v1/listing/{taskId}/versions/text/iterate
POST /api/v1/listing/{taskId}/versions/image/iterate
POST /api/v1/listing/{taskId}/final/approve
POST /api/v1/listing/{taskId}/export
```

核心查询接口：

```text
GET /api/v1/listing
GET /api/v1/listing/{taskId}
GET /api/v1/listing/{taskId}/briefs
GET /api/v1/listing/{taskId}/versions/text
GET /api/v1/listing/{taskId}/versions/image
GET /api/v1/listing/{taskId}/assets
GET /api/v1/listing/export/{exportPackageId}
```

查询接口不得触发生成、审批、归档或导出副作用。

## 8. 前端架构

### 8.1 页面结构

第一版前端是内部运营工作台，推荐页面：

```text
/tasks                      任务列表
/tasks/new                  创建任务
/tasks/:taskId              任务详情
/tasks/:taskId/brief        Brief 审核
/tasks/:taskId/text         文案版本
/tasks/:taskId/images       图片版本
/tasks/:taskId/compliance   合规报告
/tasks/:taskId/export       归档导出
/admin/templates            类目模板
/admin/providers            供应商配置
/admin/models               模型配置
```

### 8.2 前端模块结构

```text
src
  app
    router
    stores
    layouts
  shared
    api
    components
    composables
    types
    utils
  features
    listing-task
    brief-review
    competitor
    text-version
    image-version
    compliance
    export-package
    template-admin
    provider-admin
    model-admin
```

### 8.3 关键交互

任务列表需要展示主状态、Brief 状态、文案状态、图片状态、更新时间和导出状态。

创建任务页需要支持 Markdown 上传、1-4 张产品图上传、ASIN 输入和基础校验。前端校验和后端校验都必须存在。

任务详情页采用工作流步骤视图，运营能直接看到当前卡在哪个环节。

Brief 审核页提供可编辑表单，字段包括目标受众、核心卖点、关键词、禁用声明、图片方向和合规提示。

文案版本页支持版本列表、版本详情、父版本关系、迭代 Prompt 和最终选择。

图片版本页按图片类型展示 7 图组和 A+ 图，显示尺寸、合规状态、问题说明和迭代入口。

归档导出页必须明确显示最终选中的文案版本和图片版本，避免运营误导出错误版本。

## 9. 第一阶段开发顺序

建议按以下顺序实现：

1. 基础工程整理：包名、配置、异常处理、统一响应、PostgreSQL 连接。
2. 核心领域模型：任务、模板、竞品快照、Brief、文案版本、图片版本、图片资产、导出包。
3. 类目模板：内置 `CAR_STEREO` 模板和读取服务。
4. 任务提交：Markdown 上传、图片上传、任务创建和基础查询。
5. 竞品模块：Bright Data 端口、手工补录、降级记录。
6. Brief 生成与审批：Spring AI Alibaba 接入和人工修改确认。
7. 文案生成：`TextVersion` 生成、迭代和选择。
8. 图片生成：`ImageVersion`、`ImageAsset`、图片状态和合规检查。
9. 终审归档：选择最终图文版本并完成任务。
10. ZIP 导出：生成图片文件、`listing.md`、`manifest.json`、`compliance_report.md`。
11. 前端工作台：任务列表、创建任务、任务详情、Brief 审核、版本对比、归档导出。
12. 管理配置：模板、供应商、模型配置。

## 10. 暂不做的事情

第一版不做微服务拆分。

第一版不做 Amazon Seller Central 自动发布。

第一版不做价格、库存、广告、订单管理。

第一版不引入复杂 BPMN 工作流引擎，除非后续审批链路明显复杂化。

第一版不把文件存储直接绑定到某个云厂商，先保留存储端口。

## 11. 风险与约束

Spring Boot 4 和 Java 26 对团队环境要求较高。如果依赖生态或本地环境出现兼容问题，建议尽早评估是否切换到 Java 21 LTS。

AI 生成结果不可完全信任，合规检查结果只能作为运营审核辅助。归档和导出前仍需要人工确认。

Bright Data 字段可能变化，字段映射必须版本化，原始响应必须可追溯。

图片生成成本高，第一版保持每种图片类型默认 1 张候选，通过版本迭代控制成本。

前端应避免把所有生成和审核能力堆在单页中。任务详情可以作为总览，Brief、文案、图片、合规、导出拆为明确子页或标签页。
