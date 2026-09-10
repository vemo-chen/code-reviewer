# 审查记录告警等级与审查分数设计

## 1. 目标

在“审查记录”列表中新增“告警等级”和“审查分数”两列，并支持按这两个字段查询：

- 告警等级来自 `code_review_result.risk_level`。
- 审查分数来自 `code_review_result.final_score`。
- 字段为空时，列表显示 `--`。
- 不提供查询空告警等级或空审查分数记录的选项。
- 审查分数使用 `0-100` 的双滑块选择区间。

现有分页响应已经包含 `riskLevel` 和 `finalScore`，本次沿用现有字段名称和接口，不引入重复字段。

## 2. 用户界面

### 2.1 查询条件

在审查记录查询区域增加：

1. 告警等级下拉框
   - 低风险：`LOW`
   - 中风险：`MEDIUM`
   - 高风险：`HIGH`
   - 严重：`CRITICAL`
   - 可清空，清空时不传该查询条件。

2. 审查分数双滑块
   - 最小值 `0`，最大值 `100`，步长 `1`。
   - 默认值 `[0, 100]`，表示不限制分数。
   - 左右滑块共同形成闭区间。
   - 只有区间被调整为不是完整范围时，才向后端发送 `minScore` 和 `maxScore`。
   - 查询结果只包含有分数且满足区间条件的记录，因此 `finalScore IS NULL` 的记录不会被命中。
   - 需要显示当前最小值和最大值，避免用户无法确认选择范围。

点击查询时将页码重置为第 1 页；点击重置时恢复告警等级为空、分数区间 `[0, 100]`，并清理当前排序。

### 2.2 列表展示

在审查记录表格中增加两列：

- 告警等级：使用现有风险等级样式映射，值为空时显示 `--`。
- 审查分数：显示 `finalScore` 数值，值为空时显示 `--`。

建议将两列放在任务状态附近，便于同时查看任务处理状态和审查结论。列的排序能力保持不变，本需求不新增按告警等级或分数排序。

## 3. 前端接口契约

扩展 `ReviewTaskQueryParams`：

```text
riskLevel?: string
minScore?: number
maxScore?: number
```

审查记录请求示例：

```text
GET /api/dashboard/review-tasks
    ?pageNo=1
    &pageSize=10
    &riskLevel=HIGH
    &minScore=60
    &maxScore=79
```

默认分数区间 `[0, 100]` 不传分数参数，避免改变未使用筛选条件时的查询语义。

## 4. 后端查询设计

### 4.1 请求参数

在 `ReviewTaskQueryRequest` 和 `DashboardController.reviewTasks` 中增加：

```text
minScore: Integer
maxScore: Integer
```

控制器负责接收参数并传递给查询服务。查询服务对参数进行以下校验：

- 只能是 `0-100` 范围内的整数。
- `minScore` 与 `maxScore` 允许单独传递。
- 当两者都存在时，要求 `minScore <= maxScore`。
- 非法参数返回明确的参数错误，不执行模糊查询。

### 4.2 跨表筛选

审查任务主表为 `code_review_task`，告警等级和审查分数位于 `code_review_result`。沿用现有的“先查询关联任务 ID，再对任务分页”的结构：

1. 根据 `riskLevel` 查询 `code_review_result.risk_level`，收集对应 `task_id`。
2. 根据 `minScore`、`maxScore` 查询 `code_review_result.final_score`：
   - `minScore` 存在时增加 `final_score >= minScore`。
   - `maxScore` 存在时增加 `final_score <= maxScore`。
   - 传入任一分数条件都会自然排除 `final_score IS NULL`。
3. 将上述结果与日期、提交者等跨表筛选得到的任务 ID 取交集。
4. 将交集作为 `code_review_task.id` 条件，再执行原有权限、项目、任务状态、整改状态、标题条件和分页。

需要修正 `resolveTaskIdsForCrossTableFilters` 当前的条件合并方式：每个跨表条件都必须在已有任务 ID 集合上执行 `retainAll`，不能直接覆盖前一个条件的结果。无条件时继续返回 `null`，表示不追加任务 ID 条件；任一条件命中为空时返回空集合，直接返回空分页。

### 4.3 返回值与空值

分页响应继续使用：

```text
riskLevel: string | null
finalScore: number | null
```

后端不把 `null` 转换为字符串 `--`。空值展示属于前端表现层逻辑，确保接口数据类型稳定，详情页和其他消费者可以继续使用真实空值。

已有 `buildReviewTaskItem` 会从当前任务对应的最新结果填充这两个字段，无需改变字段来源。没有结果的任务返回 `null`。

## 5. 数据流

```text
查询表单
  -> buildQueryParams()
  -> GET /api/dashboard/review-tasks
  -> DashboardController
  -> ReviewTaskQueryRequest
  -> DashboardQueryService
  -> code_review_result 解析风险等级/分数任务 ID
  -> 与其它跨表条件取交集
  -> code_review_task 分页查询
  -> 组装最新事件和结果
  -> 返回 riskLevel/finalScore
  -> 表格显示，null 显示 --
```

## 6. 测试方案

### 6.1 后端接口测试

在 `DashboardControllerTest` 增加或扩展测试：

1. 默认分页返回告警等级和审查分数。
2. `riskLevel=HIGH` 只返回高风险记录。
3. `minScore=60&maxScore=79` 返回分数在闭区间内的记录。
4. 只传 `minScore` 时返回大于等于最小分数的记录。
5. 只传 `maxScore` 时返回小于等于最大分数的记录。
6. 分数筛选不会返回 `final_score` 为空的任务。
7. 同时传告警等级、分数、日期或提交者时，条件按交集处理。
8. `minScore > maxScore`、小于 `0` 或大于 `100` 时返回参数错误。
9. 没有结果的任务仍返回，且 `riskLevel`、`finalScore` 为 `null`。

测试数据至少包含：

- 高风险且分数在区间内的记录。
- 高风险但分数在区间外的记录。
- 低风险但分数在区间内的记录。
- 无结果记录。

### 6.2 前端验证

执行前端类型检查和构建，确认：

- 双滑块可拖动并显示当前区间。
- 查询和重置会正确传递或清理参数。
- 告警等级和审查分数列正确展示。
- 空值展示为 `--`。
- 移动端或窄窗口下查询区域和表格没有明显溢出。

## 7. 非目标

- 不增加数据库字段或迁移脚本。
- 不改变告警等级和审查分数的计算规则。
- 不新增按告警等级或审查分数排序。
- 不支持查询无告警等级或无审查分数的记录。
- 不重构为自定义 JOIN 分页 SQL；当前需求优先保持现有查询结构和权限逻辑。
