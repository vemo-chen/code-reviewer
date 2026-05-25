<template>
  <div class="manual-page">
    <header class="manual-hero">
      <div class="manual-hero__content">
        <p class="manual-kicker">Code Reviewer</p>
        <h1>用户手册</h1>
        <p class="manual-lead">
          面向使用者的操作说明。这里不讲底层实现，只帮助你快速完成模型配置、项目接入、审查查看和整改闭环。
        </p>
        <div class="manual-hero__actions">
          <a href="#webhook" class="hero-link">从 GitLab Webhook 开始</a>
          <a href="#reviews" class="hero-link hero-link--secondary">查看审查记录使用方式</a>
          <button type="button" class="hero-link hero-link--ghost" @click="openSystemHome">回到系统首页</button>
        </div>
      </div>
      <div class="manual-hero__card">
        <div class="version-card-head">
          <div class="version-card-title-group">
            <h3 class="version-card-title">版本说明</h3>
            <span class="current-version">当前版本：V1.2</span>
          </div>
          <button type="button" class="version-history-link" @click="historyDialogVisible = true">查看历史版本</button>
        </div>
        <ul class="version-list">
          <li>
            <strong>V1.2</strong>
            <ol>
              <li>项目管理新增“更多 -&gt; 自定义审查”，支持按提交时间区间批量审查 commit，并提供“跳过已审查 / 强制重审”两种处理方式。</li>
              <li>审查记录补齐提交时间与任务创建时间，并按提交时间倒序展示，便于按真实代码提交顺序回看任务。</li>
              <li>操作入口进一步收敛到轻量“更多”菜单，自定义审查仅对管理员和项目 Owner 可见。</li>
            </ol>
          </li>
        </ul>
      </div>
    </header>

    <div class="manual-shell">
      <aside class="manual-nav">
        <div class="manual-nav__title">目录</div>
        <nav>
          <a v-for="item in sections" :key="item.id" :href="`#${item.id}`" class="manual-nav__item">
            {{ item.label }}
          </a>
        </nav>
      </aside>

      <main class="manual-content">
        <section id="overview" class="manual-section">
          <div class="section-head">
            <p class="section-index">01</p>
            <div>
              <h2>开始前先了解这套系统</h2>
              <p>Code Reviewer 是一套基于大模型的自动化代码审查平台，用于在代码提交或合并请求阶段快速完成智能审查。</p>
              <p>系统主要围绕“自动审查”和“整改闭环”两件事展开。</p>
            </div>
          </div>
          <div class="content-card">
            <h4>功能说明</h4>
            <ul class="manual-list">
              <li>🚀 多模型支持 <span style="color:red;">（当前版本仅支持DeepSeek、其他厂商需要做一定适配）</span> </li>
              <ul>
                <li>兼容 DeepSeek、ZhipuAI、OpenAI、Anthropic、通义千问 和 Ollama，想用哪个就用哪个。</li>
              </ul>
              <li>🎭 项目级别的配置</li>
              <ul>
                <li>支持项目级 Prompt、模型、通知、Webhook Token 、审查分支等独立配置。</li>
              </ul>
              <li>📅 CR评审功能、整改闭环</li>
              <ul>
                <li>支持项目级 审查与整改闭环：支持审查结果查看、整改提交、Owner 复审和通过 / 驳回。</li>
              </ul>
              <li>📢 消息即时推送</li>
              <ul>
                <li>审查结果一键直达 企业微信</li>
              </ul>
              <li>📊 可视化 Dashboard</li>
              <ul>
                <li>集中展示所有 Code Review 记录，项目统计、开发者统计</li>
              </ul>
            </ul>
            <h4>职责说明</h4>
            <ul class="manual-list">
              <li>🤵 管理员通常负责配置大模型、创建项目、分配成员、指定项目 Owner。</li>
              <li>🤵 当然，你也可以创建项目，作为项目 Owner，分配成员，负责审核整改结果，决定审查通过或驳回。</li>
              <li>🤵 项目成员负责查看审查结果、修改代码并提交复审。</li>
            </ul>
            <div class="manual-note">
              首次使用时，建议先完成“大模型配置”和“项目配置”，再到 GitLab 项目中接入 Webhook。
            </div>
          </div>
        </section>

        <section id="webhook" class="manual-section">
          <div class="section-head">
            <p class="section-index">02</p>
            <div>
              <h2>配置 GitLab 代码库 Webhook</h2>
              <p>让 GitLab 在 push 代码或更新 Merge Request 时，自动通知 Code Reviewer。</p>
            </div>
          </div>
          <div class="content-card">
            <ol class="manual-steps">
              <li>创建Access Token</li>
              <ul>
                <li>方法一：在 GitLab 个人设置中，创建一个 Personal Access Token。</li>
                <li>方法二：在 GitLab 项目设置中，创建Project Access Token</li>
              </ul>
              <li>配置 Webhook</li>
              <ul>
                <li>进入 GitLab 对应项目，打开 <strong>Settings / Integrations</strong>。</li>
                <li>在 URL 填写系统提供的 Webhook 地址，一般是 <code>/api/webhooks/gitlab</code>; 当前环境是<code>http://10.12.8.132:8765/api/webhooks/gitlab</code>。</li>
                <li>在 Secret Token 中填写系统要求的<strong> Access Token</strong>。若项目已配置项目级 Token，则优先使用项目级 Token。</li>
                <li>勾选你希望触发的事件，至少建议开启 <strong>Push events</strong> 和 <strong>Merge request events</strong>。</li>
                <div class="webhook-image-block">
                  <el-image
                      :src="gitlabSettingImage"
                      :preview-src-list="[gitlabSettingImage]"
                      preview-teleported
                      fit="contain"
                      alt="GitLab Webhook 配置"
                      class="webhook-image"
                  />
                </div>
              </ul>

            </ol>
            <div class="manual-note">
              如果项目已接入但始终没有生成审查记录，优先检查 Webhook 地址、Token、审查分支和 AI 审查开关是否配置正确。
            </div>
          </div>
        </section>

        <section id="llm" class="manual-section">
          <div class="section-head">
            <p class="section-index">03</p>
            <div>
              <h2>创建 / 配置大模型</h2>
              <p>先在“大模型管理”中准备可用模型，项目才能绑定并使用。</p>
            </div>
          </div>
          <div class="content-card">
            <ol class="manual-steps">
              <li>进入 <strong>大模型管理</strong> 页面，点击新建模型。</li>
              <li>填写配置名称、提供商、协议类型、Base URL、API Key 和模型标识。</li>
              <li>根据使用范围设置为公共模型或项目私有模型。</li>
              <li>如为项目私有模型，需要指定维护项目；维护项目 Owner 或管理员才能继续管理。</li>
              <li>保存前建议先测试连接，确认模型可用。</li>
            </ol>
            <div class="manual-note">
              已被项目绑定的模型不能直接删除；默认模型同一时间只允许存在一条。
            </div>
          </div>
        </section>

        <section id="project" class="manual-section">
          <div class="section-head">
            <p class="section-index">04</p>
            <div>
              <h2>创建 / 配置项目</h2>
              <p>项目是系统的业务承载单元，Webhook、Prompt、模型、模板、成员和通知都在项目维度配置。</p>
            </div>
          </div>
          <div class="content-card">
            <ol class="manual-steps">
              <li>进入 <strong>项目管理</strong> 页面，点击新建项目。</li>
              <li>填写项目名称和 GitLab 项目 URL，系统会自动解析 GitLab 项目信息。</li>
              <li>选择参与审查的项目分支（新建时请点击刷新按钮，系统将根据GitLab URL 和 token 自动拉取分支）</li>
              <li>指定项目 Owner。项目 Owner 负责审核整改结果。</li>
              <li>配置项目成员。项目成员拥有查看本项目数据和提交复审的权限。</li>
              <li>配置项目模板。项目模板用于区分 Java 后端、前端、Go、通用等不同技术栈项目的默认审查策略。</li>
              <li>打开 <strong>AI 审查</strong> 开关，并在同一卡片里选择项目使用的模型。</li>
              <li>按需要设置 GitLab Note 回写、企业微信通知、项目 Prompt、项目级 Webhook Token。</li>
              <li>保存后，再回到 GitLab 项目中配置 Webhook。</li>
            </ol>
            <div class="manual-note">
              普通用户创建项目时，自己会自动成为该项目成员和 Owner；管理员创建项目时需要主动指定 Owner 和成员。
            </div>
          </div>
        </section>

        <section id="usage" class="manual-section">
          <div class="section-head">
            <p class="section-index">05</p>
            <div>
              <h2>日常使用流程</h2>
              <p>完成模型、项目和 Webhook 配置后，系统会自动进入审查流程。</p>
            </div>
          </div>
          <div class="content-card">
            <ol class="manual-steps">
              <li>开发者 push 代码，或创建 / 更新 Merge Request。</li>
              <li>GitLab 触发 Webhook 到系统。</li>
              <li>系统自动创建审查任务，并调用大模型完成分析。</li>
              <li>审查结果进入审查记录，并按项目配置决定是否回写 GitLab、是否推送企业微信。</li>
              <li>若存在问题，任务进入 <strong>待修复</strong>，项目成员修复后提交复审。</li>
              <li>项目 Owner 或管理员复审，决定通过或驳回。</li>
            </ol>
          </div>
        </section>

        <section id="dashboard" class="manual-section">
          <div class="section-head">
            <p class="section-index">06</p>
            <div>
              <h2>看板使用说明</h2>
              <p>看板适合快速了解近期项目活跃度、审查情况和整体趋势。</p>
            </div>
          </div>
          <div class="content-card">
            <ul class="manual-list">
              <li>右上角可切换时间范围，例如近一周、近两周、近一月或自定义时间。</li>
              <li>主要指标通常用于观察项目活跃数、审查量、平均得分等整体情况。</li>
              <li>图表数据会按当前用户有权限访问的项目范围自动过滤。</li>
              <li>如果你只负责部分项目，看板展示的也会是当前可见项目的数据。</li>
            </ul>
          </div>
        </section>

        <section id="llm-management" class="manual-section">
          <div class="section-head">
            <p class="section-index">07</p>
            <div>
              <h2>大模型管理使用说明</h2>
              <p>这个页面主要面向管理员和模型维护者。</p>
            </div>
          </div>
          <div class="content-card">
            <ul class="manual-list">
              <li>可查看模型配置列表、启停状态、范围类型和维护项目。</li>
              <li>常用操作包括编辑、启用 / 停用、测试连接和删除。</li>
              <li>更多操作已统一收敛到轻量菜单，保持操作栏简洁。</li>
              <li>项目私有模型通常只允许维护项目的 Owner 或管理员继续管理。</li>
            </ul>
          </div>
        </section>

        <section id="project-management" class="manual-section">
          <div class="section-head">
            <p class="section-index">08</p>
            <div>
              <h2>项目管理使用说明</h2>
              <p>项目管理页用于维护项目基础信息、成员、Owner、模型、模板和通知配置。</p>
            </div>
          </div>
          <div class="content-card">
            <ul class="manual-list">
              <li>查询区支持按项目名称、GitLab URL、AI 审查状态和企业微信通知状态筛选。</li>
              <li>编辑和删除仍在行内操作栏；“更多”菜单用于放置低频管理动作。</li>
              <li><strong>更多 -&gt; 自定义审查</strong> 可按提交时间区间批量审查 commit，并支持“跳过已审查 / 强制重审”两种模式。</li>
              <li>自定义审查入口仅管理员和项目 Owner 可见；普通项目成员不可发起。</li>
              <li>编辑项目时，可继续维护成员、Owner、模型、模板、Prompt 和通知配置。</li>
            </ul>
          </div>
        </section>

        <section id="reviews" class="manual-section">
          <div class="section-head">
            <p class="section-index">09</p>
            <div>
              <h2>审查记录使用说明</h2>
              <p>审查记录是最常用的页面，用于查看 AI 审查结果、整改状态和复审过程。</p>
            </div>
          </div>
          <div class="content-card">
            <ul class="manual-list">
              <li>可按项目、任务状态、提交时间范围、提交者和提交信息等条件筛选。</li>
              <li>列表默认按提交时间倒序展示，便于优先查看最新提交。</li>
              <li>列表中可看到任务状态、整改状态、提交分支、提交者、提交时间等核心信息。</li>
              <li>打开详情后，可同时查看提交时间与任务创建时间，避免混淆“代码提交”与“系统建任务”的时间点。</li>
              <li>项目成员可提交复审；项目 Owner 和管理员可执行审查通过或驳回。</li>
            </ul>
            <div class="manual-note">
              手动批量审查场景下，提交者展示的是实际 commit 提交人；自定义审查发起人只作为后台追踪信息，不会覆盖提交者显示。
            </div>
          </div>
        </section>
      </main>
    </div>

    <el-dialog v-model="historyDialogVisible" title="历史版本" width="620px" destroy-on-close class="version-history-dialog">
      <ul class="version-list version-list--dialog">
        <li>
          <strong>V1.1</strong>
          <ol>
            <li>新增深度审查能力，自动扩展 Diff 周边上下文，帮助识别需要结合完整方法或组件逻辑才能判断的问题。</li>
            <li>审查记录支持展示当前代码和建议修改代码，并提供类编辑器样式的代码高亮效果。</li>
            <li>上下文识别范围扩展到 Java、前端、Python、C++ 等常见项目类型。</li>
          </ol>
        </li>
        <li>
          <strong>V1.0</strong>
          <ol>
            <li>支持大模型配置管理，可维护模型提供商、模型参数、范围类型和启停状态。</li>
            <li>支持项目配置、GitLab Webhook 接入、成员管理、AI 审查开关和通知回写配置。</li>
            <li>提供审查记录、整改复审闭环和看板统计，便于跟踪项目审查质量和处理进度。</li>
          </ol>
        </li>
      </ul>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import gitlabSettingImage from "../../assets/images/gitlab-setting.png";

