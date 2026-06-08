# ZIP 默认交付包最小闭环实现计划

> 本计划承接已完成的终审选择最小闭环。
> 本阶段目标是让已 `COMPLETED` 的任务基于最终选中的 `TextVersion` 和 `ImageVersion` 生成默认 ZIP 交付包，并支持查询导出包状态。

## 1. 当前基线

已完成：

- 文案和图片均生成成功后，任务进入 `WAIT_FINAL_APPROVE`。
- 运营可终审选择最终文案版本和图片版本。
- 终审成功后：
  - `ListingTask.status = COMPLETED`
  - `ListingTask.selectedTextVersionId` 已写入
  - `ListingTask.selectedImageVersionId` 已写入
  - 被选中的文案和图片版本 `selected = true`
- `ExportPackage` 和 `ExportFormat` 领域骨架已存在。
- `ImageAssetRepository` 已支持按图片版本查询图片资产。
- 文件存储端口 `FileStoragePort` 已支持保存和读取文件。

当前未完成：

- 没有 `ExportPackageRepository`。
- 没有导出请求或响应 DTO。
- 没有导出应用服务。
- 没有 ZIP 文件生成逻辑。
- 没有导出包查询接口。
- `ExportPackage` 没有失败原因字段。
- 没有 `POST /api/v1/listing/{taskId}/export`。
- 没有 `GET /api/v1/listing/export/{exportPackageId}`。

## 2. 阶段目标

本阶段完成后，应满足：

- 只有 `COMPLETED` 任务允许导出默认 ZIP。
- 导出必须引用任务最终选中的 `TextVersion` 和 `ImageVersion`。
- 导出的图片资产必须来自最终选中的 `ImageVersion`。
- ZIP 包至少包含：
  - `listing.md`
  - `manifest.json`
  - `compliance_report.md`
  - `images/` 下的图片资产占位文件或可读取文件内容
- 系统创建一条 `ExportPackage` 记录。
- `ExportPackage.format = ZIP`。
- 导出成功后：
  - `ExportPackage.status = SUCCEEDED`
  - `fileUrl` 指向 ZIP 文件
  - `manifestUrl` 指向 manifest 文件
  - `includedAssetIdsJson` 记录纳入导出的图片资产 ID
- 导出失败时记录 `FAILED` 和失败原因，不回退任务状态。
- 支持查询导出包状态、文件 URL、manifest URL 和失败原因。
- ZIP 导出可重复生成，同一任务允许多条导出记录。
- API 不直接暴露 JPA 实体。
- 完整测试通过。

## 3. 范围边界

### 3.1 本阶段做

- 扩展 `ExportPackage`：
  - 增加 `failureReason` 字段。
- 新增仓储：
  - `ExportPackageRepository`
- 新增 DTO：
  - `ExportPackageResponse`
- 新增应用服务：
  - `ExportPackageService`
- 新增接口：
  - `POST /api/v1/listing/{taskId}/export`
  - `GET /api/v1/listing/export/{exportPackageId}`
- 生成 ZIP 默认交付包：
  - `listing.md`
  - `manifest.json`
  - `compliance_report.md`
  - `images/*`

### 3.2 本阶段不做

- 不做 Excel / Markdown / Word 可选导出。
- 不做异步导出队列。
- 不做导出进度百分比。
- 不做失败导出重试接口。
- 不做管理员合规豁免。
- 不做真实图片 URL 下载；本地存储可读取时写入原文件内容，不可读取时写入占位说明文件。

## 4. 设计原则

- 导出服务只读取最终选中的版本，不接收前端指定的文案或图片版本 ID。
- 导出失败不影响 `ListingTask.status`。
- 导出包记录是审计记录，同一任务可重复生成多条 ZIP 导出记录。
- Controller 只负责请求封装和统一响应，不直接操作实体。
- ZIP 生成逻辑先保持同步执行，后续需要异步时再抽离任务调度。

## 5. 文件结构

### 5.1 新建文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ExportPackageResponse.java
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ExportPackageService.java
e-commerce/src/main/java/com/snails/ecommerce/listing/infrastructure/ExportPackageRepository.java

e-commerce/src/test/java/com/snails/ecommerce/listing/application/ExportPackageServiceTest.java
e-commerce/src/test/java/com/snails/ecommerce/listing/infrastructure/ExportPackageRepositoryTest.java
```

### 5.2 修改文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/api/ListingTaskController.java
e-commerce/src/main/java/com/snails/ecommerce/listing/domain/ExportPackage.java
e-commerce/src/test/java/com/snails/ecommerce/listing/api/ListingTaskControllerTest.java
docs/plans/ZIP默认交付包最小闭环实现计划.md
```

