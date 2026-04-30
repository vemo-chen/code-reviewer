# Dashboard API

## 1. 审查任务分页

`GET /api/dashboard/review-tasks?pageNo=1&pageSize=10`

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
- `summary`

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