const historyDialogVisible = ref(false);

const sections = [
  { id: "overview", label: "开始前先了解这套系统" },
  { id: "webhook", label: "配置 GitLab 代码库 Webhook" },
  { id: "llm", label: "创建 / 配置大模型" },
  { id: "project", label: "创建 / 配置项目" },
  { id: "usage", label: "日常使用流程" },
  { id: "dashboard", label: "看板使用说明" },
  { id: "llm-management", label: "大模型管理使用说明" },
  { id: "project-management", label: "项目管理使用说明" },
  { id: "reviews", label: "审查记录使用说明" }
];

const openSystemHome = () => {
  window.open("/", "_blank", "noopener,noreferrer");
};
</script>

<style scoped>
.manual-page {
  min-height: 100vh;
  padding: 32px 32px 56px;
  background:
      radial-gradient(circle at top right, rgba(255, 196, 118, 0.18), transparent 28%),
      linear-gradient(180deg, #faf7f1 0%, #f5f0e7 100%);
  color: #43352b;
}

.manual-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, 0.75fr);
  gap: 24px;
  align-items: stretch;
  margin: 0 auto 28px;
  max-width: 1360px;
}

.manual-hero__content,
.manual-hero__card {
  border-radius: 28px;
  background: rgba(255, 252, 247, 0.94);
  box-shadow: 0 24px 80px rgba(97, 78, 53, 0.08);
  border: 1px solid rgba(205, 190, 165, 0.42);
}

