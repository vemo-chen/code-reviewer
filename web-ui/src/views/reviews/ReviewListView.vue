
<template>
  <section class="list-page">
    <section class="query-panel">
      <el-form :inline="true" :model="queryForm">
        <el-form-item>
          <el-select v-model="queryForm.projectId" placeholder="请选择项目" clearable filterable>
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="project.projectName"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="queryForm.status" placeholder="请选择任务状态" clearable filterable>
            <el-option label="PENDING" value="PENDING" />
            <el-option label="RUNNING" value="RUNNING" />
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="queryForm.fixStatus" placeholder="请选择整改状态" clearable filterable>
            <el-option label="待修改" value="TO_BE_FIXED" />
            <el-option label="待审查" value="TO_BE_REVIEWED" />
            <el-option label="审查通过" value="REVIEW_PASSED" />
            <el-option label="审查不通过" value="REVIEW_REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="queryForm.gitlabUsername"
            placeholder="请选择/输入提交者"
            clearable
            filterable
            allow-create
            default-first-option
          >
            <el-option
              v-for="user in gitlabUsers"
              :key="user.gitlabUsername"
              :label="user.optionLabel"
              :value="user.gitlabUsername"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model.trim="queryForm.targetTitle" placeholder="请输入提交信息" clearable />
        </el-form-item>
        <el-form-item class="date-item">
          <el-date-picker
            v-model="queryForm.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            unlink-panels
          />
        </el-form-item>
        <el-form-item class="actions">
          <el-button type="warning" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <transition name="batch-bar">
        <div v-if="hasSelection" class="batch-toolbar">
          <div class="batch-count-pill">已选 {{ selectedRows.length }} 条</div>
          <div class="batch-toolbar-divider" />
          <button
            class="batch-toolbar-action is-primary"
            type="button"
            :disabled="retryableSelectedRows.length === 0 || batchRetryLoading"
            @click="batchRetry"
          >
            <el-icon class="batch-toolbar-icon"><RefreshRight /></el-icon>
            <span>{{ batchRetryLoading ? "提交中..." : "批量重试" }}</span>
          </button>
          <button class="batch-toolbar-action" type="button" @click="clearSelection">
            <el-icon class="batch-toolbar-icon"><CloseBold /></el-icon>
            <span>取消选择</span>
          </button>
        </div>
      </transition>

      <el-table
        ref="reviewTableRef"
        v-loading="loading"
        :data="records"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="52" :selectable="isRowSelectable" />
        <el-table-column prop="projectName" label="项目" min-width="150" />
        <el-table-column prop="targetTitle" label="提交信息" min-width="260" show-overflow-tooltip />
        <el-table-column prop="submitBranch" label="提交分支" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.submitBranch || "--" }}
          </template>
        </el-table-column>
        <el-table-column label="任务状态" min-width="110">
          <template #default="{ row }">
            <span :class="['state-pill', taskStatusClass(row.status)]">
              {{ row.status || "--" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="整改状态" min-width="120">
          <template #default="{ row }">
            <span :class="['state-pill', fixStatusClass(row.fixStatus)]">
              {{ formatFixStatus(row.fixStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="提交者" min-width="120" />
        <el-table-column label="提交时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button link type="warning" :loading="detailLoadingId === row.taskId" @click="openDetail(row)">
                查看详情
              </el-button>
              <el-button
                link
                type="warning"
                :disabled="!canManualRetry(row.status)"
                :loading="retryLoadingId === row.taskId"
                @click="retryTask(row)"
              >
                重试
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <div class="pagination-total">共{{ pagination.total }} 条</div>
        <div class="pagination-actions">
          <el-pagination
            v-model:current-page="pagination.pageNo"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            layout="prev, pager, next"
            background
            @current-change="loadRecords"
          />
          <el-select v-model="pagination.pageSize" class="page-size-select" @change="handlePageSizeChange">
            <el-option label="10条/页" :value="10" />
            <el-option label="20条/页" :value="20" />
            <el-option label="50条/页" :value="50" />
          </el-select>
        </div>
      </div>
    </section>

    <el-drawer v-model="detailVisible" size="680px" destroy-on-close :show-close="false" class="review-detail-drawer">
      <template #header>
        <div class="drawer-header">
          <div class="drawer-header__title">
            <h3>审查详情</h3>
          </div>
          <div class="drawer-header__tools">
            <div v-if="detail" class="drawer-header__actions">
              <el-button
                v-if="canSubmitFix(detail)"
                class="drawer-action-btn drawer-action-btn--primary"
                :loading="fixActionLoading === 'submit'"
                @click="handleSubmitFixReview"
              >
                提交复审
              </el-button>
              <el-button
                v-if="canApproveFix(detail)"
                class="drawer-action-btn drawer-action-btn--success"
                :loading="fixActionLoading === 'approve'"
                @click="handleApproveFix"
              >
                审查通过
              </el-button>
              <el-button
                v-if="canRejectFix(detail)"
                class="drawer-action-btn drawer-action-btn--danger"
                :loading="fixActionLoading === 'reject'"
                @click="handleRejectFix"
              >
                审查不通过
              </el-button>
            </div>
            <button type="button" class="drawer-close-btn" @click="detailVisible = false" aria-label="关闭审查详情">
              ×
            </button>
          </div>
        </div>
      </template>
      <div v-if="detail" class="detail-drawer">
        <section class="detail-hero">
          <div class="detail-hero__head">
            <h4>{{ detail.targetTitle || "未命名提交" }}</h4>
            <span class="detail-hero__project">{{ detail.projectName || "--" }}</span>
          </div>
          <div class="detail-hero__meta">
            <span>{{ detail.operatorName || "--" }}</span>
            <span>{{ detail.submitBranch || "--" }}</span>
            <span>{{ detail.targetId || "--" }}</span>
            <span>{{ formatDateTime(detail.createdAt) }}</span>
          </div>
        </section>

        <section class="detail-overview-grid">
          <article class="overview-card overview-card--status overview-card--full">
            <div class="compliance-head">
              <div class="compliance-head__main">
                <div class="overview-card__title-row">
                  <h5>整改情况</h5>
                  <span class="overview-card__title-divider" aria-hidden="true"></span>
                  <el-button text type="warning" class="overview-chip overview-chip--inline" @click="openFlowDialog">
                    查看流转历史
                  </el-button>
                </div>
              </div>
              <div class="review-stamp-wrap">
                <div :class="['review-stamp', `review-stamp--${fixStatusClass(detail.fixStatus)}`]">
                  {{ formatFixStatus(detail.fixStatus) }}
                </div>
              </div>
            </div>
            <div class="overview-card__body">
              <div class="overview-card__split overview-card__split--balanced">
                <div class="detail-metric">
                  <span class="overview-card__label">提交复审人</span>
                  <strong class="overview-card__name">{{ detail.fixSubmittedByName || "--" }}</strong>
                  <span v-if="detail.fixSubmittedAt" class="overview-card__time">{{ formatDateTime(detail.fixSubmittedAt) }}</span>
                </div>
                <div class="detail-metric">
                  <span class="overview-card__label">审查人</span>
                  <strong class="overview-card__name">{{ detail.fixReviewedByName || "--" }}</strong>
                  <span class="overview-card__time">{{ formatDateTime(detail.fixReviewedAt) }}</span>
                </div>
              </div>
              <div class="overview-card__subline">
                <span class="detail-label">最近复审意见</span>
                <div class="overview-card__comment">
                  <p>{{ detail.fixReviewComment || "暂无复审意见" }}</p>
                </div>
              </div>
            </div>
          </article>
        </section>

        <section class="detail-section detail-section--compliance">
          <div class="compliance-head">
            <div class="compliance-head__main">
              <h5>合规性评估</h5>
              <div class="detail-meta">
                <span :class="['state-pill', taskStatusClass(detail.status)]">{{ detail.status || "--" }}</span>
                <span :class="['state-pill', riskLevelClass(detail.riskLevel)]">{{ detail.riskLevel || "--" }}</span>
              </div>
            </div>
            <div class="compliance-score">
              <span class="compliance-score__label">得分</span>
              <strong>{{ detail.finalScore ?? "--" }}</strong>
            </div>
          </div>

          <div class="compliance-highlight">
            <div class="compliance-highlight__title">评估摘要</div>
            <p>{{ detail.summary || detail.briefSummary || "暂无摘要" }}</p>
          </div>

          <div class="compliance-deductions">
            <div class="deduction-chip">
              <span>建议评分</span>
              <strong>{{ detail.suggestedScore ?? "--" }}</strong>
            </div>
            <div class="deduction-chip">
              <span>扣分</span>
              <strong>{{ detail.deductionScore ?? "--" }}</strong>
            </div>
          </div>

          <div v-if="detail.scoreReason" class="score-note">
            <p class="score-note__text">{{ detail.scoreReason }}</p>
          </div>
        </section>

        <section class="detail-section">
          <h5 class="section-title">问题列表</h5>
          <div v-if="detail.comments.length" class="comment-list">
            <article v-for="comment in detail.comments" :key="comment.id" class="comment-card">
              <div class="comment-head">
                <strong>{{ comment.category || "代码问题" }}</strong>
                <span :class="['state-pill', riskLevelClass(comment.severity)]">
                  {{ comment.severity || "--" }}
                </span>
              </div>
              <p class="comment-location">{{ comment.filePath || "--" }}:{{ comment.lineNo || "--" }}</p>
              <p class="comment-body"><strong>问题：</strong>{{ comment.message }}</p>
              <p class="suggestion"><strong>建议：</strong>{{ comment.suggestion || "暂无建议" }}</p>
            </article>
          </div>
          <el-empty v-else description="暂无问题明细" />
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="flowDialogVisible" title="流转历史" width="640px" destroy-on-close class="flow-history-dialog">
      <div v-if="fixFlows.length" class="fix-flow-list">
        <article v-for="flow in fixFlows" :key="flow.id" class="fix-flow-item">
          <div class="fix-flow-item__node" :class="fixStatusClass(flow.toStatus)" />
          <div class="fix-flow-item__content">
            <div class="fix-flow-item__head">
              <strong>{{ formatFixStatus(flow.toStatus) }}</strong>
              <time>{{ formatDateTime(flow.createdAt) }}</time>
            </div>
            <p class="fix-flow-item__operator">{{ flow.operatorName || "--" }}</p>
            <p class="fix-flow-item__transition">
              {{ formatFixStatus(flow.fromStatus) }} → {{ formatFixStatus(flow.toStatus) }}
            </p>
            <p v-if="flow.comment" class="fix-flow-item__comment">{{ flow.comment }}</p>
          </div>
        </article>
      </div>
      <el-empty v-else description="暂无流转历史" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { CloseBold, RefreshRight } from "@element-plus/icons-vue";
import {
  approveFixReview,
  batchRetryReviewTask,
  fetchFixFlows,
  fetchReviewSubmitters,
  fetchReviewTaskDetail,
  fetchReviewTasks,
  rejectFixReview,
  retryReviewTask,
  submitFixReview,
  type ReviewFixFlowItem
} from "../../api/dashboard";
import { fetchProjects } from "../../api/projects";
import { useAuthStore } from "../../stores/auth";

interface ProjectItem {
  id: number;
  projectName: string;
}

interface GitLabUserOption {
  gitlabUsername: string;
  optionLabel: string;
}

interface ReviewRecordItem {
  taskId: number;
  projectId: number | null;
  projectName: string;
  targetId: string;
  targetTitle: string;
  submitBranch: string;
  status: string;
  fixStatus: string;
  retryCount: number;
  operatorName: string;
  riskLevel: string;
  summary: string;
  createdAt: string | null;
  finishedAt: string | null;
}

interface ReviewCommentItem {
  id: number;
  filePath: string;
  lineNo: number | null;
  severity: string;
  category: string;
  message: string;
  suggestion: string;
  isPosted: boolean;
  createdAt: string | null;
}

interface ReviewTaskDetail {
  taskId: number;
  projectId: number | null;
  projectName: string;
  targetId: string;
  targetTitle: string;
  submitBranch: string;
  status: string;
  ownerUserId: number | null;
  ownerDisplayName: string;
  fixStatus: string;
  fixSubmittedBy: number | null;
  fixSubmittedByName: string;
  fixSubmittedAt: string | null;
  fixReviewedBy: number | null;
  fixReviewedByName: string;
  fixReviewedAt: string | null;
  fixReviewComment: string;
  retryCount: number;
  operatorName: string;
  createdAt: string | null;
  finishedAt: string | null;
  riskLevel: string;
  suggestedScore: number | null;
  deductionScore: number | null;
  finalScore: number | null;
  scoreReason: string;
  summary: string;
  briefSummary: string;
  comments: ReviewCommentItem[];
}

interface BatchRetryResultItem {
  taskId: number;
  success: boolean;
  message: string;
}

interface BatchRetryResponse {
  total: number;
  successCount: number;
  failedCount: number;
  results: BatchRetryResultItem[];
}

const authStore = useAuthStore();
const reviewTableRef = ref<any>(null);
const loading = ref(false);
const detailLoadingId = ref<number | null>(null);
const retryLoadingId = ref<number | null>(null);
const batchRetryLoading = ref(false);
const detailVisible = ref(false);
const flowDialogVisible = ref(false);
const fixActionLoading = ref<"submit" | "approve" | "reject" | "">("");
const projects = ref<ProjectItem[]>([]);
const gitlabUsers = ref<GitLabUserOption[]>([]);
const records = ref<ReviewRecordItem[]>([]);
const selectedRows = ref<ReviewRecordItem[]>([]);
const detail = ref<ReviewTaskDetail | null>(null);
const fixFlows = ref<ReviewFixFlowItem[]>([]);

const hasSelection = computed(() => selectedRows.value.length > 0);
const retryableSelectedRows = computed(() => selectedRows.value.filter((row) => canManualRetry(row.status)));
const currentUserId = computed(() => authStore.user?.userId ?? null);
const isAdmin = computed(() => authStore.isAdmin);

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0
});

const getDefaultDateRange = (): string[] => {
  const end = new Date();
  const start = new Date();
  start.setMonth(start.getMonth() - 1);

  const formatDate = (value: Date) => {
    const year = value.getFullYear();
    const month = `${value.getMonth() + 1}`.padStart(2, "0");
    const day = `${value.getDate()}`.padStart(2, "0");
    return `${year}-${month}-${day}`;
  };

  return [formatDate(start), formatDate(end)];
};

const queryForm = reactive<{
  projectId: number | undefined;
  status: string;
  fixStatus: string;
  gitlabUsername: string | null;
  targetTitle: string;
  dateRange: string[] | null;
}>({
  projectId: undefined,
  status: "",
  fixStatus: "",
  gitlabUsername: "",
  targetTitle: "",
  dateRange: getDefaultDateRange()
});
const normalizeText = (value: string | null | undefined) => {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed || undefined;
};

const buildQueryParams = () => ({
  projectId: queryForm.projectId,
  status: queryForm.status || undefined,
  fixStatus: queryForm.fixStatus || undefined,
  gitlabUsername: normalizeText(queryForm.gitlabUsername),
  targetTitle: normalizeText(queryForm.targetTitle),
  startDate: Array.isArray(queryForm.dateRange) && queryForm.dateRange.length === 2
    ? queryForm.dateRange[0]
    : undefined,
  endDate: Array.isArray(queryForm.dateRange) && queryForm.dateRange.length === 2
    ? queryForm.dateRange[1]
    : undefined
});

const clearSelection = () => {
  selectedRows.value = [];
  reviewTableRef.value?.clearSelection?.();
};

const handleSearch = () => {
  pagination.pageNo = 1;
  loadRecords();
};

const resetFilters = () => {
  queryForm.projectId = undefined;
  queryForm.status = "";
  queryForm.fixStatus = "";
  queryForm.gitlabUsername = "";
  queryForm.targetTitle = "";
  queryForm.dateRange = getDefaultDateRange();
  pagination.pageNo = 1;
  loadRecords();
};

const loadProjects = async () => {
  const response = await fetchProjects({ pageNo: 1, pageSize: 500 });
  projects.value = response.data.data.records || [];
};

const loadGitLabUsers = async () => {
  try {
    const response = await fetchReviewSubmitters();
    const items = response.data.data.items || [];
    gitlabUsers.value = items.map((item: { gitlabUsername: string; displayLabel?: string; operatorName?: string }) => ({
      gitlabUsername: item.gitlabUsername,
      optionLabel: item.displayLabel || item.operatorName || item.gitlabUsername
    }));
  } catch (error) {
    gitlabUsers.value = [];
  }
};

const loadRecords = async () => {
  loading.value = true;
  try {
    const response = await fetchReviewTasks(pagination.pageNo, pagination.pageSize, buildQueryParams());
    const pageData = response.data.data;
    records.value = pageData.records || [];
    pagination.total = pageData.total || 0;
    await nextTick();
    clearSelection();
  } catch (error) {
    ElMessage.error("加载审查记录失败");
  } finally {
    loading.value = false;
  }
};

const loadDetailBundle = async (taskId: number) => {
  const [detailResponse, flowResponse] = await Promise.all([
    fetchReviewTaskDetail(taskId),
    fetchFixFlows(taskId)
  ]);
  detail.value = detailResponse.data.data;
  fixFlows.value = flowResponse.data.data.records || [];
};

const handlePageSizeChange = () => {
  pagination.pageNo = 1;
  loadRecords();
};

const handleSelectionChange = (rows: ReviewRecordItem[]) => {
  selectedRows.value = rows;
};

const openDetail = async (row: ReviewRecordItem) => {
  detailLoadingId.value = row.taskId;
  try {
    await loadDetailBundle(row.taskId);
    flowDialogVisible.value = false;
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error("加载审查详情失败");
  } finally {
    detailLoadingId.value = null;
  }
};

const canManualRetry = (status: string | null | undefined) => {
  const normalized = (status || "").toUpperCase();
  return normalized === "FAILED" || normalized === "SUCCESS";
};

const canSubmitFix = (task: ReviewTaskDetail | null) => {
  if (!task) {
    return false;
  }
  return task.status === "SUCCESS" && (task.fixStatus === "TO_BE_FIXED" || task.fixStatus === "REVIEW_REJECTED");
};

const canApproveFix = (task: ReviewTaskDetail | null) => {
  if (!task || task.fixStatus !== "TO_BE_REVIEWED") {
    return false;
  }
  return isAdmin.value || (currentUserId.value !== null && task.ownerUserId === currentUserId.value);
};

const canRejectFix = (task: ReviewTaskDetail | null) => canApproveFix(task);

const openFlowDialog = () => {
  flowDialogVisible.value = true;
};

const isRowSelectable = (row: ReviewRecordItem) => canManualRetry(row.status);

const confirmRetry = (count: number) =>
  ElMessageBox.confirm(
    `重试后将重新提交 ${count} 条任务给大模型，并覆盖这些任务当前已有的审查结果，是否继续？`,
    "确认重试",
    {
      type: "warning",
      confirmButtonText: "确认重试",
      cancelButtonText: "取消",
      distinguishCancelAndClose: true
    }
  );

const retryTask = async (row: ReviewRecordItem) => {
  if (!canManualRetry(row.status)) {
    return;
  }
  try {
    await confirmRetry(1);
  } catch (error) {
    return;
  }
  retryLoadingId.value = row.taskId;
  try {
    await retryReviewTask(row.taskId);
    ElMessage.success("任务已重新提交审查");
    await loadRecords();
    if (detailVisible.value && detail.value && detail.value.taskId === row.taskId) {
      await loadDetailBundle(row.taskId);
    }
  } catch (error) {
    ElMessage.error("任务重试失败");
  } finally {
    retryLoadingId.value = null;
  }
};

const batchRetry = async () => {
  if (retryableSelectedRows.value.length === 0) {
    return;
  }
  try {
    await confirmRetry(retryableSelectedRows.value.length);
  } catch (error) {
    return;
  }

  const selectedTaskIds = retryableSelectedRows.value.map((row) => row.taskId);
  batchRetryLoading.value = true;
  try {
    const response = await batchRetryReviewTask(selectedTaskIds);
    const result = response.data.data as BatchRetryResponse;
    const failedMessages = (result.results || [])
      .filter((item) => !item.success)
      .slice(0, 3)
      .map((item) => `#${item.taskId} ${item.message}`);
    const summary = `已提交 ${result.successCount} 条，失败 ${result.failedCount} 条`;
    ElMessage[result.failedCount > 0 ? "warning" : "success"](
      failedMessages.length > 0 ? `${summary}；${failedMessages.join("；")}` : summary
    );
    await loadRecords();
    if (detailVisible.value && detail.value && selectedTaskIds.indexOf(detail.value.taskId) >= 0) {
      await loadDetailBundle(detail.value.taskId);
    }
  } catch (error) {
    ElMessage.error("批量重试失败");
  } finally {
    batchRetryLoading.value = false;
  }
};
const runFixAction = async (
  action: "submit" | "approve" | "reject",
  handler: () => Promise<unknown>,
  successMessage: string,
  failureMessage: string
) => {
  if (!detail.value) {
    return;
  }
  fixActionLoading.value = action;
  try {
    await handler();
    ElMessage.success(successMessage);
    await Promise.all([loadRecords(), loadDetailBundle(detail.value.taskId)]);
  } catch (error) {
    ElMessage.error(failureMessage);
  } finally {
    fixActionLoading.value = "";
  }
};

const handleSubmitFixReview = async () => {
  if (!detail.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      "确认将当前记录提交给项目 Owner 进行复审吗？",
      "提交复审",
      {
        type: "warning",
        confirmButtonText: "提交复审",
        cancelButtonText: "取消"
      }
    );
  } catch (error) {
    return;
  }
  await runFixAction(
    "submit",
    () => submitFixReview(detail.value!.taskId),
    "提交复审成功",
    "提交复审失败"
  );
};

const handleApproveFix = async () => {
  if (!detail.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      "确认将当前整改记录标记为审查通过吗？",
      "审查通过",
      {
        type: "warning",
        confirmButtonText: "确认通过",
        cancelButtonText: "取消"
      }
    );
  } catch (error) {
    return;
  }
  await runFixAction(
    "approve",
    () => approveFixReview(detail.value!.taskId),
    "审查通过成功",
    "审查通过失败"
  );
};

const handleRejectFix = async () => {
  if (!detail.value) {
    return;
  }
  let comment = "";
  try {
    const result = await ElMessageBox.prompt("请填写审查不通过的原因", "审查不通过", {
      inputType: "textarea",
      inputPlaceholder: "请输入复审意见",
      inputValidator: (value) => (value && value.trim() ? true : "复审意见不能为空"),
      confirmButtonText: "提交",
      cancelButtonText: "取消"
    });
    comment = result.value?.trim() || "";
  } catch (error) {
    return;
  }
  await runFixAction(
    "reject",
    () => rejectFixReview(detail.value!.taskId, comment),
    "审查不通过提交成功",
    "审查不通过提交失败"
  );
};

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return "--";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value.replace("T", " ").slice(0, 19);
  }
  const year = parsed.getFullYear();
  const month = `${parsed.getMonth() + 1}`.padStart(2, "0");
  const day = `${parsed.getDate()}`.padStart(2, "0");
  const hours = `${parsed.getHours()}`.padStart(2, "0");
  const minutes = `${parsed.getMinutes()}`.padStart(2, "0");
  const seconds = `${parsed.getSeconds()}`.padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
};

