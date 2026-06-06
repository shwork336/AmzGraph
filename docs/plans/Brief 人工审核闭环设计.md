# Brief 人工审核闭环设计

## 1. 目标

在现有 Listing 任务提交与详情查询闭环之后，补齐 Brief 人工审核能力，使运营可以查看 Brief 历史版本、基于最新版本创建人工修改版本，并批准最新版本进入后续图文生成阶段。

本阶段只建立审核闭环和生成阶段入口，不实现真实文案生成、图片生成、认证系统或前端页面。

## 2. 范围

本阶段实现：

- 查询任务的全部 Brief 版本。
- 查询任务的最新 Brief 版本。
- 基于最新 Brief 创建人工修改版本。
- 批准最新 Brief 版本。
- 记录 Brief 创建人、审批人和审批时间。
- 严格校验任务状态、版本归属和版本新旧关系。
- 批准后将任务推进到 `GENERATING`。
- 为上述服务和接口补充测试。

本阶段不实现：

- Brief 草稿自动保存。
- 基于历史版本创建分支。
- 撤回审批或重新打开已批准 Brief。
- 用户认证和从登录上下文自动获取操作者。
- 批准后的真实文案、图片生成。
- Brief 前端审核页面。

## 3. 应用服务边界

新增独立的 `BriefReviewService`，负责 Brief 查询、版本创建和审批。

现有 `ListingWorkflowService` 继续负责任务提交和任务详情查询，不继续承载 Brief 审核逻辑。这样可以避免工作流服务继续膨胀，并让审核规则集中在单一应用服务中。

`BriefReviewService` 依赖：

- `ListingTaskRepository`
- `ListingBriefVersionRepository`
- `IdGenerator`

## 4. 数据模型

### 4.1 ListingBriefVersion 新增字段

- `createdBy`：Brief 版本创建人。
- `approvedBy`：批准该版本的操作人，未批准时为空。
- `approvedAt`：批准时间，未批准时为空。

首个占位 Brief 的 `createdBy` 固定为 `SYSTEM`。人工修改版本的 `createdBy` 由接口请求显式提供。

审批信息记录在被批准的 Brief 版本上。`ListingTask` 继续只保存整体 `briefStatus`，不重复保存审批人和审批时间。

### 4.2 版本关系

- 每次人工修改都创建新的 `ListingBriefVersion`，不覆盖已有记录。
- 新版本的 `parentBriefVersionId` 指向修改前的最新版本。
- 只允许基于当前最新版本创建新版本。
- 当前阶段保持线性版本链，不支持版本树分支。

### 4.3 最新版本判定

仓储按 `createdAt` 降序查询最新版本。为避免时间相同导致结果不稳定，查询顺序需要增加 `briefVersionId` 降序作为稳定排序条件。

## 5. API 设计

### 5.1 查询全部 Brief 版本

```text
GET /api/v1/listing/{taskId}/briefs
```

返回该任务的 Brief 版本列表，按创建时间倒序排列。该接口只读，不触发状态变更。

### 5.2 查询最新 Brief

```text
GET /api/v1/listing/{taskId}/briefs/latest
```

返回任务当前最新 Brief 的完整审核字段。

### 5.3 创建人工修改版本

```text
POST /api/v1/listing/{taskId}/briefs
Content-Type: application/json
```

请求包含：

- `baseBriefVersionId`
- `createdBy`
- `targetAudience`
- `coreSellingPoints`
- `targetKeywords`
- `forbiddenClaims`
- `imageDirectionPrompts`
- `complianceNotes`

`baseBriefVersionId` 必须等于当前最新版本 ID。服务创建新版本并返回新版本详情。

### 5.4 批准 Brief

```text
POST /api/v1/listing/{taskId}/briefs/{briefVersionId}/approve
Content-Type: application/json
```

请求包含：

- `approvedBy`

被批准版本必须是任务当前最新版本。成功后返回批准后的 Brief 详情。

## 6. 状态规则

### 6.1 创建修改版本

创建人工修改版本前必须满足：

