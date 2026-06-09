# 前端 Brief 审核页最小闭环实现计划

> 本计划承接前端任务详情总览页最小闭环。目标是补齐运营工作台中的 Brief 审核页，让运营能查看 Brief 版本历史、基于最新版本创建人工修改版本，并批准最新 Brief 推进任务到生成阶段。

## 1. 当前基线

已完成：

- 后端 Brief 历史查询接口 `GET /api/v1/listing/{taskId}/briefs`。
- 后端最新 Brief 查询接口 `GET /api/v1/listing/{taskId}/briefs/latest`。
- 后端人工创建 Brief 版本接口 `POST /api/v1/listing/{taskId}/briefs`。
- 后端批准 Brief 接口 `POST /api/v1/listing/{taskId}/briefs/{briefVersionId}/approve`。
- 前端任务详情页 `/tasks/:taskId`。

当前不足：

- 前端没有 Brief 审核页。
- 任务详情页的 Brief 审核入口只是占位提示。
- 运营无法在前端查看完整 Brief 列表、编辑最新 Brief 并批准进入生成阶段。

## 2. 阶段目标

- 新增路由：
  - `/tasks/:taskId/brief`
- 前端 API client 支持：
  - 查询 Brief 历史。
  - 查询最新 Brief。
  - 创建人工修改版本。
  - 批准 Brief。
- Brief 审核页展示：
  - 最新 Brief 详情。
  - Brief 版本历史。
  - 列表字段编辑区。
  - 创建人、批准人、批准时间和创建时间。
- 页面提供操作：
  - 从最新 Brief 载入编辑表单。
  - 保存为新版本。
  - 批准最新 Brief。
  - 返回任务详情。
- 前端构建和后端测试通过。

## 3. 范围边界

本阶段做：

- 前端 Brief 审核页。
- 前端路由和任务详情页入口调整。
- Brief 查询、编辑、创建版本、批准的基础加载态和错误提示。

本阶段不做：

- 不实现真实大模型 Brief 再生成。
- 不实现字段级差异对比。
- 不实现多人协同编辑。
- 不新增后端接口。
- 不自动触发文案或图片生成。

## 4. 任务拆解

### 任务 1：API 类型和 client

- [x] 定义 `BriefVersionResponse` 类型。
- [x] 定义创建 Brief 版本输入类型。
- [x] 增加查询 Brief 历史 API。
- [x] 增加查询最新 Brief API。
- [x] 增加创建 Brief 版本 API。
- [x] 增加批准 Brief API。

### 任务 2：路由和入口

- [x] 新增 `/tasks/:taskId/brief` 路由。
- [x] 任务详情页 Brief 审核按钮跳转到新页面。
- [x] Brief 页提供返回任务详情入口。

### 任务 3：Brief 审核页面

- [x] 展示最新 Brief 详情。
- [x] 展示 Brief 版本历史。
- [x] 提供列表字段编辑表单。
- [x] 支持保存为新版本。
- [x] 支持批准最新 Brief。
- [x] 处理加载态和错误提示。

### 任务 4：验证

- [x] 前端 `npm run build` 通过。
- [x] 后端 `.\gradlew.bat test` 通过。

## 5. 验收清单

- [x] `/tasks/:taskId/brief` 可打开。
- [x] 页面能展示最新 Brief。
- [x] 页面能展示 Brief 历史。
- [x] 页面能基于最新 Brief 保存人工修改版本。
- [x] 页面能批准最新 Brief。
- [x] 任务详情页可进入 Brief 审核页。
- [x] 前端构建输出成功。
- [x] 后端完整测试输出 `BUILD SUCCESSFUL`。

## 6. 后续阶段入口

本阶段完成后，下一步建议进入：

- 前端文案版本页。
- 前端图片版本页。
- 前端合规报告页。