const formatFixStatus = (status: string | null | undefined) => {
  const normalized = (status || "").toUpperCase();
  if (normalized === "TO_BE_FIXED") {
    return "待修改";
  }
  if (normalized === "TO_BE_REVIEWED") {
    return "待审查";
  }
  if (normalized === "REVIEW_PASSED") {
    return "审查通过";
  }
  if (normalized === "REVIEW_REJECTED") {
    return "审查不通过";
  }
  return "--";
};

const taskStatusClass = (status: string | null | undefined) => {
  const normalized = (status || "").toUpperCase();
  if (normalized === "SUCCESS") {
    return "is-success";
  }
  if (normalized === "FAILED") {
    return "is-danger";
  }
  return "is-warning";
};

const fixStatusClass = (status: string | null | undefined) => {
  const normalized = (status || "").toUpperCase();
  if (normalized === "REVIEW_PASSED") {
    return "is-success";
  }
  if (normalized === "REVIEW_REJECTED") {
    return "is-danger";
  }
  if (normalized === "TO_BE_REVIEWED") {
    return "is-warning";
  }
  if (normalized === "TO_BE_FIXED") {
    return "is-neutral";
  }
  return "is-neutral";
};

const riskLevelClass = (riskLevel: string | null | undefined) => {
  const normalized = (riskLevel || "").toUpperCase();
  if (normalized === "HIGH" || normalized === "CRITICAL") {
    return "is-danger";
  }
  if (normalized === "MEDIUM") {
    return "is-warning";
  }
  if (normalized === "LOW") {
    return "is-success";
  }
  return "is-neutral";
};

