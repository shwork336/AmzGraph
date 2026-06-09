# 前端 API 错误解析健壮化最小闭环实现计划

> 本计划承接前端运行时冒烟验证。目标是让前端 API 客户端在后端返回非 JSON、空响应、网关错误或网络失败时，仍能给页面稳定可读的错误消息，避免暴露 `Unexpected token` 等底层解析异常。

## 1. 当前基线

已完成：

- 前端工作台主要页面和任务子页面已经完成浏览器冒烟。
- 页面统一使用 `errorMessage()` 或 `message.error()` 展示错误。
- API 客户端集中在 `frontend/src/api/client.ts`。

当前不足：

- `request()` 和 `multipartRequest()` 直接调用 `response.json()`。
- 后端未启动、代理返回 HTML、204/空响应或网关错误时，会抛出不稳定的 JSON 解析错误。
- 普通 JSON 请求和 multipart 请求重复了响应解析逻辑。

## 2. 阶段目标

- API 响应解析集中化。
- 非 JSON 或空响应返回稳定错误文案。
- HTTP 非 2xx 和业务 `success=false` 都能保留后端错误信息。
- 普通 JSON 请求和 multipart 请求复用同一套错误处理。

## 3. 范围边界

本阶段做：

- 调整前端 API 客户端响应解析。
- 保持页面调用方式不变。
- 前端构建和后端测试回归。

本阶段不做：

- 不新增前端页面。
- 不修改后端错误响应结构。
- 不引入前端测试框架。

## 4. 任务拆解

- [x] 抽取统一发送和解析函数。
- [x] 兼容后端业务错误、HTTP 错误、非 JSON 响应和空响应。
- [x] 前端 `npm.cmd run build` 通过。
- [x] 后端 `.\gradlew.bat test --console=plain` 通过。

## 5. 验收清单

- [x] `request()` 与 `multipartRequest()` 无重复 JSON 错误处理。
- [x] 非 JSON 错误不会暴露 JSON parse 异常。
- [x] 前端构建通过。
- [x] 后端完整测试通过。
