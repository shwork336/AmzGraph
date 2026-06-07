# Markdown 导出失败重试最小闭环实现计划

> 本计划承接 Markdown 可选导出最小闭环。
> PRD 要求可选导出失败不得影响已归档任务和 ZIP 默认交付包，并允许单独重试对应格式。本阶段先补齐已落地的 Markdown 格式重试能力。

## 1. 当前基线

已完成：

- ZIP 默认交付包支持失败重试。
- Markdown 可选导出已落地。
- `ExportPackage` 可以记录导出格式、状态、文件 URL、失败原因和包含的资产 ID。
- `GET /api/v1/listing/export/{exportPackageId}` 可查询所有格式导出记录。
- `POST /api/v1/listing/export/{exportPackageId}/retry` 已存在。

当前未完成：

- `ExportPackageService#retryExportPackage` 只允许 `ZIP`。
- `MARKDOWN` 失败导出记录不能重试。
- 没有测试证明 Markdown 重试会创建新记录并保留原失败记录。

## 2. 阶段目标

本阶段完成后，应满足：

- `FAILED + ZIP` 仍按原行为重试 ZIP。
- `FAILED + MARKDOWN` 可重试 Markdown。
- 重试会创建新的 `ExportPackage` 记录，不覆盖原失败记录。
- 原失败记录保留原状态和失败原因，用于审计。
- `SUCCEEDED` 导出记录不可重试。
- 暂未实现的 `EXCEL`、`WORD` 仍不可重试。
- HTTP 接口继续复用 `POST /api/v1/listing/export/{exportPackageId}/retry`。
- 完整测试通过。

## 3. 范围边界

### 3.1 本阶段做

- 扩展 `ExportPackageService#retryExportPackage`：
  - `ZIP` 调用 `exportDefaultZip`
  - `MARKDOWN` 调用 `exportMarkdown`
  - `EXCEL`、`WORD` 返回稳定业务错误
- 补充服务测试：
  - Markdown 失败导出可重试成功
  - Markdown 重试保留原失败记录
  - Excel/Word 仍不可重试
- 补充 Controller 测试：
  - 现有 retry 接口可返回 Markdown 重试结果

### 3.2 本阶段不做

- 不做 Excel / Word 导出。
- 不做 Excel / Word 重试。
- 不新增新的重试 HTTP 路由。
- 不改变 `COMPLETED` 任务状态。
- 不改造为异步任务。

## 4. 设计原则

- 重试基于旧导出记录的 `format` 和 `taskId`，不复用旧文件 URL。
- 新导出记录重新读取任务最终选择和当前资产合规状态。
- 原失败记录不修改，避免丢失失败审计信息。
- 对未实现格式保持显式拒绝，避免前端误以为 Excel/Word 已可用。

## 5. 文件结构

### 5.1 修改文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ExportPackageService.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ExportPackageServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java
docs/plans/Markdown导出失败重试最小闭环实现计划.md
```

## 6. 任务拆解

### 任务 1：实现 Markdown 重试服务能力

- [x] **步骤 1：补充服务测试**

覆盖：

- `FAILED + MARKDOWN` 可重试。
- 重试成功后创建新的 Markdown 导出记录。
- 原失败记录状态和失败原因不变。
- `EXCEL`、`WORD` 仍不可重试。

- [x] **步骤 2：扩展 `retryExportPackage`**

职责：

- 先校验导出记录存在。
- 再校验状态必须为 `FAILED`。
- 根据格式分派：
  - `ZIP` -> `exportDefaultZip`
  - `MARKDOWN` -> `exportMarkdown`
  - 其他格式 -> `INVALID_REQUEST`

- [x] **步骤 3：业务文件 review 关卡**

`ExportPackageService.java` 属于业务逻辑文件，修改后需提交给用户 review。

### 任务 2：补充 HTTP 回归

- [x] **步骤 1：补充 Controller 测试**

覆盖：

- retry 接口可返回 `MARKDOWN` 格式响应。

- [x] **步骤 2：确认 Controller 无需新增路由**

复用现有：

```text
POST /api/v1/listing/export/{exportPackageId}/retry
```

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

## 7. 验收清单

- [x] `FAILED + ZIP` 仍可重试。
- [x] `FAILED + MARKDOWN` 可重试。
- [x] Markdown 重试创建新导出记录。
- [x] Markdown 重试保留原失败记录。
- [x] `SUCCEEDED` 导出记录不可重试。
- [x] `EXCEL`、`WORD` 仍不可重试。
- [x] retry HTTP 接口可返回 Markdown 格式响应。
- [x] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。

## 8. 后续阶段入口

本阶段完成后，下一步建议进入：

- Excel 可选导出。
- Word 可选导出。
- 前端归档导出页。
- 导出任务异步化和导出列表查询。
