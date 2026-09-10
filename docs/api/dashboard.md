# Dashboard API

## 1. 审查任务分页

`GET /api/dashboard/review-tasks?pageNo=1&pageSize=10&riskLevel=HIGH&minScore=60&maxScore=79`

可选查询参数：

- `riskLevel`：`LOW`、`MEDIUM`、`HIGH` 或 `CRITICAL`
- `minScore`：审查分数下限，闭区间，取值 `0-100`
- `maxScore`：审查分数上限，闭区间，取值 `0-100`

`minScore` 和 `maxScore` 可以单独传递，同时传递时要求 `minScore <= maxScore`。传递任一分数条件时，不返回审查分数为空的记录。

返回字段包含：

- `taskId`
- `projectId`
- `projectName`
- `targetId`
- `targetTitle`
- `status`
- `retryCount`
- `operatorName`
- `riskLevel`
- `finalScore`
- `summary`
- `errorCode`
- `errorMessage`
- `failureSummary`

## 2. 项目统计

`GET /api/dashboard/project-stats`

返回字段包含：

- `totalProjects`
- `totalTasks`
- `highRiskTasks`
- `projects[]`

## 3. 开发者统计

`GET /api/dashboard/developer-stats`

返回字段包含：

- `totalDevelopers`
- `totalTasks`
- `highRiskTasks`
- `developers[]`

## 4. 评分统计

`GET /api/dashboard/score-stats?startDate=yyyy-MM-dd&endDate=yyyy-MM-dd`

当前用于统计指定时间区间内的平均分，统计口径为 `code_review_result.final_score`。

返回字段包含：

- `startDate`
- `endDate`
- `averageScore`
- `projectScores[]`
- `developerScores[]`
