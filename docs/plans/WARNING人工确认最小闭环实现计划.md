# WARNING 人工确认最小闭环实现计划

> 本计划承接已完成的管理员合规豁免最小闭环。
> 本阶段目标是允许运营对 `WARNING` 图片资产记录人工确认原因，保留风险提示，同时让确认信息进入资产查询、manifest 和合规报告。

## 1. 当前基线

已完成：

- `ImageAsset` 已有人工确认字段：
  - `complianceReviewedBy`
  - `complianceReviewReason`
  - `complianceReviewedAt`
- `FAIL` 图片资产默认阻止导出。
- 管理员可对 `FAIL` 图片资产记录豁免，豁免后允许导出。
- manifest 和 `compliance_report.md` 已输出完整豁免信息。
- 图片资产查询响应已返回 `complianceReviewReason`。

当前未完成：

- 没有针对 `WARNING` 图片资产的人工确认服务方法。
- 没有针对 `WARNING` 图片资产的人工确认 HTTP 接口。
- 没有限制 `WARNING` 确认和 `FAIL` 豁免的语义边界。
- 没有测试证明 `WARNING` 确认信息会进入导出 manifest/report。

## 2. 阶段目标

本阶段完成后，应满足：

- 运营可以对单个 `WARNING` 图片资产记录人工确认。
- 只允许对 `WARNING` 图片资产执行普通人工确认。
- 确认必须记录：
  - 确认人
  - 确认原因
  - 确认时间
- `FAIL` 图片资产仍只能走管理员豁免接口。
- `PASS` 图片资产不可执行 `WARNING` 确认。
- 已确认 `WARNING` 资产导出时，manifest 和 `compliance_report.md` 包含确认信息。
- API 不直接暴露 JPA 实体。
- 完整测试通过。

## 3. 范围边界

### 3.1 本阶段做

- 扩展 `ImageAssetComplianceService`：
  - 新增 `confirmWarning(...)`
- 复用现有 DTO：
  - `ApproveImageAssetComplianceRequest`
  - `ImageAssetComplianceReviewResponse`
- 新增接口：
  - `POST /api/v1/listing/{taskId}/versions/image/{imageVersionId}/assets/{assetId}/compliance/confirm-warning`
- 补充服务测试：
  - `WARNING` 可人工确认。
  - `FAIL` 不可走 WARNING 确认。
  - `PASS` 不可走 WARNING 确认。
  - 归属校验沿用图片资产合规服务。
- 补充导出测试：
  - 已确认 `WARNING` 信息进入 manifest/report。

### 3.2 本阶段不做

- 不强制所有 `WARNING` 都确认后才能导出。
- 不做撤销确认。
- 不做批量确认。
- 不做权限系统。
- 不做真实合规检测。

## 4. 设计原则

- `WARNING` 确认不修改 `complianceStatus`，保留风险提示。
- `WARNING` 确认和 `FAIL` 管理员豁免使用不同服务方法和 HTTP 路由，避免语义混淆。
- 导出服务继续允许 `WARNING` 导出，但会完整携带确认信息。
- Controller 只负责请求校验和统一响应。

## 5. 文件结构

### 5.1 修改文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ImageAssetComplianceService.java
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ImageAssetComplianceServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ExportPackageServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java
docs/plans/WARNING人工确认最小闭环实现计划.md
```

## 6. 任务拆解

### 任务 1：实现 WARNING 确认服务能力

- [x] **步骤 1：补充服务测试**

覆盖：

- `WARNING` 图片资产可记录确认人、原因和时间。
- `FAIL` 图片资产不可走 WARNING 确认。
- `PASS` 图片资产不可走 WARNING 确认。
- 图片版本必须属于任务。
- 图片资产必须属于图片版本。

- [x] **步骤 2：实现 `confirmWarning`**

职责：

- 复用任务、图片版本、图片资产归属校验。
- 校验资产状态为 `WARNING`。
- 写入确认人、原因和时间。
- 返回稳定 DTO。

- [x] **步骤 3：业务文件 review 关卡**

`ImageAssetComplianceService.java` 属于业务逻辑文件，完成后先提交给用户 review。

### 任务 2：导出报告保留 WARNING 确认信息

- [x] **步骤 1：补充导出服务测试**

验证：

- 已确认 `WARNING` 资产导出时，manifest 包含确认人和原因。
- `compliance_report.md` 包含确认人和原因。

- [x] **步骤 2：确认导出服务无需额外改动或补齐缺口**

当前 `ExportPackageService` 已输出 `complianceReviewedBy`、`complianceReviewReason` 和 `complianceReviewedAt`。
如果测试发现缺口，则在该服务内补齐。

### 任务 3：实现 WARNING 确认 HTTP 接口

- [x] **步骤 1：扩展 Controller 测试**

新增接口测试：

- `POST /api/v1/listing/{taskId}/versions/image/{imageVersionId}/assets/{assetId}/compliance/confirm-warning`

覆盖：

- 成功返回统一 `ApiResponse`
- 请求校验失败返回 `INVALID_REQUEST`
- 业务错误透传稳定错误码

- [x] **步骤 2：修改 `ListingTaskController`**

Controller 只负责薄封装：

- 调用 `confirmWarning`
- 返回统一 `ApiResponse`

- [x] **步骤 3：API 边界 review 关卡**

`ListingTaskController.java` 涉及 API 边界，修改后先提交给用户 review。

### 任务 4：完整回归和计划收尾

- [x] **步骤 1：运行合规服务测试**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ImageAssetComplianceServiceTest"
```

- [x] **步骤 2：运行导出服务测试**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ExportPackageServiceTest"
```

- [x] **步骤 3：运行 Controller 回归**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ListingTaskControllerTest"
```

- [x] **步骤 4：运行完整测试**

```powershell
cd e-commerce
.\gradlew.bat test
```

- [x] **步骤 5：更新本计划勾选状态**

每完成一个任务，同步更新本文件。

## 7. 验收清单

- [x] `WARNING` 图片资产可记录人工确认。
- [x] 确认必须记录确认人、原因和时间。
- [x] `FAIL` 图片资产不可走 WARNING 确认。
- [x] `PASS` 图片资产不可走 WARNING 确认。
- [x] 已确认 `WARNING` 资产导出时 manifest 包含确认信息。
- [x] 已确认 `WARNING` 资产导出时 `compliance_report.md` 包含确认信息。
- [x] HTTP 接口返回统一 `ApiResponse`。
- [x] API 不直接暴露 JPA 实体。
- [x] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。

## 8. 后续阶段入口

本阶段完成后，下一步建议进入：

- Excel / Markdown / Word 可选导出。
- 前端归档导出页。
- 真实合规检测接入。
- 文案和图片人工迭代。
