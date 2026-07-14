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

- 同一个 MR 生命周期固定对应一条 event 和最多一条 `MR_REVIEW` task
- 按目标分支匹配审查配置；新 head 复用并重置同一 task，相同 head 只更新生命周期状态
- 拉取最新 Merge Request changes 并生成一份聚合结果
- 根据项目配置决定是否回写 MR Note
- 根据项目配置决定是否发送企业微信通知

### 3.2 Push Hook

- 一次 push webhook 固定对应一条 event 和最多一条 `PUSH_REVIEW` task
- 使用 GitLab Compare `before..after&straight=true` 审查本次 push 的净变化，不按 commits 拆 task
- MR 审查成功且合并 SHA 证据匹配时，目标分支 push 整体忽略；无法确认或 MR 未成功时回退审查完整 range
- 多个内部 semantic batch 只持久化一份结果
- 有问题时只向 `after` SHA 回写一条汇总 Note，正文包含 push range 与问题位置；无问题不回写 Note
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