## 6. 任务拆解

### 任务 1：补齐导出包持久化能力

- [x] **步骤 1：补充 ExportPackageRepository 测试**

验证：

- 可以保存 ZIP 导出包。
- 可以按 `taskId` 和创建时间倒序查询同一任务的导出包历史。

- [x] **步骤 2：扩展 `ExportPackage`**

新增字段：

```java
private String failureReason;
```

- [x] **步骤 3：新增 `ExportPackageRepository`**

建议方法：

```java
List<ExportPackage> findByTaskIdOrderByCreatedAtDescExportPackageIdDesc(String taskId);
```

- [x] **步骤 4：运行仓储测试**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ExportPackageRepositoryTest"
```

### 任务 2：实现 ZIP 导出应用服务

- [x] **步骤 1：定义导出响应 DTO**

新增：

- `ExportPackageResponse`

- [x] **步骤 2：补充导出服务测试**

覆盖：

- `COMPLETED` 任务可导出 ZIP。
- 非 `COMPLETED` 任务返回 `TASK_STATUS_INVALID`。
- 任务不存在返回 `TASK_NOT_FOUND`。
- 缺少最终文案或图片版本返回 `TASK_STATUS_INVALID`。
- 最终文案版本不存在返回 `INVALID_REQUEST`。
- 最终图片版本不存在返回 `INVALID_REQUEST`。
- 图片资产来自最终图片版本。
- 可重复导出同一任务并生成多条记录。

- [x] **步骤 3：实现 `ExportPackageService`**

职责：

- 查询任务。
- 校验任务处于 `COMPLETED`。
- 读取任务最终选中的文案和图片版本。
- 查询最终图片版本下的图片资产。
- 生成 `listing.md`、`manifest.json`、`compliance_report.md`。
- 生成 ZIP 文件并保存到 `FileStoragePort`。
- 单独保存 manifest 文件到 `FileStoragePort`。
- 创建并保存 `ExportPackage`。
- 返回 `ExportPackageResponse`。

- [x] **步骤 4：业务文件 review 关卡**

`ExportPackageService.java` 属于业务逻辑文件，完成后先提交给用户 review。

### 任务 3：实现导出 HTTP 接口

- [x] **步骤 1：扩展 Controller 测试**

新增接口测试：

- `POST /api/v1/listing/{taskId}/export`
- `GET /api/v1/listing/export/{exportPackageId}`

覆盖：

- 成功返回统一 `ApiResponse`
- 业务错误透传稳定错误码
- 查询接口不触发导出副作用

- [x] **步骤 2：修改 `ListingTaskController`**

Controller 只负责薄封装：

- 注入 `ExportPackageService`
- 调用 `exportDefaultZip`
- 调用 `getExportPackage`
- 返回统一 `ApiResponse`

- [x] **步骤 3：API 边界 review 关卡**

`ListingTaskController.java` 和导出 DTO 涉及 API 边界，修改后先提交给用户 review。

### 任务 4：完整回归和计划收尾

- [x] **步骤 1：运行导出模块测试**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ExportPackageServiceTest" --tests "*ExportPackageRepositoryTest"
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

每完成一个任务，同步更新本文件。

## 7. 验收清单

- [x] 只有 `COMPLETED` 任务允许导出 ZIP。
- [x] 导出只引用最终选中的文案版本。
- [x] 导出只引用最终选中的图片版本。
- [x] 导出图片资产来自最终选中的图片版本。
- [x] ZIP 包包含 `listing.md`。
- [x] ZIP 包包含 `manifest.json`。
- [x] ZIP 包包含 `compliance_report.md`。
- [x] ZIP 包包含 `images/` 下的图片资产文件或占位说明。
- [x] 导出成功后创建 `ExportPackage`。
- [x] 导出成功后记录 `fileUrl`、`manifestUrl` 和 `includedAssetIdsJson`。
- [x] 同一任务可以重复导出。
- [x] 查询接口返回导出状态、文件 URL、manifest URL 和失败原因。
- [x] API 不直接暴露 JPA 实体。
- [x] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。

## 8. 后续阶段入口

本阶段完成后，下一步建议进入：

- 导出失败重试。
- Excel / Markdown / Word 可选导出。
- 合规 FAIL 资产拦截与管理员豁免。
- 前端归档导出页。
