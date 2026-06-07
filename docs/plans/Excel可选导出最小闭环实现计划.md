# Excel 可选导出最小闭环实现计划

> 本计划承接 ZIP 默认交付包、Markdown 可选导出和 Markdown 导出失败重试闭环。
> PRD 要求 Excel 适合批量复制到运营表格，包含 Listing 文案、关键词、图片文件名、图片类型、合规结果和版本信息。本阶段先落地可打开、可复制、可审计的最小 `.xlsx`。

## 1. 当前基线

已完成：

- 已完成任务可导出 ZIP 默认交付包。
- 已完成任务可导出 Markdown 可选文件。
- ZIP / Markdown 失败导出可单独重试。
- `ExportFormat` 已包含 `EXCEL`。
- 导出服务已抽出统一导出上下文，复用任务完成状态、最终选择和合规可导出校验。

当前未完成：

- 没有 Excel 可选导出服务方法。
- 没有 Excel 可选导出 HTTP 接口。
- `EXCEL` 失败导出记录仍不可重试。
- 没有测试证明 Excel 文件包含 Listing、图片资产和合规信息。

## 2. 阶段目标

本阶段完成后，应满足：

- 已完成任务可生成 `.xlsx` Excel 可选导出文件。
- Excel 导出只读取任务最终选中的文案版本和图片版本。
- Excel 文件至少包含三个工作表：
  - `Task`：任务、站点、语言、最终版本 ID
  - `Listing`：标题、五点、描述、后台搜索词、关键词
  - `Images`：图片资产 ID、类型、文件 URL、尺寸、合规状态、合规方法、问题、人工确认/豁免信息
- 导出成功创建 `ExportPackage`：
  - `format = EXCEL`
  - `status = SUCCEEDED`
  - `fileUrl` 指向 `.xlsx` 文件
  - `manifestUrl = null`
- 允许同一任务重复生成多个 Excel 导出记录。
- 未豁免的 `FAIL` 图片资产仍拦截导出，且不创建新的 `ExportPackage`。
- `FAILED + EXCEL` 可通过现有 retry 接口重试。
- API 不直接暴露 JPA 实体。
- 完整测试通过。

## 3. 范围边界

### 3.1 本阶段做

- 扩展 `ExportPackageService`：
  - 新增 `exportExcel(String taskId)`
  - 新增最小 `.xlsx` 生成逻辑
  - 扩展 `retryExportPackage` 支持 `EXCEL`
- 扩展 `ListingTaskController`：
  - 新增 `POST /api/v1/listing/{taskId}/export/excel`
- 补充服务测试：
  - Excel 导出成功
  - `.xlsx` 包含核心工作簿条目和业务文本
  - 重复导出创建多条记录
  - 未完成任务不可导出 Excel
  - 未豁免 `FAIL` 图片资产不可导出 Excel
  - 已豁免 `FAIL` 图片资产可导出 Excel 并保留豁免信息
  - `FAILED + EXCEL` 可重试
- 补充 Controller 测试：
  - Excel 导出成功
  - Excel 导出业务错误透传
  - retry 接口可返回 Excel 重试结果

### 3.2 本阶段不做

- 不引入 Apache POI 或其他 Excel 依赖。
- 不做复杂样式、公式、筛选器和冻结窗格。
- 不嵌入真实图片二进制。
- 不做 Word 导出。
- 不新增独立 Excel 重试路由。
- 不改造为异步导出。

## 4. 设计原则

- 使用标准库生成 Office Open XML `.xlsx` ZIP 包，避免新增依赖和外部下载。
- Excel 导出复用 ZIP / Markdown 已有导出边界，避免绕过终审和合规拦截。
- Excel 文件自身承载运营复制所需信息，本阶段不额外生成 manifest。
- 重试基于旧导出记录的 `format` 和 `taskId`，创建新记录，不覆盖旧记录。

## 5. 文件结构

### 5.1 修改文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ExportPackageService.java
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ExportPackageServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java
docs/plans/Excel可选导出最小闭环实现计划.md
```

## 6. 任务拆解

### 任务 1：实现 Excel 导出服务能力

- [x] **步骤 1：补充服务测试**

覆盖：

- 已完成任务可导出 Excel。
- Excel 文件包含 `Task`、`Listing`、`Images` 工作表。
- Excel 文件包含 Listing 文案、关键词、图片资产、合规状态和人工确认/豁免信息。
- 同一任务可重复导出 Excel。
- 未完成任务不可导出 Excel。
- 未豁免 `FAIL` 图片资产不可导出 Excel。
- 已豁免 `FAIL` 图片资产可导出 Excel。

- [x] **步骤 2：实现 `exportExcel`**

职责：

- 加载统一导出上下文。
- 创建 `EXCEL` 导出记录。
- 生成 `.xlsx` 文件并保存。
- 成功后写入文件 URL 和 `SUCCEEDED`。
- 保存失败时写入 `FAILED` 和失败原因。

- [x] **步骤 3：业务文件 review 关卡**

`ExportPackageService.java` 属于业务逻辑文件，修改后需提交给用户 review。

### 任务 2：实现 Excel 导出 HTTP 接口

- [x] **步骤 1：补充 Controller 测试**

新增接口测试：

- `POST /api/v1/listing/{taskId}/export/excel`

覆盖：

- 成功返回统一 `ApiResponse`。
- 业务错误透传稳定错误码。

- [x] **步骤 2：修改 `ListingTaskController`**

Controller 只负责封装：

- 调用 `exportExcel`
- 返回统一 `ApiResponse`

### 任务 3：实现 Excel 重试

- [x] **步骤 1：补充重试测试**

覆盖：

- `FAILED + EXCEL` 可重试。
- 重试创建新导出记录。
- 原失败记录保留。
- retry HTTP 接口可返回 Excel 格式响应。

- [x] **步骤 2：扩展 `retryExportPackage`**

新增格式分派：

- `EXCEL` -> `exportExcel`

### 任务 4：完整回归和计划收尾

- [x] **步骤 1：运行导出服务测试**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ExportPackageServiceTest"
```

- [x] **步骤 2：运行 Controller 回归**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ListingTaskControllerTest"
```

- [x] **步骤 3：运行完整测试**

```powershell
cd e-commerce
.\gradlew.bat test
```

## 7. 验收清单

- [x] 已完成任务可导出 `.xlsx`。
- [x] Excel 导出只使用最终选中的文案和图片版本。
- [x] Excel 文件包含 `Task` 工作表。
- [x] Excel 文件包含 `Listing` 工作表。
- [x] Excel 文件包含 `Images` 工作表。
- [x] Excel 文件包含图片资产和合规信息。
- [x] Excel 文件包含人工确认/豁免信息。
- [x] `ExportPackage.format = EXCEL`。
- [x] `ExportPackage.status = SUCCEEDED`。
- [x] `ExportPackage.fileUrl` 指向 `.xlsx` 文件。
- [x] `ExportPackage.manifestUrl = null`。
- [x] 未豁免 `FAIL` 图片资产阻止 Excel 导出。
- [x] `FAILED + EXCEL` 可重试。
- [x] HTTP 接口返回统一 `ApiResponse`。
- [x] API 不直接暴露 JPA 实体。
- [x] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。

## 8. 后续阶段入口

本阶段完成后，下一步建议进入：

- Word 可选导出。
- 前端归档导出页。
- 导出列表查询。
- 导出任务异步化。