.manual-hero__content {
  padding: 34px 38px;
}

.manual-kicker {
  margin: 0 0 12px;
  color: #cb7a13;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.manual-hero h1 {
  margin: 0;
  color: #2f231d;
  font-size: 42px;
  line-height: 1.08;
  font-weight: 900;
  letter-spacing: -0.04em;
}

.manual-lead {
  max-width: 760px;
  margin: 18px 0 0;
  color: rgba(67, 53, 43, 0.78);
  font-size: 16px;
  line-height: 1.8;
}

.manual-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 28px;
}

.hero-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: linear-gradient(135deg, #f39b22 0%, #dc7d07 100%);
  color: #fffdf8;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.hero-link:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 28px rgba(220, 125, 7, 0.22);
}

.hero-link--secondary {
  background: rgba(255, 166, 0, 0.12);
  color: #b96800;
  border-color: rgba(230, 162, 60, 0.32);
  box-shadow: none;
}

.hero-link--ghost {
  background: transparent;
  color: #705646;
  border-color: rgba(191, 172, 143, 0.46);
  box-shadow: none;
}

.manual-hero__card {
  display: grid;
  gap: 16px;
  padding: 28px;
}

.version-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.version-card-title-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.current-version {
  padding: 2px 8px;
  border: 1px solid rgba(230, 162, 60, 0.22);
  border-radius: 999px;
  background: rgba(255, 247, 230, 0.72);
  color: #b96800;
  font-size: 11px;
  font-weight: 800;
  line-height: 18px;
  white-space: nowrap;
}