onMounted(async () => {
  try {
    await Promise.all([loadProjects(), loadRecords()]);
    await loadGitLabUsers();
  } catch (error) {
    ElMessage.error("加载页面基础数据失败");
  }
});
</script>

<style scoped>
.list-page {
  display: grid;
  gap: 20px;
}

.query-panel,
.table-panel {
  background: var(--cr-surface-low);
  box-shadow: var(--cr-shadow-card);
}

.query-panel {
  padding: 18px 18px 0;
  border-radius: 8px;
}

.table-panel {
  padding: 12px 12px 14px;
  border-radius: 8px;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 18px;
  margin: 0 4px 18px;
  padding: 12px 16px;
  border-radius: 18px;
  border: 1px solid #ece2d0;
  background: linear-gradient(180deg, #fcfaf4 0%, #f5efe2 100%);
  box-shadow: 0 12px 22px rgba(137, 105, 59, 0.08);
}

.batch-count-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 102px;
  padding: 8px 16px;
  border-radius: 11px;
  background: #ff8c00;
  color: #fff7ea;
  box-shadow: inset 0 -1px 0 rgba(0, 0, 0, 0.08);
  font-size: 14px;
  font-weight: 800;
  line-height: 1;
}
.batch-toolbar-divider {
  width: 1px;
  height: 28px;
  background: #dfd2bc;
}

