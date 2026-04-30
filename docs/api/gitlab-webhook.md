# GitLab Webhook API

## 1. 入口

`POST /api/webhooks/gitlab`

请求头：

- `X-Gitlab-Token`：必须与项目配置中的 GitLab Token 一致

说明：当前版本使用项目级 GitLab Token 完成 webhook 校验和 GitLab API 调用。

## 2. 支持的事件

- `Merge Request Hook`
- `Push Hook`

## 3. 当前处理逻辑

### 3.1 Merge Request Hook

- 拉取 Merge Request changes
- 调用大模型审查
- 根据项目配置决定是否回写 MR Note
- 根据项目配置决定是否发送企业微信通知

### 3.2 Push Hook

- 只审查本次 push 的最新提交，即 `after` 对应的 commit
- 拉取该 commit 的 diff
- 根据项目配置决定是否回写 Commit Note
- 根据项目配置决定是否发送企业微信通知

## 4. 项目维度命中规则

Webhook 到来后会先根据 payload 中的 `project.id` 查项目配置：

- 找不到项目：记录事件并标记 `IGNORED`
- 项目停用：记录事件并标记 `IGNORED`
- 项目关闭 AI 审查：记录事件并标记 `IGNORED`
- 只有命中有效项目且开启 AI 审查时才创建任务

## 5. 返回语义

- 成功接收并处理：HTTP 200
- 业务校验失败：HTTP 400

说明：对于未纳管项目，系统会接收 webhook 但忽略执行，以避免 GitLab 反复重试。
