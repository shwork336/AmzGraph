# 合规 FAIL 资产导出拦截最小闭环实现计划

> 本计划承接已完成的 ZIP 默认交付包和导出失败重试最小闭环。
> 本阶段目标是在导出 ZIP 前拦截最终图片版本中的 `FAIL` 图片资产，避免默认导出存在硬性上架风险的素材。

## 1. 当前基线

已完成：

- 已完成任务可生成 ZIP 默认交付包。
- ZIP 导出只读取任务最终选中的文案版本和图片版本。
- ZIP 内包含 `listing.md`、`manifest.json`、`compliance_report.md` 和图片资产占位文件。
- `manifest.json` 和 `compliance_report.md` 已记录图片资产合规状态。
- `WARNING` 图片资产目前会被导出并在 manifest/report 中标记。
- 导出失败不影响主任务 `COMPLETED` 状态。
- 失败的 ZIP 导出包可单独重试。

当前未完成：

- `FAIL` 图片资产仍可能被导出。
- 没有导出前的硬性合规拦截。
- 没有验证 `WARNING` 仍允许导出。
- 没有管理员豁免模型，因此不能安全放行 `FAIL`。

## 2. 阶段目标

本阶段完成后，应满足：

- ZIP 导出前检查最终图片版本下的全部图片资产。
- 任一资产 `complianceStatus = FAIL` 时，导出请求返回 `TASK_STATUS_INVALID`。
- `FAIL` 拦截不创建新的 `ExportPackage` 记录。
- `WARNING` 资产仍允许导出，并继续出现在 manifest 和合规报告中。
- 重试失败导出包时同样执行 `FAIL` 拦截。
- API 不直接暴露 JPA 实体。
- 完整测试通过。

## 3. 范围边界

### 3.1 本阶段做

- 扩展 `ExportPackageService`：
  - 在 ZIP 打包前校验图片资产合规状态。
  - `FAIL` 资产返回稳定业务错误。
- 补充服务测试：
  - `FAIL` 资产阻止导出。
  - `FAIL` 资产阻止失败包重试。
  - `WARNING` 资产仍允许导出。
  - `FAIL` 拦截不创建 `ExportPackage` 记录。

### 3.2 本阶段不做

- 不做管理员豁免实体。
- 不做管理员豁免审批接口。
- 不做 `WARNING` 人工确认流。
- 不做真实视觉模型合规检测。
- 不做合规结果批量修复。

## 4. 设计原则

- 默认安全：没有管理员豁免记录时，`FAIL` 必须禁止导出。
- `WARNING` 是风险提示，不阻断导出，但必须在 manifest/report 中可见。
- 合规拦截发生在创建导出记录之前，避免产生无意义失败导出包。
- 重试路径复用默认 ZIP 导出逻辑，因此自动继承拦截规则。

## 5. 文件结构

### 5.1 修改文件

```text
e-commerce/src/main/java/com/snails/ecommerce/listing/application/ExportPackageService.java
e-commerce/src/test/java/com/snails/ecommerce/listing/application/ExportPackageServiceTest.java
docs/plans/合规FAIL资产导出拦截最小闭环实现计划.md
```

## 6. 任务拆解

### 任务 1：补充导出合规拦截测试

- [x] **步骤 1：补充 FAIL 阻断导出测试**

验证：

- 最终图片版本存在 `FAIL` 资产时返回 `TASK_STATUS_INVALID`。
- 不创建新的 `ExportPackage`。

- [x] **步骤 2：补充 WARNING 放行测试**

验证：

- `WARNING` 资产仍可导出。
- manifest 或合规报告中仍标记 `WARNING`。

- [x] **步骤 3：补充重试路径测试**

验证：

- 失败导出包重试时，如果当前最终图片版本存在 `FAIL` 资产，同样返回 `TASK_STATUS_INVALID`。
- 原失败导出包不被覆盖。

### 任务 2：实现导出合规拦截

- [x] **步骤 1：实现 `requireExportableAssets`**

建议逻辑：

```java
private void requireExportableAssets(List<ImageAsset> assets) {
    List<String> failedAssetIds = assets.stream()
            .filter(asset -> "FAIL".equals(asset.getComplianceStatus()))
            .map(ImageAsset::getAssetId)
            .toList();
    if (!failedAssetIds.isEmpty()) {
        throw new BusinessException(...);
    }
}
```

- [x] **步骤 2：在创建导出记录前调用拦截**

保证 `FAIL` 拦截不会产生新的 `ExportPackage`。

- [x] **步骤 3：业务文件 review 关卡**

`ExportPackageService.java` 属于业务逻辑文件，完成后先提交给用户 review。

### 任务 3：完整回归和计划收尾

- [x] **步骤 1：运行导出服务测试**

```powershell
cd e-commerce
.\gradlew.bat test --tests "*ExportPackageServiceTest"
```

- [x] **步骤 2：运行完整测试**

```powershell
cd e-commerce
.\gradlew.bat test
```

- [x] **步骤 3：更新本计划勾选状态**

每完成一个任务，同步更新本文件。

## 7. 验收清单

- [x] `FAIL` 图片资产阻止 ZIP 导出。
- [x] `FAIL` 图片资产阻止导出重试。
- [x] `FAIL` 拦截不创建新的导出记录。
- [x] `WARNING` 图片资产仍允许导出。
- [x] `WARNING` 状态保留在 manifest 或合规报告中。
- [x] API 不直接暴露 JPA 实体。
- [x] 完整 Gradle 测试输出 `BUILD SUCCESSFUL`。

## 8. 后续阶段入口

本阶段完成后，下一步建议进入：

- 管理员合规豁免。
- `WARNING` 人工确认流。
- Excel / Markdown / Word 可选导出。
- 前端归档导出页。