.batch-toolbar-action {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border: none;
  background: transparent;
  color: #5a4734;
  cursor: pointer;
  font-size: 15px;
  font-weight: 700;
  transition: color 0.2s ease, transform 0.2s ease, opacity 0.2s ease;
}

.batch-toolbar-action:hover:not(:disabled) {
  color: #8f4d00;
  transform: translateY(-1px);
}

.batch-toolbar-action:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.batch-toolbar-action.is-primary {
  color: #8f4d00;
}

.batch-toolbar-icon {
  font-size: 17px;
}

.action-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 4px 0;
}

.pagination-total {
  color: var(--cr-text-soft);
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.pagination-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.page-size-select,
.pagination-actions :deep(.page-size-select) {
  width: 112px;
  flex: 0 0 112px;
}

.pagination-actions :deep(.page-size-select .el-select__wrapper) {
  width: 112px;
}

:deep(.el-form--inline .el-form-item) {
  width: 200px;
  margin-right: 16px;
}

:deep(.el-form--inline .el-form-item.date-item) {
  width: 320px;
}

:deep(.el-form--inline .el-form-item.actions) {
  width: auto;
  margin-left: auto;
}

:deep(.el-input),
:deep(.el-select),
:deep(.el-date-editor) {
  width: 100%;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper),
:deep(.el-date-editor.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: none;
  background: var(--cr-surface-paper);
}

:deep(.el-table) {
  --el-table-header-bg-color: var(--cr-surface-low);
  --el-table-row-hover-bg-color: rgba(255, 140, 0, 0.04);
  --el-table-border-color: transparent;
  border-radius: 8px;
}

:deep(.el-table th.el-table__cell) {
  color: rgba(86, 67, 52, 0.72);
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-weight: 800;
}

:deep(.el-table td.el-table__cell) {
  color: var(--cr-text-soft);
}

.state-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.state-pill--hero {
  min-width: 126px;
  padding: 8px 16px;
  font-size: 16px;
  letter-spacing: 0;
  text-transform: none;
}

.state-pill.is-success {
  background: rgba(37, 141, 83, 0.14);
  color: #167a43;
}

.state-pill.is-warning {
  background: rgba(255, 140, 0, 0.16);
  color: var(--cr-primary-deep);
}

.state-pill.is-danger {
  background: rgba(220, 55, 55, 0.14);
  color: #b32222;
}

.state-pill.is-neutral {
  background: rgba(26, 28, 25, 0.08);
  color: rgba(26, 28, 25, 0.64);
}

.detail-drawer {
  display: grid;
  gap: 14px;
  padding: 6px 2px 28px;
}

:deep(.review-detail-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 14px 20px 12px;
  border-bottom: 1px solid #ebeef5;
  background: rgba(255, 255, 255, 0.96);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.drawer-header__title h3 {
  margin: 0;
  color: var(--cr-text);
  font-size: 16px;
  font-weight: 800;
}

.drawer-header__tools {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
}

.drawer-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.drawer-action-btn {
  height: 30px;
  padding: 0 14px;
  border-radius: 9px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 700;
  box-shadow: none;
}

.drawer-action-btn + .drawer-action-btn {
  margin-left: 0;
}

.drawer-action-btn--primary {
  background: #f4a63b;
  border-color: #f4a63b;
  color: #fff7ea;
}

.drawer-action-btn--primary:hover,
.drawer-action-btn--primary:focus-visible {
  background: #ea9830;
  border-color: #ea9830;
  color: #fff7ea;
}

.drawer-action-btn--success {
  background: #6cc04a;
  border-color: #6cc04a;
  color: #ffffff;
}

.drawer-action-btn--success:hover,
.drawer-action-btn--success:focus-visible {
  background: #61b241;
  border-color: #61b241;
  color: #ffffff;
}

.drawer-action-btn--danger {
  background: #fff;
  border-color: rgba(239, 68, 68, 0.38);
  color: #ef5f5f;
}

.drawer-action-btn--danger:hover,
.drawer-action-btn--danger:focus-visible {
  background: rgba(255, 242, 242, 0.92);
  border-color: rgba(239, 68, 68, 0.52);
  color: #e34d4d;
}

.drawer-close-btn {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #ebeef5;
  border-radius: 9px;
  background: #ffffff;
  color: rgba(96, 98, 102, 0.9);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.18s ease;
}

.drawer-close-btn:hover,
.drawer-close-btn:focus-visible {
  border-color: #dcdfe6;
  background: #f8fafc;
  color: var(--cr-text);
}

.detail-hero {
  display: grid;
  gap: 8px;
  padding: 2px 4px 4px;
}

.detail-hero__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-hero h4 {
  margin: 0;
  color: var(--cr-text);
  font-size: 16px;
  font-weight: 800;
  line-height: 1.45;
}

.detail-hero__project {
  display: inline-flex;
  align-items: center;
  border: 1px solid #ebeef5;
  border-radius: 999px;
  padding: 3px 8px;
  background: #f5f7fa;
  color: #606266;
  font-size: 10px;
  font-weight: 700;
}

.detail-hero__meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.detail-hero__meta span {
  margin: 0;
  color: var(--cr-text-soft);
  font-size: 11px;
  line-height: 1.6;
}

.detail-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #f8fafc;
  padding: 14px;
  display: grid;
  gap: 14px;
  min-height: 118px;
}

.overview-card--full {
  grid-column: 1 / -1;
}

.overview-card__body {
  display: grid;
  gap: 14px;
}

.overview-card__label {
  color: rgba(96, 98, 102, 0.76);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.overview-card__value {
  display: flex;
  align-items: center;
}

.overview-card__split--balanced {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.overview-card__subline {
  display: grid;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}

.overview-card__title-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.overview-card__title-divider {
  width: 1px;
  height: 14px;
  background: rgba(191, 179, 164, 0.42);
  flex: 0 0 auto;
}

.overview-card__comment {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.88);
  padding: 12px 14px;
}

.overview-card__subline p {
  margin: 0;
  color: var(--cr-text-soft);
  font-size: 12px;
  line-height: 1.6;
}

.overview-card__split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.overview-card__name {
  color: var(--cr-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
}

.overview-card__time {
  color: rgba(96, 98, 102, 0.82);
  font-size: 10px;
  line-height: 1.5;
}

.overview-chip {
  height: 24px;
  padding: 0 10px;
  border: 1px solid rgba(230, 162, 60, 0.18);
  border-radius: 999px;
  background: rgba(253, 246, 236, 0.92);
  color: var(--cr-primary-deep);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.overview-chip--inline {
  height: auto;
  min-height: unset;
  padding: 0;
  border: none;
  background: transparent;
  color: rgba(120, 107, 93, 0.82);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.01em;
  text-transform: none;
}

.overview-chip--inline:hover,
.overview-chip--inline:focus-visible {
  background: transparent;
  color: var(--cr-primary-deep);
}

.detail-section {
  border-radius: 10px;
  background: #f8fafc;
  padding: 16px;
  border: 1px solid #ebeef5;
  box-shadow: none;
}

.detail-section--compliance {
  display: grid;
  gap: 14px;
}

.compliance-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.compliance-head__main {
  display: grid;
  gap: 8px;
}

.compliance-head__main h5 {
  margin: 0;
  color: var(--cr-text);
  font-size: 18px;
  font-weight: 800;
}

.section-title {
  margin: 0 0 12px;
  color: var(--cr-text);
  font-size: 18px;
  font-weight: 800;
}

.compliance-score {
  display: grid;
  justify-items: end;
  gap: 4px;
}

.compliance-score__label {
  color: rgba(96, 98, 102, 0.76);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.compliance-score strong {
  color: var(--cr-primary-deep);
  font-size: 42px;
  line-height: 1;
  font-weight: 900;
}

.compliance-score--status strong {
  font-size: 16px;
}

.review-stamp-wrap {
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  min-width: 132px;
  padding-top: 2px;
}

.review-stamp {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 110px;
  padding: 6px 12px 5px;
  border: 2px solid rgba(196, 73, 57, 0.42);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.16);
  color: rgba(196, 73, 57, 0.78);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: 0.08em;
  line-height: 1;
  transform: rotate(-11deg);
  text-shadow: 0 0 0.01px currentColor;
  box-shadow: 0 1px 0 rgba(196, 73, 57, 0.08);
  text-transform: none;
  white-space: nowrap;
}

.review-stamp--is-success {
  border-color: rgba(91, 163, 118, 0.44);
  color: rgba(91, 163, 118, 0.78);
  box-shadow: 0 1px 0 rgba(91, 163, 118, 0.08);
}

.review-stamp--is-warning {
  border-color: rgba(214, 152, 63, 0.46);
  color: rgba(214, 152, 63, 0.82);
  box-shadow: 0 1px 0 rgba(214, 152, 63, 0.08);
}

.review-stamp--is-neutral {
  border-color: rgba(132, 116, 97, 0.34);
  color: rgba(132, 116, 97, 0.74);
  box-shadow: 0 1px 0 rgba(132, 116, 97, 0.08);
}

.compliance-highlight {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  padding: 14px;
}

.compliance-highlight__title {
  margin-bottom: 8px;
  color: var(--cr-text);
  font-size: 12px;
  font-weight: 800;
}

.compliance-highlight p {
  margin: 0;
  color: var(--cr-text-soft);
  font-size: 12px;
  line-height: 1.7;
}

.compliance-deductions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.deduction-chip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
}

.deduction-chip span {
  color: var(--cr-text-soft);
  font-size: 11px;
}

.deduction-chip strong {
  color: var(--cr-primary-deep);
  font-size: 12px;
  font-weight: 800;
}

.detail-grid {
  display: grid;
  gap: 14px;
}

.detail-grid--fix {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 16px;
}

.detail-metric {
  display: grid;
  gap: 8px;
}

.detail-label {
  color: rgba(86, 67, 52, 0.68);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.review-comment-box {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.7);
}

.review-comment-box p {
  margin: 0;
  color: var(--cr-text-soft);
  line-height: 1.7;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-overview-grid .detail-meta {
  justify-content: flex-end;
}

.detail-summary {
  margin: 14px 0 0;
  color: var(--cr-text-soft);
  line-height: 1.7;
}

.score-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(255, 140, 0, 0.1);
  color: var(--cr-primary-deep);
  font-size: 12px;
  font-weight: 800;
}

.score-note {
  display: grid;
  margin-top: -2px;
  padding: 2px 2px 0 12px;
}

.score-note__text {
  margin: 0;
  color: rgba(120, 107, 93, 0.82);
  font-size: 11px;
  line-height: 1.6;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-header h5 {
  margin: 0;
}

.flow-count {
  color: var(--cr-text-soft);
  font-size: 12px;
  font-weight: 700;
}

.fix-flow-list {
  display: grid;
  gap: 14px;
  position: relative;
  padding: 4px 2px;
}

.fix-flow-list::before {
  content: "";
  position: absolute;
  left: 7px;
  top: 8px;
  bottom: 8px;
  width: 1px;
  background: #dcdfe6;
}

.fix-flow-item {
  position: relative;
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  gap: 12px;
  background: transparent;
  padding: 0;
}

.fix-flow-item__node {
  position: relative;
  z-index: 1;
  width: 14px;
  height: 14px;
  margin-top: 4px;
  border-radius: 999px;
  border: 2px solid #dcdfe6;
  background: #fff;
}

.fix-flow-item__node.is-success {
  border-color: #67c23a;
}

.fix-flow-item__node.is-warning {
  border-color: #e6a23c;
}

.fix-flow-item__node.is-danger {
  border-color: #f56c6c;
}

.fix-flow-item__node.is-neutral {
  border-color: #c0c4cc;
}

.fix-flow-item__content {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
  padding: 12px 14px;
}

:deep(.flow-history-dialog .el-dialog__body) {
  padding-top: 10px;
}

.fix-flow-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.fix-flow-item__head strong {
  color: var(--cr-text);
  font-size: 12px;
}

.fix-flow-item__head time {
  color: rgba(86, 67, 52, 0.62);
  font-size: 10px;
}

.fix-flow-item__operator {
  margin: 6px 0 0;
  color: var(--cr-text-soft);
  font-size: 11px;
}

.fix-flow-item__transition,
.fix-flow-item__comment {
  margin: 8px 0 0;
  color: var(--cr-text-soft);
  font-size: 11px;
  line-height: 1.6;
}

.comment-list {
  display: grid;
  gap: 12px;
}

.comment-card {
  border-radius: 10px;
  background: var(--cr-surface-paper);
  padding: 14px;
}

.comment-card p {
  margin: 0;
  color: var(--cr-text-soft);
  font-size: 12px;
  line-height: 1.7;
}

.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.comment-head strong {
  color: var(--cr-text);
}

.comment-location {
  margin: 8px 0;
  font-size: 11px;
  line-height: 1.5;
  color: rgba(120, 107, 93, 0.58);
  word-break: break-all;
}

.comment-body {
  color: var(--cr-text-soft);
}

.comment-body strong,
.suggestion strong {
  color: var(--cr-text);
  font-weight: 800;
}

.suggestion {
  margin-top: 8px;
}

.batch-bar-enter-active,
.batch-bar-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.batch-bar-enter-from,
.batch-bar-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 960px) {
  :deep(.el-form--inline .el-form-item.actions) {
    margin-left: 0;
  }

  .batch-toolbar,
  .pagination-bar,
  .detail-grid--fix {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .detail-overview-grid,
  .detail-grid--fix,
  .overview-card__split,
  .compliance-deductions {
    grid-template-columns: 1fr;
  }

  .drawer-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .drawer-header__tools {
    width: 100%;
    margin-left: 0;
    justify-content: space-between;
  }

  .drawer-header__actions {
    justify-content: flex-start;
  }

  .detail-hero__meta,
  .compliance-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .compliance-score {
    justify-items: start;
  }

  .detail-metric--align-end {
    text-align: left;
  }
}
</style>
