# 后台导出 Worker 最小闭环实现计划

> 本计划承接导出任务异步化最小闭环。目标是在已有 `PENDING -> RUNNING -> SUCCEEDED/FAILED` 状态边界上，补齐后台扫描和执行入口，让前端只需创建导出记录，后台即可按批处理推进导出状态。

## 1. 当前基线

已完成：

- `POST /api/v1/listing/{taskId}/exports?format=ZIP` 可创建 `PENDING` 导出记录。
- `POST /api/v1/listing/export/{exportPackageId}/run` 可显式执行单条 `PENDING` 导出。
- 同步 ZIP/Markdown/Excel/Word 导出接口仍保持兼容。
- 导出列表支持查询、筛选和分页。

当前不足：

- 仍需要调用方显式触发 `/run`。
- 没有后台 worker 扫描 `PENDING` 记录。
- 没有批量执行入口，不能隔离单条失败后继续处理后续记录。

## 2. 阶段目标

- 仓储支持查询最早创建的 `PENDING` 导出记录。
- 新增后台 worker 服务：
  - 每批最多处理指定数量记录。
  - 按创建时间升序、导出包 ID 升序处理。
  - 单条失败不阻断后续记录。
  - 返回本批扫描数量、成功数量、失败数量。
- 新增调度适配器：
  - 默认关闭自动调度。
  - 通过 `listing.export-worker.enabled=true` 启用。
  - 周期参数默认 5000ms，可通过配置覆盖。
- 保留显式 `/run` 接口，便于人工补偿和测试。
- 完整测试通过。

## 3. 范围边界

本阶段做：

- 扩展 `ExportPackageRepository` 查询 `PENDING` 记录。
- 新增 `ExportPackageWorkerService`。
- 新增调度适配器 `ExportPackageWorkerScheduler`。
- 在应用入口启用 Spring Scheduling。
- 补充 repository、worker、scheduler 测试。

本阶段不做：

- 不引入外部消息队列。
- 不做分布式锁。
- 不做多实例并发领取防重。
- 不新增导出取消能力。
- 不改变现有导出 API 响应结构。

## 4. 任务拆解

### 任务 1：仓储支持扫描 PENDING

- [x] 新增按状态、创建时间、导出包 ID 升序查询方法。
- [x] 补充仓储排序测试。

### 任务 2：Worker 服务

- [x] 新增批量处理方法。
- [x] 校验批大小必须大于 0。
- [x] 成功执行后统计 succeeded。
- [x] 单条失败后统计 failed 并继续处理。

### 任务 3：调度适配器

- [x] 默认关闭自动调度。
- [x] 配置启用后周期调用 worker。
- [x] 测试配置启用时 bean 可加载。

### 任务 4：回归验证

- [x] 运行仓储测试。
- [x] 运行 worker 测试。
- [x] 运行完整 Gradle 测试。

## 5. 验收清单

- [x] Worker 能扫描最早的 `PENDING` 导出记录。
- [x] Worker 能执行本批所有可执行记录。
- [x] Worker 遇到单条失败时继续处理后续记录。
- [x] 非法批大小返回 `INVALID_REQUEST`。
- [x] 默认配置不自动启用调度 bean。
- [x] 开启配置后调度 bean 可加载。
- [x] 完整测试输出 `BUILD SUCCESSFUL`。

## 6. 后续阶段入口

本阶段完成后，下一步建议进入：

- 导出任务取消能力。
- PENDING/RUNNING 超时恢复。
- 多实例导出 worker 领取锁。
- 前端归档导出页。
