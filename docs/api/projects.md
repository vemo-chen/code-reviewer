# 项目管理 API

## 1. 说明

项目管理接口用于维护项目维度配置。系统会根据项目的真实 GitLab URL 自动补全 `gitlab_project_id`，后续 webhook 运行时只按 `gitlab_project_id` 命中项目配置。

基础路径：

- `POST /api/projects`
- `PUT /api/projects/{id}`
- `GET /api/projects/{id}`
- `GET /api/projects`
- `DELETE /api/projects/{id}`
- `POST /api/projects/{id}/refresh`

## 2. 请求字段

- `projectName`：系统内自定义项目名称，必填
- `sourcePlatform`：来源平台，当前默认 `gitlab`，可不传
- `gitlabProjectUrl`：真实 GitLab 项目 URL，必填
- `aiReviewEnabled`：是否开启 AI 代码审查
- `gitlabNoteEnabled`：是否回写 GitLab Note
- `wecomNotifyEnabled`：是否开启企业微信通知
- `wecomWebhookUrl`：项目级企业微信 webhook，可选
- `promptContent`：项目级 Prompt 补充内容，可选
- `active`：项目是否启用

## 3. 返回字段

- `id`
- `projectKey`
- `projectName`
- `sourcePlatform`
- `gitlabProjectId`
- `gitlabProjectUrl`
- `aiReviewEnabled`
- `gitlabNoteEnabled`
- `wecomNotifyEnabled`
- `wecomWebhookUrl`
- `promptContent`
- `active`
- `createdAt`
- `updatedAt`

## 4. promptContent 使用建议

`promptContent` 用于补充项目专属规则，不建议重复公共规则。推荐只写：

- 项目硬性规范
- 项目特有架构约束
- 需要重点关注的模块与风险点
- 明确的忽略项

运行时 Prompt 顺序：

1. 系统指令
2. 公共 `review-rules.md`
3. 项目级 `promptContent`
4. 本次代码变更上下文
5. 输出 JSON 约束

## 5. 创建项目示例

`POST /api/projects`

```json
{
  "projectName": "MAS Core",
  "gitlabProjectUrl": "http://gitlab.example.com/group/mas-core",
  "aiReviewEnabled": true,
  "gitlabNoteEnabled": true,
  "wecomNotifyEnabled": true,
  "wecomWebhookUrl": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=project-key",
  "promptContent": "# 项目硬性规范\n- Bean 注入必须使用 @Resource\n- 不允许使用 @Data\n- Service 之间不允许互相注入\n- 业务方法中不允许出现魔法值，必须使用枚举表达业务语义",
  "active": true
}
```

响应示例：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "projectKey": "project:f1f689c442df4364bb88f7919b1493b0",
    "projectName": "MAS Core",
    "sourcePlatform": "gitlab",
    "gitlabProjectId": 1001,
    "gitlabProjectUrl": "http://gitlab.example.com/group/mas-core",
    "aiReviewEnabled": true,
    "gitlabNoteEnabled": true,
    "wecomNotifyEnabled": true,
    "wecomWebhookUrl": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=project-key",
    "promptContent": "# 项目硬性规范...",
    "active": true,
    "createdAt": "2026-04-08T04:00:00.000+00:00",
    "updatedAt": "2026-04-08T04:00:00.000+00:00"
  }
}
```

说明：创建时会自动调用 GitLab API，根据 `gitlabProjectUrl` 补全 `gitlabProjectId`。

## 6. 更新项目示例

`PUT /api/projects/{id}`

```json
{
  "projectName": "MAS Core Updated",
  "gitlabProjectUrl": "http://gitlab.example.com/group/mas-core-updated",
  "aiReviewEnabled": false,
  "gitlabNoteEnabled": false,
  "wecomNotifyEnabled": false,
  "promptContent": "请重点关注 DTO 边界和 Service 分层约束。",
  "active": true
}
```

说明：如果 `gitlabProjectUrl` 变化，系统会重新补全 `gitlabProjectId`。

## 7. 查询接口

### 7.1 查询项目详情

`GET /api/projects/{id}`

### 7.2 查询项目列表

`GET /api/projects`

当前按 `id desc` 返回。

## 8. 删除与刷新

### 8.1 删除项目

`DELETE /api/projects/{id}`

响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": "deleted"
}
```

### 8.2 刷新 GitLab 项目信息

`POST /api/projects/{id}/refresh`

用途：当 GitLab 项目 URL 变更或需要重新同步 `gitlabProjectId` 时使用。