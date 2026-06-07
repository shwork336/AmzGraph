# Markdown 可选导出最小闭环实现计划

> 本计划承接 ZIP 默认交付包、导出失败重试、合规 FAIL 拦截、管理员合规豁免和 WARNING 人工确认闭环。
> 本阶段目标是先落地风险最低的可选导出格式：Markdown，用于知识库归档、代码仓库保存和轻量人工审阅。

## 1. 当前基线

已完成：

- 任务终审后进入 `COMPLETED`。
- 已完成任务可以生成 ZIP 默认交付包。
- 导出记录通过 `ExportPackage` 保存格式、状态、文件 URL、失败原因和包含的图片资产 ID。
- `ExportFormat` 已包含 `MARKDOWN`。
- 合规 `FAIL` 图片资产默认拦截导出，存在完整管理员豁免信息时允许导出。
- `WARNING` 图片资产可记录人工确认信息，并已进入 ZIP manifest 和合规报告。

当前未完成：

- 没有 Markdown 可选导出服务方法。
- 没有 Markdown 可选导出 HTTP 接口。
- `ExportPackageService#createRunningPackage` 仍硬编码 `ZIP`。
- 没有测试证明 Markdown 导出复用最终图文选择和合规导出边界。

## 2. 阶段目标

本阶段完成后，应满足：

- 已完成任务可生成 Markdown 可选导出文件。
- Markdown 导出只读取任务最终选中的文案版本和图片版本。
- Markdown 文件包含：
  - 任务基础信息
  - 最终文案版本 ID
  - 最终图片版本 ID
  - Listing 标题、要点、描述、后台搜索词
  - 图片资产 ID、类型、URL、尺寸、合规状态、合规方法、问题和人工确认/豁免信息
- 导出成功创建 `ExportPackage`：
  - `format = MARKDOWN`
  - `status = SUCCEEDED`
  - `fileUrl` 指向 `.md` 文件
  - `manifestUrl = null`
- 允许同一任务重复生成多个 Markdown 导出记录。
- 未豁免的 `FAIL` 图片资产仍拦截导出，且不创建新的 `ExportPackage`。
- API 不直接暴露 JPA 实体。
- 完整测试通过。

## 3. 范围边界

### 3.1 本阶段做

- 扩展 `ExportPackageService`：
  - 新增 `exportMarkdown(String taskId)`
  - 抽出可复用的导出上下文加载逻辑
  - 让运行中导出记录支持传入 `ExportFormat`
  - 新增 Markdown 文件内容生成逻辑
- 扩展 `ListingTaskController`：
  - 新增 `POST /api/v1/listing/{taskId}/export/markdown`
- 补充服务测试：
  - Markdown 导出成功
  - Markdown 重复导出生成多条记录
  - 未完成任务不可导出 Markdown
  - 未豁免 FAIL 图片资产不可导出 Markdown
  - 已豁免 FAIL 图片资产可导出 Markdown 并保留豁免信息
- 补充 Controller 测试：
  - Markdown 导出成功
  - Markdown 导出业务错误透传

### 3.2 本阶段不做

- 不做 Excel / Word 导出。
- 不把 ZIP 和 Markdown 重试统一泛化。
- 不做 Markdown 模板配置。
- 不做异步导出队列。
- 不改变 `COMPLETED` 任务状态。
- 不实现真实图片文件打包。

## 4. 设计原则

- Markdown 是可选导出格式，失败不得影响任务归档状态和 ZIP 默认交付包。
- Markdown 导出复用 ZIP 已有的最终选择校验和合规拦截规则，避免两个格式出现业务边界分叉。
- Markdown 文件自身承载审阅信息，因此本阶段不额外生成 manifest。
- Controller 只负责路由、请求包装和统一响应，业务规则留在应用服务。

## 5. 文件结构

### 5.1 修改文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ExportPackageService.java
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ExportPackageServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java
docs/plans/Markdown可选导出最小闭环实现计划.md
```

## 6. 任务拆解

### 任务 1：实现 Markdown 导出服务能力

- [x] **步骤 1：补充服务测试**

覆盖：

- 已完成任务可导出 Markdown。
- Markdown 文件包含最终图文版本、文案内容、图片资产和合规信息。
- 同一任务可重复导出 Markdown。
- 未完成任务不可导出 Markdown。
- 未豁免 `FAIL` 图片资产不可导出 Markdown。
- 已豁免 `FAIL` 图片资产可导出 Markdown，并保留豁免信息。

- [x] **步骤 2：实现 `exportMarkdown`**

职责：

- 加载任务最终图文版本和图片资产。
- 复用任务完成状态、最终选择和合规可导出校验。
- 创建 `MARKDOWN` 导出记录。
- 保存单个 Markdown 文件。
- 成功后写入文件 URL 和 `SUCCEEDED`。
- 保存失败时写入 `FAILED` 和失败原因。

- [x] **步骤 3：业务文件 review 关卡**

`ExportPackageService.java` 属于业务逻辑文件，完成后需提交给用户 review。

### 任务 2：实现 Markdown 导出 HTTP 接口

- [x] **步骤 1：补充 Controller 测试**

新增接口测试：

- `POST /api/v1/listing/{taskId}/export/markdown`

覆盖：

- 成功返回统一 `ApiResponse`。
- 业务错误透传稳定错误码。

- [x] **步骤 2：修改 `ListingTaskController`**

Controller 只负责封装：

- 调用 `exportMarkdown`
- 返回统一 `ApiResponse`

- [x] **步骤 3：API 边界 review 关卡**

`ListingTaskController.java` 涉及 API 边界，修改后需提交给用户 review。

### 任务 3：完整回归和计划收尾

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

- [x] **步骤 4：更新本计划勾选状态**

每完成一个任务，同步更新本文档。

## 7. 验收清单

- [x] 已完成任务可导出 Markdown。
- [x] Markdown 导出只使用最终选中的文案和图片版本。
- [x] Markdown 文件包含 Listing 文案。
- [x] Markdown 文件包含图片资产和合规信息。
- [x] Markdown 文件包含人工确认/豁免信息。
- [x] `ExportPackage.format = MARKDOWN`。
- [x] `ExportPackage.status = SUCCEEDED`。
- [x] `ExportPackage.fileUrl` 指向 `.md` 文件。
- [x] `ExportPackage.manifestUrl = null`。
- [x] 未豁免 `FAIL` 图片资产阻止 Markdown 导出。
- [x] HTTP 接口返回统一 `ApiResponse`。
- [x] API 不直接暴露 JPA 实体。
- [x] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。

## 8. 后续阶段入口

本阶段完成后，下一步建议进入：

- Markdown/ZIP 可选导出失败重试统一化。
- Excel 可选导出。
- Word 可选导出。
- 前端归档导出页。
