# 审查失败原因回写详情设计

## 背景

审查任务在执行失败时，任务表 `code_review_task` 已经持久化了 `errorCode` 和 `errorMessage`。当前审查详情接口只返回通用任务信息，没有把失败原因显式带回前端，导致 `FAILED` 记录在详情页里只看到失败状态，看不到为什么失败。

## 目标

1. 让 `FAILED` 状态的审查记录在详情页直接看到失败原因。
2. 保留 `errorCode` / `errorMessage` 的技术信息，便于排障。
3. 前端展示要更易读，不直接把底层异常当作唯一文案。

## 非目标

1. 不新增失败事件表或失败历史表。
2. 不改动任务失败写库逻辑。
3. 不把所有异常统一折叠成一个通用文案。

## 设计

### 后端数据来源

详情数据以 `code_review_task` 为准：

- `errorCode`
- `errorMessage`
- `status`

当任务状态不是 `FAILED` 时，这两个字段仍然可以透出，但前端默认不展示。

### 详情接口

`GET /api/dashboard/review-tasks/{taskId}` 增加字段：

- `errorCode`
- `errorMessage`
- `failureSummary`，由后端按 `errorCode` 生成更易懂的摘要文案

建议摘要规则保持分层：

- `LLM_IO_ERROR` -> 模型服务连接失败或超时
- `LLM_API_ERROR` -> 模型服务返回错误响应
- `LLM_EMPTY_RESPONSE` -> 模型服务返回空内容
- `GITLAB_*` -> GitLab 调用失败
- `REVIEW_*` -> 审查结果处理失败
- `TASK_*` / `PROJECT_*` -> 任务或项目配置失败
- 其他情况 -> 审查执行失败

`failureSummary` 只做展示，不替代原始字段。

### 前端展示

在审查详情页中新增“失败原因”区域，仅在 `detail.status === "FAILED"` 时显示。

显示顺序建议为：

1. `failureSummary`
2. `errorCode`
3. `errorMessage`

页面默认只展示摘要和原始错误信息的简洁版本，必要时再提供展开查看完整技术信息的区域。

### 文案原则

- 对用户友好，但不抹掉错误类型差异。
- `LLM_IO_ERROR` 和 `LLM_API_ERROR` 必须保留区分。
- 原始 `errorMessage` 作为排障信息保留，不做二次改写。

## 实现范围

后端：

- `ReviewTaskDetailResponse` 增加失败字段
- `DashboardQueryService#getReviewTaskDetail` 填充失败字段
- 必要时增加一个失败原因映射方法

前端：

- `ReviewListView.vue` 详情面板增加失败原因展示
- 对失败状态做条件渲染
- 保留现有详情布局，不改列表主结构

## 测试

后端至少补一条详情接口测试：

- `FAILED` 任务返回 `errorCode`
- `FAILED` 任务返回 `errorMessage`
- `FAILED` 任务返回 `failureSummary`

前端至少验证：

- 非失败任务不显示失败原因区域
- 失败任务显示摘要和技术详情

## 风险与约束

1. `errorMessage` 可能偏技术化，因此前端必须优先展示摘要。
2. 失败原因分类不要做成大而全的规则引擎，先覆盖现有已知错误码即可。
3. 现有数据里的 `errorMessage` 可能为空，页面要能兜底显示 `--`。