.version-card-title {
  margin: 0;
  color: #2f231d;
  font-size: 20px;
  font-weight: 800;
}

.version-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.version-list > li {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 247, 232, 0.9), rgba(255, 252, 247, 0.9));
  border: 1px solid rgba(230, 162, 60, 0.16);
}

.version-list strong {
  display: block;
  color: var(--cr-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.version-list ol {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
}

.version-list ol li {
  color: #2f231d;
  font-size: 13px;
  line-height: 1.55;
}

.version-history-link {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--cr-primary);
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.version-history-link:hover,
.version-history-link:focus-visible {
  color: var(--cr-primary-deep);
}

.version-list--dialog {
  gap: 14px;
}

:deep(.version-history-dialog .el-dialog__title) {
  color: #2f231d;
  font-weight: 800;
}

.manual-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 24px;
  max-width: 1360px;
  margin: 0 auto;
}

.manual-nav {
  position: sticky;
  top: 32px;
  align-self: start;
  padding: 22px 18px;
  border-radius: 24px;
  background: rgba(255, 252, 247, 0.9);
  box-shadow: 0 16px 48px rgba(97, 78, 53, 0.08);
  border: 1px solid rgba(205, 190, 165, 0.42);
}

.manual-nav__title {
  margin-bottom: 14px;
  color: #cb7a13;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.manual-nav nav {
  display: grid;
  gap: 8px;
}

.manual-nav__item {
  display: block;
  padding: 10px 12px;
  border-radius: 14px;
  color: #5f4a3e;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  text-decoration: none;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.manual-nav__item:hover {
  background: rgba(255, 166, 0, 0.1);
  color: #cb7a13;
  transform: translateX(2px);
}

.manual-content {
  display: grid;
  gap: 22px;
}

.manual-section {
  scroll-margin-top: 28px;
}

.section-head {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.section-index {
  min-width: 42px;
  margin: 2px 0 0;
  color: rgba(203, 122, 19, 0.78);
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.section-head h2 {
  margin: 0;
  color: #2f231d;
  font-size: 28px;
  font-weight: 900;
  letter-spacing: -0.03em;
}

.section-head p {
  margin: 8px 0 0;
  color: rgba(67, 53, 43, 0.72);
  font-size: 14px;
  line-height: 1.8;
}

.content-card {
  padding: 26px 28px;
  border-radius: 24px;
  background: rgba(255, 252, 247, 0.92);
  box-shadow: 0 18px 50px rgba(97, 78, 53, 0.06);
  border: 1px solid rgba(205, 190, 165, 0.38);
}

.content-card h4 {
  margin: 0 0 12px;
  color: #2f231d;
  font-size: 16px;
  font-weight: 800;
}

.manual-steps,
.manual-list {
  margin: 0;
  padding-left: 20px;
  color: #43352b;
}

.manual-steps {
  display: grid;
  gap: 12px;
}

.manual-steps li,
.manual-list li {
  line-height: 1.85;
  font-size: 14px;
}

.manual-list {
  display: grid;
  gap: 10px;
}

.inline-note {
  display: inline-block;
  color: rgba(67, 53, 43, 0.74);
}

.manual-note {
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 247, 232, 0.8), rgba(255, 252, 247, 0.9));
  border: 1px solid rgba(230, 162, 60, 0.18);
  color: rgba(88, 66, 49, 0.82);
  font-size: 13px;
  line-height: 1.8;
}

code {
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 166, 0, 0.1);
  color: #af6300;
  font-size: 12px;
  font-weight: 700;
}

.webhook-image-block {
  margin-top: 8px;
}

.webhook-image {
  width: min(100%, 360px);
  border-radius: 12px;
  border: 1px solid rgba(205, 190, 165, 0.42);
  box-shadow: 0 12px 28px rgba(97, 78, 53, 0.12);
  cursor: zoom-in;
  overflow: hidden;
}

@media (max-width: 1100px) {
  .manual-hero,
  .manual-shell {
    grid-template-columns: 1fr;
  }

  .manual-nav {
    position: static;
  }
}

@media (max-width: 720px) {
  .manual-page {
    padding: 18px 16px 40px;
  }

  .manual-hero__content,
  .manual-hero__card,
  .content-card {
    padding: 22px 18px;
    border-radius: 22px;
  }

  .manual-hero h1 {
    font-size: 34px;
  }

  .section-head {
    gap: 12px;
  }

  .section-head h2 {
    font-size: 24px;
  }
}
</style>
