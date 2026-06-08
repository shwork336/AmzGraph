# 导出 Worker 领取锁最小闭环实现计划

> 本计划承接导出超时恢复最小闭环。目标是在 worker 扫描 `PENDING` 导出记录后，通过数据库条件更新完成原子领取，避免多实例部署时同一条导出记录被多个 worker 同时执行。

## 1. 当前基线

已完成：

- worker 可扫描 `PENDING` 导出记录并执行。
- worker 执行前可恢复超时 `PENDING` / `RUNNING` 记录。
- 手工接口可显式执行单条 `PENDING` 导出。
- 导出失败可重试。

当前不足：

- worker 先查询 `PENDING` 列表，再逐条执行。
- 多实例 worker 同时扫描时，可能拿到同一条 `PENDING` 记录。
- `PENDING -> RUNNING` 状态转换没有数据库条件保护。

## 2. 阶段目标

- 新增原子领取能力：
  - 仅当记录当前仍为 `PENDING` 时，才允许更新为 `RUNNING`。
  - 更新成功返回 1，失败返回 0。
- worker 只执行领取成功的记录。
- 领取失败不计入成功或失败，只跳过本条。
- 手工 `/run` 接口保持兼容。
- 完整测试通过。

## 3. 范围边界

本阶段做：

- 扩展 `ExportPackageRepository` 条件更新方法。
- 扩展 `ExportPackageService`：
  - worker 使用领取后执行入口。
  - 手工 `/run` 保持从 `PENDING` 执行。
- 扩展 `ExportPackageWorkerResult`，增加 claimed 统计。
- 扩展 `ExportPackageWorkerService`，只执行领取成功的记录。
- 补充仓储、服务、worker 测试。

本阶段不做：

- 不引入外部锁组件。
- 不新增锁持有人字段。
- 不新增心跳字段。
- 不改变 HTTP 响应结构。
- 不处理多实例下文件写入幂等的完整方案。

## 4. 任务拆解

### 任务 1：仓储原子领取

- [x] 新增条件更新 `PENDING -> RUNNING`。
- [x] 更新条件包含导出包 ID 和当前状态。
- [x] 仓储测试覆盖领取成功和非 PENDING 领取失败。

### 任务 2：服务执行边界

- [x] 新增 worker 领取并执行入口。
- [x] 已领取记录按 `RUNNING` 执行。
- [x] 手工 `/run` 仍支持 `PENDING`。

### 任务 3：Worker 批处理

- [x] worker 只执行领取成功的记录。
- [x] 领取失败时跳过本条。
- [x] 返回 claimed 统计。

### 任务 4：测试和回归

- [x] 仓储测试。
- [x] 服务测试。
- [x] Worker 测试。
- [x] 完整 Gradle 测试。

## 5. 验收清单

- [x] `PENDING` 记录可被原子领取为 `RUNNING`。
- [x] 非 `PENDING` 记录不能被领取。
- [x] worker 只执行领取成功的记录。
- [x] 领取失败不影响后续记录。
- [x] 手工 `/run` 仍可执行 `PENDING` 记录。
- [x] 完整测试输出 `BUILD SUCCESSFUL`。

## 6. 后续阶段入口

本阶段完成后，下一步建议进入：

- `startedAt/updatedAt` 时间字段增强。
- 导出取消人和取消原因审计。
- 前端归档导出页。
- 多实例文件写入幂等保护。