- 任务存在。
- 任务主状态为 `WAIT_BRIEF_APPROVE`。
- 任务 Brief 状态为 `WAIT_APPROVE`。
- 基础 Brief 存在并属于该任务。
- 基础 Brief 是当前最新版本。
- 基础 Brief 尚未批准。

任一状态条件不满足时返回 `TASK_STATUS_INVALID`。

### 6.2 批准版本

批准前必须满足：

- 任务存在。
- 任务主状态为 `WAIT_BRIEF_APPROVE`。
- 任务 Brief 状态为 `WAIT_APPROVE`。
- Brief 存在并属于该任务。
- Brief 是当前最新版本。
- Brief 尚未批准。

批准成功后：

- Brief `approved` 更新为 `true`。
- Brief 记录 `approvedBy` 和 `approvedAt`。
- 任务 `briefStatus` 更新为 `APPROVED`。
- 任务主状态更新为 `GENERATING`。
- `textStatus` 保持 `NOT_STARTED`。
- `imageStatus` 保持 `NOT_STARTED`。

重复审批严格拒绝，不做幂等成功处理。

## 7. 校验和错误

- 任务不存在：`TASK_NOT_FOUND`
- Brief 不存在或不属于任务：`INVALID_REQUEST`
- 任务状态不允许修改或审批：`TASK_STATUS_INVALID`
- 操作的不是最新版本：`TASK_STATUS_INVALID`
- `createdBy` 或 `approvedBy` 为空：`INVALID_REQUEST`
- 审核字段格式或请求体校验失败：`INVALID_REQUEST`

列表型字段通过 DTO 暴露为 `List<String>`，应用服务使用统一 JSON 序列化方式写入实体的 JSON 字符串字段，不由 Controller 手工拼接 JSON。

## 8. 一致性和事务

创建新版本和批准操作均使用事务。

批准操作需要在同一事务内完成 Brief 更新和任务状态更新，避免出现 Brief 已批准但任务仍等待审批的中间状态。

当前内部运营系统暂不引入乐观锁字段。通过“任务仍处于等待审批状态”和“操作版本仍为最新版本”的双重检查防止常规重复操作。后续出现真实并发编辑需求时，再增加实体版本号。

## 9. 测试范围

应用服务测试至少覆盖：

- 查询全部 Brief 版本并验证倒序。
- 查询最新 Brief。
- 基于最新版本成功创建新版本。
- 新版本正确记录父版本和 `createdBy`。
- 基于历史版本创建时被拒绝。
- 非等待审批状态下创建版本被拒绝。
- 成功批准最新版本。
- 批准后 Brief 审计字段正确。
- 批准后任务进入 `GENERATING` 和 `APPROVED`。
- 文案、图片状态仍为 `NOT_STARTED`。
- 批准历史版本被拒绝。
- 重复审批被拒绝。
- Brief 与任务不匹配时被拒绝。

Controller 测试至少覆盖：

- 四个新增接口的成功响应。
- 请求字段校验失败时返回统一错误结构。
- `TASK_NOT_FOUND`、`TASK_STATUS_INVALID` 的 HTTP 响应映射。

实体映射测试补充新增审计字段的持久化验证。

## 10. 实施节奏

按照仓库约定，基础 DTO、Repository 和实体字段可以成组实现。

涉及业务规则的 Java 文件必须逐个提交给用户 review：

1. `BriefReviewService`
2. `ListingTaskController` 中的 Brief 接口

每个业务文件 review 通过后再继续下一个业务文件。全部实现完成后运行：

```powershell
cd e-commerce
.\gradlew.bat test
```

完整测试必须输出 `BUILD SUCCESSFUL`。

## 11. 验收标准

- 运营可以读取任务的完整 Brief 历史和最新版本。
- 人工修改不会覆盖历史 Brief。
- 版本链通过 `parentBriefVersionId` 保持线性可追溯。
- 只有最新、未批准的 Brief 可以被修改或批准。
- 审批人、审批时间和版本创建人可追溯。
- 批准后任务进入 `GENERATING`，但不会误触发尚未实现的生成逻辑。
- 所有错误通过现有统一响应结构返回稳定错误码。
- 完整 Gradle 测试通过。
