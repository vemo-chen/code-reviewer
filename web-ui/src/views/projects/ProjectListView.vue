<template>
  <section class="list-page">
    <el-tabs v-model="activeTab" class="page-tabs">
      <el-tab-pane :label="texts.projectManagementTab" name="project-management">
        <section class="query-panel">
          <el-form :inline="true" :model="queryForm">
            <el-form-item><el-input v-model="queryForm.projectName" :placeholder="texts.projectNamePlaceholder" clearable /></el-form-item>
            <el-form-item><el-input v-model="queryForm.gitlabProjectUrl" :placeholder="texts.gitlabUrlPlaceholder" clearable /></el-form-item>
            <el-form-item>
              <el-select v-model="queryForm.aiReviewEnabled" class="query-select" :placeholder="texts.aiReviewStatus" clearable filterable>
                <el-option :label="texts.enabled" :value="true" />
                <el-option :label="texts.disabled" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select v-model="queryForm.wecomNotifyEnabled" class="query-select" :placeholder="texts.wecomStatus" clearable filterable>
                <el-option :label="texts.enabled" :value="true" />
                <el-option :label="texts.disabled" :value="false" />
              </el-select>
            </el-form-item>
            <el-form-item class="actions">
              <el-button type="warning" :loading="loading" @click="handleSearch">{{ texts.search }}</el-button>
              <el-button @click="resetFilters">{{ texts.reset }}</el-button>
              <el-button type="warning" plain @click="openCreateDialog">{{ texts.createProject }}</el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="table-panel">
          <el-table v-loading="loading" :data="projects" stripe>
            <el-table-column prop="projectName" :label="texts.projectName" min-width="180" />
            <el-table-column label="GitLab URL" min-width="340" show-overflow-tooltip>
              <template #default="{ row }">
                <a v-if="row.gitlabProjectUrl" class="gitlab-link" :href="row.gitlabProjectUrl" target="_blank" rel="noopener noreferrer">{{ row.gitlabProjectUrl }}</a>
                <span v-else>--</span>
              </template>
            </el-table-column>
            <el-table-column :label="texts.aiReview" min-width="100">
              <template #header>
                <el-tooltip :content="texts.aiReviewDesc" placement="top">
                  <span class="table-header-tip">{{ texts.aiReview }}</span>
                </el-tooltip>
              </template>
              <template #default="{ row }"><span :class="['status-pill', row.aiReviewEnabled ? 'is-primary' : 'is-neutral']">{{ row.aiReviewEnabled ? texts.enabled : texts.disabled }}</span></template>
            </el-table-column>
            <el-table-column :label="texts.reviewContext" min-width="120">
              <template #header>
                <el-tooltip :content="texts.reviewContextDesc" placement="top">
                  <span class="table-header-tip">{{ texts.reviewContext }}</span>
                </el-tooltip>
              </template>
              <template #default="{ row }"><span :class="['status-pill', row.reviewContextEnabled !== false ? 'is-primary' : 'is-neutral']">{{ row.reviewContextEnabled !== false ? texts.enabled : texts.disabled }}</span></template>
            </el-table-column>
            <el-table-column :label="texts.gitlabNote" min-width="110">
              <template #header>
                <el-tooltip :content="texts.gitlabNoteDesc" placement="top">
                  <span class="table-header-tip">{{ texts.gitlabNote }}</span>
                </el-tooltip>
              </template>
              <template #default="{ row }"><span :class="['status-pill', row.gitlabNoteEnabled ? 'is-primary' : 'is-neutral']">{{ row.gitlabNoteEnabled ? texts.enabled : texts.disabled }}</span></template>
            </el-table-column>
            <el-table-column :label="texts.wecomNotify" min-width="110">
              <template #header>
                <el-tooltip :content="texts.wecomNotifyDesc" placement="top">
                  <span class="table-header-tip">{{ texts.wecomNotify }}</span>
                </el-tooltip>
              </template>
              <template #default="{ row }"><span :class="['status-pill', row.wecomNotifyEnabled ? 'is-primary' : 'is-neutral']">{{ row.wecomNotifyEnabled ? texts.enabled : texts.disabled }}</span></template>
            </el-table-column>
            <el-table-column :label="texts.active" min-width="100">
              <template #header>
                <el-tooltip :content="texts.activeDesc" placement="top">
                  <span class="table-header-tip">{{ texts.active }}</span>
                </el-tooltip>
              </template>
              <template #default="{ row }"><span :class="['status-pill', row.active ? 'is-primary' : 'is-neutral']">{{ row.active ? texts.activeEnabled : texts.activeDisabled }}</span></template>
            </el-table-column>
            <el-table-column :label="texts.updatedAt" min-width="180">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column :label="texts.actions" width="260" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openViewDialog(row)">{{ texts.view }}</el-button>
                  <el-button v-if="canEditProject(row)" link type="warning" @click="openEditDialog(row)">{{ texts.edit }}</el-button>
                  <el-button v-if="canEditProject(row)" link type="danger" @click="handleDelete(row)">{{ texts.delete }}</el-button>
                  <el-dropdown
                    v-if="canCustomReviewProject(row)"
                    trigger="click"
                    popper-class="project-more-actions-menu"
                    @command="onProjectMoreCommand(row, $event)"
                  >
                    <button type="button" class="more-actions-trigger" :aria-label="texts.moreActions">
                      <svg viewBox="0 0 16 16" aria-hidden="true">
                        <circle cx="8" cy="3.25" r="1.2" />
                        <circle cx="8" cy="8" r="1.2" />
                        <circle cx="8" cy="12.75" r="1.2" />
                      </svg>
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="custom-review">{{ texts.customReview }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <div class="pagination-total">{{ texts.totalPrefix }} {{ pagination.total }} {{ texts.totalSuffix }}</div>
            <div class="pagination-actions">
              <el-pagination v-model:current-page="pagination.pageNo" :page-size="pagination.pageSize" :total="pagination.total" layout="prev, pager, next" background @current-change="loadProjects" />
              <el-select v-model="pagination.pageSize" class="page-size-select" @change="handlePageSizeChange">
                <el-option :label="texts.pageSize10" :value="10" />
                <el-option :label="texts.pageSize20" :value="20" />
                <el-option :label="texts.pageSize50" :value="50" />
              </el-select>
            </div>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane :label="texts.projectTemplateManagementTab" name="project-template-management">
        <section class="query-panel">
          <el-form :inline="true" :model="templateQueryForm">
            <el-form-item><el-input v-model="templateQueryForm.templateName" :placeholder="texts.templateNamePlaceholder" clearable /></el-form-item>
            <el-form-item class="actions">
              <el-button type="warning" :loading="templateLoading" @click="handleTemplateSearch">{{ texts.search }}</el-button>
              <el-button @click="resetTemplateFilters">{{ texts.reset }}</el-button>
              <el-button type="warning" plain @click="openCreateTemplateDialog">{{ texts.createTemplate }}</el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="table-panel">
          <el-table v-loading="templateLoading" :data="templates" stripe>
            <el-table-column prop="templateName" :label="texts.templateName" min-width="180" />
            <el-table-column :label="texts.templateDesc" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.templateDesc || "--" }}</template>
            </el-table-column>
            <el-table-column :label="texts.fileExtensions" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">{{ row.fileExtensions || "--" }}</template>
            </el-table-column>
            <el-table-column :label="texts.reviewPromptStatus" min-width="140">
              <template #default="{ row }">
                <span :class="['status-pill', row.baseReviewPrompt ? 'is-primary' : 'is-neutral']">
                  {{ row.baseReviewPrompt ? texts.configured : texts.notConfigured }}
                </span>
              </template>
            </el-table-column>
            <el-table-column :label="texts.actions" width="180" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openViewTemplateDialog(row)">{{ texts.view }}</el-button>
                  <el-button v-if="canManageTemplate(row)" link type="warning" @click="openEditTemplateDialog(row)">{{ texts.edit }}</el-button>
                  <el-button v-if="canManageTemplate(row)" link type="danger" @click="handleDeleteTemplate(row)">{{ texts.delete }}</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <div class="pagination-total">{{ texts.totalPrefix }} {{ templatePagination.total }} {{ texts.totalSuffix }}</div>
            <div class="pagination-actions">
              <el-pagination v-model:current-page="templatePagination.pageNo" :page-size="templatePagination.pageSize" :total="templatePagination.total" layout="prev, pager, next" background @current-change="loadTemplates" />
              <el-select v-model="templatePagination.pageSize" class="page-size-select" @change="handleTemplatePageSizeChange">
                <el-option :label="texts.pageSize10" :value="10" />
                <el-option :label="texts.pageSize20" :value="20" />
                <el-option :label="texts.pageSize50" :value="50" />
              </el-select>
            </div>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="dialogVisible" :title="dialogMode === 'create' ? texts.createProject : dialogMode === 'edit' ? texts.editProject : texts.viewProject" size="560px" destroy-on-close class="project-drawer">
      <div class="drawer-body" v-loading="drawerLoading">
        <el-form ref="projectFormRef" :model="projectForm" :rules="projectRules" :disabled="projectReadOnly" label-position="top" class="project-form">
          <el-form-item :label="texts.projectName" prop="projectName"><el-input v-model="projectForm.projectName" :placeholder="texts.projectNamePlaceholder" /></el-form-item>
          <el-form-item label="GitLab URL" prop="gitlabProjectUrl"><el-input v-model="projectForm.gitlabProjectUrl" :placeholder="texts.gitlabRealUrlPlaceholder" /></el-form-item>
          <el-form-item :label="texts.webhookToken" prop="gitlabWebhookToken">
            <div class="token-input-row">
              <el-input v-model="projectForm.gitlabWebhookToken" show-password :placeholder="texts.webhookTokenPlaceholder" clearable />
              <el-tooltip :content="texts.testConnection" placement="top">
                <el-button v-if="!projectReadOnly" class="token-test-button" plain :loading="gitlabTesting" @click="handleTestGitLab">
                  <svg class="token-test-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      d="M5 12.55a11 11 0 0 1 14 0M8.5 16a6 6 0 0 1 7 0M12 20h.01"
                      fill="none"
                      stroke="currentColor"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="1.9"
                    />
                  </svg>
                </el-button>
              </el-tooltip>
            </div>
          </el-form-item>
          <el-form-item :label="texts.reviewBranches" prop="selectedReviewBranches">
            <div class="branch-select-row">
              <el-select
                v-model="projectForm.reviewBranches"
                class="full-width"
                :placeholder="texts.reviewBranchesPlaceholder"
                multiple
                filterable
                allow-create
                default-first-option
                collapse-tags
                :max-collapse-tags="5"
                collapse-tags-tooltip
                :loading="branchLoading"
              >
                <el-option v-for="item in reviewBranchOptions" :key="item.name" :label="formatBranchLabel(item)" :value="item.name">
                  <span>{{ item.name }}</span>
                  <span v-if="item.defaultBranch" class="branch-option-meta">{{ texts.defaultBranch }}</span>
                  <span v-if="item.protectedBranch" class="branch-option-meta">{{ texts.protectedBranch }}</span>
                  <span v-if="item.missing" class="branch-option-missing">{{ texts.missingBranch }}</span>
                </el-option>
              </el-select>
              <el-tooltip :content="texts.refreshBranches" placement="top">
                <el-button v-if="!projectReadOnly" class="branch-refresh-button" plain :loading="branchLoading" @click="handleLoadBranches">
                  <svg class="branch-refresh-icon" viewBox="0 0 24 24" aria-hidden="true">
                    <path
                      d="M20 6v5h-5M4 18v-5h5M18.2 9A7 7 0 0 0 6.3 6.7L4 9m2 6a7 7 0 0 0 11.7 2.3L20 15"
                      fill="none"
                      stroke="currentColor"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="1.9"
                    />
                  </svg>
                </el-button>
              </el-tooltip>
            </div>
            <p class="member-hint">{{ texts.reviewBranchesHint }}</p>
          </el-form-item>
          <el-form-item v-if="showOwnerField" :label="texts.projectOwner">
            <el-select v-model="projectForm.ownerUserId" class="full-width" :placeholder="texts.projectOwnerPlaceholder" clearable filterable @change="handleOwnerChange">
              <el-option v-for="item in ownerCandidateOptions" :key="item.id" :label="formatUserLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="texts.projectMembers">
            <el-select v-model="projectForm.memberUserIds" class="full-width" :placeholder="texts.projectMembersPlaceholder" multiple filterable :collapse-tags="projectForm.memberUserIds.length > 10" :max-collapse-tags="10" collapse-tags-tooltip @change="handleMemberChange">
              <el-option v-for="item in memberCandidateOptions" :key="item.id" :label="formatUserLabel(item)" :value="item.id" :disabled="isCreatorLockedMember(item.id)" />
            </el-select>
            <p v-if="showCreatorHint" class="member-hint">{{ texts.creatorHint }}</p>
            <p v-else class="member-hint">{{ texts.memberHint }}</p>
          </el-form-item>
          <section class="switch-section">
            <h4 class="switch-section__title">{{ texts.templateSection }}</h4>
            <div class="template-config-panel">
              <div class="template-config-item">
                <div class="template-config-item__copy">
                  <span class="switch-item__title">{{ texts.projectTemplateSelect }}</span>
                  <p class="switch-item__desc">{{ texts.projectTemplateSelectDesc }}</p>
                </div>
                <el-select v-model="projectForm.templateId" class="full-width" :placeholder="texts.projectTemplateSelectPlaceholder" clearable filterable>
                  <el-option v-for="item in templateOptions" :key="item.id" :label="item.templateName" :value="item.id" />
                </el-select>
              </div>
              <div class="template-config-item">
                <div class="template-config-item__copy">
                  <span class="switch-item__title">{{ texts.fileExtensionsOverride }}</span>
                  <p class="switch-item__desc">{{ texts.fileExtensionsOverrideDesc }}</p>
                </div>
                <el-input v-model="projectForm.supportedFileExtensions" type="textarea" :rows="3" :placeholder="texts.fileExtensionsPlaceholder" />
              </div>
              <div class="template-config-item">
                <div class="template-config-item__copy">
                  <span class="switch-item__title">{{ texts.projectPrompt }}</span>
                  <p class="switch-item__desc">{{ texts.projectPromptDesc }}</p>
                </div>
                <el-input v-model="projectForm.promptContent" type="textarea" :rows="8" :placeholder="texts.projectPromptPlaceholder" />
              </div>
            </div>
          </section>
          <section class="switch-section">
            <h4 class="switch-section__title">{{ texts.switchSection }}</h4>
            <div class="switch-panel">
              <div class="switch-item">
                <div class="switch-item__copy"><span class="switch-item__title">{{ texts.aiReview }}</span><p class="switch-item__desc">{{ texts.aiReviewDesc }}</p></div>
                <el-switch v-model="projectForm.aiReviewEnabled" />
                <div v-if="projectForm.aiReviewEnabled" class="switch-item__extra">
                  <div class="switch-item__extra-head"><span class="switch-item__title">{{ texts.reviewModel }}</span></div>
                  <el-select v-model="projectForm.llmModelId" class="full-width" :placeholder="texts.reviewModelPlaceholder" clearable filterable>
                    <el-option v-for="item in projectLlmModelOptions" :key="item.id" :label="formatLlmModelLabel(item)" :value="item.id" />
                  </el-select>
                </div>
              </div>
              <div class="switch-item">
                <div class="switch-item__copy">
                  <span class="switch-item__title">{{ texts.reviewContext }}</span>
                  <p class="switch-item__desc">{{ texts.reviewContextDesc }}</p>
                </div>
                <el-switch v-model="projectForm.reviewContextEnabled" />
              </div>
              <div class="switch-item">
                <div class="switch-item__copy"><span class="switch-item__title">{{ texts.gitlabNote }}</span><p class="switch-item__desc">{{ texts.gitlabNoteDesc }}</p></div>
                <el-switch v-model="projectForm.gitlabNoteEnabled" />
              </div>
              <div class="switch-item">
                <div class="switch-item__copy"><span class="switch-item__title">{{ texts.wecomNotify }}</span><p class="switch-item__desc">{{ texts.wecomNotifyDesc }}</p></div>
                <el-switch v-model="projectForm.wecomNotifyEnabled" />
                <div v-if="projectForm.wecomNotifyEnabled" class="switch-item__extra">
                  <div class="switch-item__extra-head"><span class="switch-item__title">{{ texts.wecomWebhook }}</span></div>
                  <el-input v-model="projectForm.wecomWebhookUrl" :placeholder="texts.wecomWebhookPlaceholder" clearable />
                </div>
              </div>
              <div class="switch-item">
                <div class="switch-item__copy"><span class="switch-item__title">{{ texts.active }}</span><p class="switch-item__desc">{{ texts.activeDesc }}</p></div>
                <el-switch v-model="projectForm.active" />
              </div>
            </div>
          </section>
        </el-form>
      </div>
      <div class="drawer-footer">
        <el-button @click="dialogVisible = false">{{ projectReadOnly ? texts.close : texts.cancel }}</el-button>
        <el-button v-if="!projectReadOnly" type="warning" :loading="saving" @click="submitProject">{{ texts.save }}</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="customReviewDialogVisible" :title="texts.customReviewDialogTitle" width="640px" destroy-on-close class="custom-review-dialog">
      <el-form ref="customReviewFormRef" :model="customReviewForm" :rules="customReviewRules" label-position="top" class="project-form custom-review-form">
        <section class="switch-section custom-review-section">
          <h4 class="switch-section__title">{{ texts.customReviewBasicInfo }}</h4>
          <el-form-item :label="texts.projectName">
            <el-input :model-value="customReviewForm.projectName" readonly />
          </el-form-item>
          <el-form-item :label="texts.reviewBranches">
            <el-select
              v-model="customReviewForm.selectedReviewBranches"
              class="full-width"
              multiple
              collapse-tags
              collapse-tags-tooltip
              :max-collapse-tags="4"
              :placeholder="texts.customReviewBranchPlaceholder"
              :disabled="customReviewForm.reviewBranchOptions.length === 0"
            >
              <el-option
                v-for="branch in customReviewForm.reviewBranchOptions"
                :key="branch"
                :label="branch"
                :value="branch"
              />
            </el-select>
            <p class="member-hint">{{ texts.customReviewBranchScopeHint }}</p>
          </el-form-item>
          <el-form-item :label="texts.customReviewTimeRange" prop="dateRange">
            <el-config-provider :locale="zhCn">
              <el-date-picker
                v-model="customReviewForm.dateRange"
                class="full-width"
                type="datetimerange"
                :range-separator="texts.customReviewDateRangeSeparator"
                :start-placeholder="texts.customReviewDateRangeStart"
                :end-placeholder="texts.customReviewDateRangeEnd"
                value-format="YYYY-MM-DD HH:mm:ss"
                unlink-panels
              />
            </el-config-provider>
          </el-form-item>
          <el-form-item :label="texts.customReviewMode" prop="reviewMode">
            <div class="custom-review-mode-block">
              <el-radio-group v-model="customReviewForm.reviewMode" class="custom-review-mode-group">
                <el-radio label="SKIP_REVIEWED" border>{{ texts.skipReviewed }}</el-radio>
                <el-radio label="FORCE_REREVIEW" border>{{ texts.forceRereview }}</el-radio>
              </el-radio-group>
              <p class="member-hint custom-review-mode-note">{{ texts.customReviewModeHint }}</p>
            </div>
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="customReviewDialogVisible = false">{{ texts.cancel }}</el-button>
          <el-button type="warning" :loading="customReviewSubmitting" @click="submitCustomReview">{{ texts.customReviewSubmit }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="templateDialogVisible" :title="templateDialogMode === 'create' ? texts.createTemplate : texts.editTemplate" size="640px" destroy-on-close class="project-drawer">
      <div class="drawer-body" v-loading="templateDrawerLoading">
        <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-position="top" class="project-form">
          <el-form-item :label="texts.templateName" prop="templateName"><el-input v-model="templateForm.templateName" :placeholder="texts.templateNamePlaceholder" /></el-form-item>
          <el-form-item :label="texts.templateDesc"><el-input v-model="templateForm.templateDesc" :placeholder="texts.templateDescPlaceholder" /></el-form-item>
          <el-form-item :label="texts.fileExtensions"><el-input v-model="templateForm.fileExtensions" type="textarea" :rows="3" :placeholder="texts.fileExtensionsPlaceholder" /></el-form-item>
          <el-form-item :label="texts.baseReviewPrompt"><el-input v-model="templateForm.baseReviewPrompt" type="textarea" :rows="14" :placeholder="texts.baseReviewPromptPlaceholder" /></el-form-item>
        </el-form>
      </div>
      <div class="drawer-footer">
        <el-button @click="templateDialogVisible = false">{{ texts.cancel }}</el-button>
        <el-button type="warning" :loading="templateSaving" @click="submitTemplate">{{ texts.save }}</el-button>
      </div>
    </el-drawer>

    <el-drawer v-model="templateDetailVisible" :title="texts.viewTemplate" size="640px" destroy-on-close class="project-drawer">
      <div class="drawer-body">
        <el-form label-position="top" class="project-form">
          <el-form-item :label="texts.templateName">
            <el-input :model-value="templateDetail.templateName || '--'" readonly />
          </el-form-item>
          <el-form-item :label="texts.templateDesc">
            <el-input :model-value="templateDetail.templateDesc || '--'" readonly />
          </el-form-item>
          <el-form-item :label="texts.fileExtensions">
            <el-input :model-value="templateDetail.fileExtensions || '--'" type="textarea" :rows="3" readonly />
          </el-form-item>
          <el-form-item :label="texts.baseReviewPrompt">
            <el-input :model-value="templateDetail.baseReviewPrompt || '--'" type="textarea" :rows="14" readonly />
          </el-form-item>
        </el-form>
      </div>
      <div class="drawer-footer">
        <el-button @click="templateDetailVisible = false">{{ texts.close }}</el-button>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import {
  createProject,
  createProjectCustomReviewBatch,
  deleteProject,
  fetchGitLabBranches,
  fetchProjectDetail,
  fetchProjectLlmModels,
  fetchProjects,
  testGitLabProject,
  updateProject,
  type GitLabBranchOption,
  type ProjectCustomReviewBatchPayload,
  type ProjectCustomReviewBatchResponse,
  type ProjectUpsertPayload
} from "../../api/projects";
import { createProjectTemplate, deleteProjectTemplate, fetchProjectTemplateDetail, fetchProjectTemplates, updateProjectTemplate, type ProjectTemplateUpsertPayload } from "../../api/project-templates";
import { fetchLlmModels } from "../../api/llm";
import { fetchProjectUsers, fetchUsers } from "../../api/users";
import { useAuthStore } from "../../stores/auth";

interface ProjectMemberOption { id: number; displayName: string; username: string; role: string; status: string; }
interface LlmModelOption { id: number; configName: string; providerCode: string; modelName: string; scopeType: string; }
interface ProjectItem {
  id: number; projectName: string; sourcePlatform: string; gitlabProjectId: number | null; gitlabProjectUrl: string; gitlabWebhookToken: string;
  reviewBranches?: string | null;
  ownerUserId?: number | null; templateId?: number | null; templateName?: string | null; supportedFileExtensions?: string | null; llmModelId?: number | null; aiReviewEnabled: boolean; reviewContextEnabled?: boolean; gitlabNoteEnabled: boolean; wecomNotifyEnabled: boolean;
  wecomWebhookUrl: string; promptContent: string; active: boolean; updatedAt: string | null;
}
interface ProjectForm {
  id?: number; projectName: string; sourcePlatform: string; gitlabProjectUrl: string; gitlabWebhookToken: string; ownerUserId?: number | null;
  reviewBranches: string[]; templateId?: number | null; supportedFileExtensions: string; memberUserIds: number[]; llmModelId?: number | null; aiReviewEnabled: boolean; reviewContextEnabled: boolean; gitlabNoteEnabled: boolean; wecomNotifyEnabled: boolean;
  wecomWebhookUrl: string; promptContent: string; active: boolean;
}
interface ReviewBranchOption extends GitLabBranchOption { missing?: boolean; }
interface TemplateOption {
  id: number; templateName: string; templateDesc?: string;
}
interface ProjectTemplateItem {
  id: number; templateName: string; templateDesc?: string; fileExtensions?: string; baseReviewPrompt?: string; createdBy?: number | null; manageable?: boolean; updatedAt?: string | null;
}
interface ProjectTemplateForm {
  id?: number; templateName: string; templateDesc: string; fileExtensions: string; baseReviewPrompt: string;
}
interface CustomReviewForm {
  projectId?: number;
  projectName: string;
  selectedReviewBranches: string[];
  reviewBranchOptions: string[];
  dateRange: [string, string] | [];
  reviewMode: ProjectCustomReviewBatchPayload["reviewMode"];
}

const texts = {
  projectManagementTab: "\u9879\u76ee\u7ba1\u7406",
  projectTemplateManagementTab: "\u9879\u76ee\u6a21\u677f\u7ba1\u7406",
  projectName: "\u9879\u76ee\u540d\u79f0", projectNamePlaceholder: "\u8bf7\u8f93\u5165\u9879\u76ee\u540d\u79f0", gitlabUrlPlaceholder: "\u8bf7\u8f93\u5165 GitLab URL", gitlabRealUrlPlaceholder: "\u8bf7\u8f93\u5165\u771f\u5b9e GitLab \u9879\u76ee URL",
  aiReviewStatus: "AI \u5ba1\u67e5\u72b6\u6001", wecomStatus: "\u4f01\u5fae\u901a\u77e5\u72b6\u6001", enabled: "\u5f00\u542f", disabled: "\u5173\u95ed", activeEnabled: "\u542f\u7528", activeDisabled: "\u505c\u7528",
  createTemplate: "\u65b0\u5efa\u6a21\u677f", editTemplate: "\u7f16\u8f91\u6a21\u677f", templateName: "\u6a21\u677f\u540d\u79f0", templateNamePlaceholder: "\u8bf7\u8f93\u5165\u6a21\u677f\u540d\u79f0", templateDesc: "\u6a21\u677f\u63cf\u8ff0", templateDescPlaceholder: "\u8bf7\u8f93\u5165\u6a21\u677f\u63cf\u8ff0", fileExtensions: "\u9002\u7528\u6587\u4ef6\u540e\u7f00", fileExtensionsPlaceholder: "\u4f8b\u5982\uff1a.java,.xml,.properties,.yml", reviewPromptStatus: "Review \u63d0\u793a\u8bcd", configured: "\u5df2\u914d\u7f6e", notConfigured: "\u672a\u914d\u7f6e", baseReviewPrompt: "\u57fa\u7840 review \u63d0\u793a\u8bcd\u6a21\u677f", baseReviewPromptPlaceholder: "\u8bf7\u8f93\u5165\u6a21\u677f\u7ea7\u57fa\u7840 review \u63d0\u793a\u8bcd",
  templateSection: "\u6a21\u677f\u914d\u7f6e", projectTemplateSelect: "\u9879\u76ee\u6a21\u677f", projectTemplateSelectPlaceholder: "\u8bf7\u9009\u62e9\u9879\u76ee\u6a21\u677f", projectTemplateSelectDesc: "\u9009\u62e9\u9879\u76ee\u6240\u5c5e\u7684\u5ba1\u67e5\u6a21\u677f\uff0c\u7528\u4e8e\u63d0\u4f9b\u9ed8\u8ba4\u6587\u4ef6\u540e\u7f00\u548c Prompt \u914d\u7f6e\u3002", fileExtensionsOverride: "\u652f\u6301\u7684\u6587\u4ef6\u6269\u5c55\u540d", fileExtensionsOverrideDesc: "\u7559\u7a7a\u4e14\u5df2\u9009\u6a21\u677f\u65f6\uff0c\u4f7f\u7528\u6a21\u677f\u914d\u7f6e\u3002", projectPromptDesc: "\u7559\u7a7a\u4e14\u5df2\u9009\u6a21\u677f\u65f6\uff0c\u4f7f\u7528\u6a21\u677f\u63d0\u793a\u8bcd\uff1b\u5426\u5219\u4f18\u5148\u4f7f\u7528\u9879\u76ee Prompt\u3002",
  search: "\u67e5\u8be2", reset: "\u91cd\u7f6e", createProject: "\u65b0\u5efa\u9879\u76ee", editProject: "\u7f16\u8f91\u9879\u76ee", viewProject: "\u67e5\u770b\u9879\u76ee", aiReview: "AI \u5ba1\u67e5", reviewContext: "\u6df1\u5ea6\u5ba1\u67e5", gitlabNote: "GitLab \u56de\u5199", wecomNotify: "\u4f01\u5fae\u901a\u77e5", active: "\u662f\u5426\u542f\u7528", updatedAt: "\u66f4\u65b0\u65f6\u95f4", actions: "\u64cd\u4f5c",
  edit: "\u7f16\u8f91", delete: "\u5220\u9664", moreActions: "\u66f4\u591a\u64cd\u4f5c", customReview: "\u81ea\u5b9a\u4e49\u5ba1\u67e5", customReviewDialogTitle: "\u81ea\u5b9a\u4e49\u5ba1\u67e5", customReviewBasicInfo: "\u5ba1\u67e5\u8303\u56f4", customReviewBranchPlaceholder: "\u8bf7\u9009\u62e9\u5ba1\u67e5\u5206\u652f", customReviewBranchScopeHint: "\u4ec5\u672c\u6b21\u9009\u4e2d\u7684\u5206\u652f\u4f1a\u53c2\u4e0e\u6279\u91cf\u5ba1\u67e5\uff0c\u9009\u62e9\u8303\u56f4\u9650\u5b9a\u5728\u9879\u76ee reviewBranches \u5185\u3002", customReviewTimeRange: "\u63d0\u4ea4\u65f6\u95f4\u533a\u95f4", customReviewDateRangeSeparator: "\u81f3", customReviewDateRangeStart: "\u5f00\u59cb\u65f6\u95f4", customReviewDateRangeEnd: "\u7ed3\u675f\u65f6\u95f4", customReviewMode: "\u5904\u7406\u65b9\u5f0f", skipReviewed: "\u8df3\u8fc7\u5df2\u5ba1\u67e5", forceRereview: "\u5f3a\u5236\u91cd\u5ba1", customReviewModeHint: "\u9ed8\u8ba4\u8df3\u8fc7\u5df2\u6709\u7ed3\u679c\u7684 commit\u3002\u5982\u9700\u8981\u8986\u76d6\u65e7\u7ed3\u679c\uff0c\u518d\u9009\u62e9\u5f3a\u5236\u91cd\u5ba1\u3002", customReviewSubmit: "\u5f00\u59cb\u5ba1\u67e5", validateCustomReviewBranches: "\u8bf7\u9009\u62e9\u81f3\u5c11\u4e00\u4e2a\u5ba1\u67e5\u5206\u652f", validateCustomReviewTimeRange: "\u8bf7\u9009\u62e9\u63d0\u4ea4\u65f6\u95f4\u533a\u95f4", customReviewSuccessPrefix: "\u81ea\u5b9a\u4e49\u5ba1\u67e5\u5df2\u63d0\u4ea4\uff1a", customReviewFail: "\u81ea\u5b9a\u4e49\u5ba1\u67e5\u63d0\u4ea4\u5931\u8d25", customReviewAllBranches: "\u5168\u90e8\u5206\u652f", totalPrefix: "\u5171", totalSuffix: "\u6761", pageSize10: "10\u6761/\u9875", pageSize20: "20\u6761/\u9875", pageSize50: "50\u6761/\u9875",
  view: "\u67e5\u770b", viewTemplate: "\u67e5\u770b\u9879\u76ee\u6a21\u677f", close: "\u5173\u95ed",
  webhookToken: "Webhook Token", webhookTokenPlaceholder: "\u8bf7\u8f93\u5165 GitLab Webhook Token", testGitLab: "\u6d4b\u8bd5 GitLab", testConnection: "\u6d4b\u8bd5\u8fde\u63a5", projectOwner: "\u9879\u76ee Owner", projectOwnerPlaceholder: "\u8bf7\u9009\u62e9\u9879\u76ee Owner",
  projectMembers: "\u9879\u76ee\u6210\u5458", projectMembersPlaceholder: "\u8bf7\u9009\u62e9\u9879\u76ee\u6210\u5458", creatorHint: "\u521b\u5efa\u540e\u4f60\u5c06\u81ea\u52a8\u6210\u4e3a\u8be5\u9879\u76ee\u7684 Owner \u548c\u6210\u5458\uff0c\u53ef\u989d\u5916\u6307\u5b9a\u5176\u4ed6\u9879\u76ee\u6210\u5458\u3002", memberHint: "\u9879\u76ee\u6210\u5458\u652f\u6301\u591a\u9009\uff0c\u9879\u76ee Owner \u4f1a\u81ea\u52a8\u4fdd\u6301\u5728\u6210\u5458\u5217\u8868\u4e2d\u3002",
  reviewBranches: "\u5ba1\u67e5\u5206\u652f", reviewBranchesPlaceholder: "\u8bf7\u9009\u62e9\u9700\u8981 AI \u5ba1\u67e5\u7684\u5206\u652f", reviewBranchesHint: "\u7559\u7a7a\u8868\u793a\u6240\u6709\u5206\u652f\u90fd\u89e6\u53d1\u5ba1\u67e5\uff1b\u5df2\u5220\u9664\u7684\u5df2\u9009\u5206\u652f\u4f1a\u4fdd\u7559\uff0c\u9700\u8981\u624b\u52a8\u53d6\u6d88\u624d\u4f1a\u79fb\u9664\u3002", refreshBranches: "\u5237\u65b0\u5206\u652f", loadBranchesFail: "\u52a0\u8f7d GitLab \u5206\u652f\u5931\u8d25", defaultBranch: "\u9ed8\u8ba4", protectedBranch: "\u4fdd\u62a4", missingBranch: "\u5f53\u524d\u4e0d\u5b58\u5728",
  switchSection: "\u529f\u80fd\u5f00\u5173", aiReviewDesc: "\u63a7\u5236\u9879\u76ee\u662f\u5426\u81ea\u52a8\u89e6\u53d1 AI \u4ee3\u7801\u5ba1\u67e5\u3002", reviewContextDesc: "\u5f00\u542f\u540e\u4f1a\u81ea\u52a8\u62c9\u53d6\u5b8c\u6574\u6587\u4ef6\u5e76\u6269\u5c55\u5230\u51fd\u6570\u6216\u7c7b\u7ea7\u4e0a\u4e0b\u6587\uff0c\u5ba1\u67e5\u66f4\u51c6\u786e\uff0c\u4f46\u4f1a\u589e\u52a0 token \u6d88\u8017\u548c\u8d39\u7528\u3002", reviewModel: "\u5ba1\u67e5\u6a21\u578b", reviewModelPlaceholder: "\u8bf7\u9009\u62e9\u9879\u76ee\u4f7f\u7528\u7684\u5927\u6a21\u578b",
  gitlabNoteDesc: "\u5c06\u5ba1\u67e5\u7ed3\u679c\u81ea\u52a8\u56de\u5199\u5230 GitLab \u5907\u6ce8\u4e2d\u3002", wecomNotifyDesc: "\u6839\u636e\u9879\u76ee\u914d\u7f6e\u5411\u4f01\u4e1a\u5fae\u4fe1\u63a8\u9001\u5ba1\u67e5\u7ed3\u679c\u3002", wecomWebhook: "\u4f01\u5fae Webhook", wecomWebhookPlaceholder: "\u8bf7\u8f93\u5165\u4f01\u4e1a\u5fae\u4fe1 Webhook",
  activeDesc: "\u5173\u95ed\u540e\u9879\u76ee\u5c06\u4e0d\u518d\u53c2\u4e0e\u81ea\u52a8\u5ba1\u67e5\u6d41\u7a0b\u3002", projectPrompt: "\u9879\u76ee Prompt", projectPromptPlaceholder: "\u586b\u5199\u9879\u76ee\u7ea7\u8865\u5145\u89c4\u8303\uff0c\u4e3a\u7a7a\u65f6\u53ea\u4f7f\u7528\u516c\u5171\u89c4\u5219",
  cancel: "\u53d6\u6d88", save: "\u4fdd\u5b58", validateProjectName: "\u8bf7\u8f93\u5165\u9879\u76ee\u540d\u79f0", validateGitlabUrl: "\u8bf7\u8f93\u5165 GitLab \u9879\u76ee URL", warningOwner: "\u8bf7\u9009\u62e9\u9879\u76ee Owner", warningGitlabUrl: "\u8bf7\u5148\u586b\u5199 GitLab URL", warningGitlabToken: "\u8bf7\u5148\u586b\u5199 Webhook Token",
  warningReviewModel: "\u5f00\u542f AI \u5ba1\u67e5\u65f6\u5fc5\u987b\u9009\u62e9\u5ba1\u67e5\u6a21\u578b", warningWecomWebhook: "\u5f00\u542f\u4f01\u5fae\u901a\u77e5\u65f6\u5fc5\u987b\u586b\u5199\u4f01\u5fae Webhook",
  gitlabSuccessPrefix: "GitLab \u8fde\u63a5\u6210\u529f\uff1a", gitlabFail: "\u6d4b\u8bd5 GitLab \u5931\u8d25", loadProjectConfigFail: "\u52a0\u8f7d\u9879\u76ee\u914d\u7f6e\u6570\u636e\u5931\u8d25", loadProjectDetailFail: "\u52a0\u8f7d\u9879\u76ee\u8be6\u60c5\u5931\u8d25",
  createSuccess: "\u9879\u76ee\u521b\u5efa\u6210\u529f", updateSuccess: "\u9879\u76ee\u66f4\u65b0\u6210\u529f", createFail: "\u9879\u76ee\u521b\u5efa\u5931\u8d25", updateFail: "\u9879\u76ee\u66f4\u65b0\u5931\u8d25",
  deleteConfirmTitle: "\u5220\u9664\u786e\u8ba4", deleteConfirmPrefix: "\u786e\u8ba4\u5220\u9664\u9879\u76ee\u201c", deleteConfirmSuffix: "\u201d\u5417\uff1f", confirm: "\u786e\u8ba4", deleteSuccess: "\u9879\u76ee\u5220\u9664\u6210\u529f", deleteFail: "\u9879\u76ee\u5220\u9664\u5931\u8d25", loadProjectListFail: "\u52a0\u8f7d\u9879\u76ee\u5217\u8868\u5931\u8d25",
  validateTemplateName: "\u8bf7\u8f93\u5165\u6a21\u677f\u540d\u79f0", createTemplateSuccess: "\u9879\u76ee\u6a21\u677f\u521b\u5efa\u6210\u529f", updateTemplateSuccess: "\u9879\u76ee\u6a21\u677f\u66f4\u65b0\u6210\u529f", createTemplateFail: "\u9879\u76ee\u6a21\u677f\u521b\u5efa\u5931\u8d25", updateTemplateFail: "\u9879\u76ee\u6a21\u677f\u66f4\u65b0\u5931\u8d25", deleteTemplateSuccess: "\u9879\u76ee\u6a21\u677f\u5220\u9664\u6210\u529f", deleteTemplateFail: "\u9879\u76ee\u6a21\u677f\u5220\u9664\u5931\u8d25", loadTemplateListFail: "\u52a0\u8f7d\u9879\u76ee\u6a21\u677f\u5217\u8868\u5931\u8d25", loadTemplateDetailFail: "\u52a0\u8f7d\u9879\u76ee\u6a21\u677f\u8be6\u60c5\u5931\u8d25", deleteTemplateConfirmPrefix: "\u786e\u8ba4\u5220\u9664\u6a21\u677f\u201c"
} as const;

const authStore = useAuthStore();
const loading = ref(false); const saving = ref(false); const drawerLoading = ref(false); const gitlabTesting = ref(false); const branchLoading = ref(false);
const templateLoading = ref(false); const templateSaving = ref(false); const templateDrawerLoading = ref(false); const customReviewSubmitting = ref(false);
const activeTab = ref("project-management");
const projects = ref<ProjectItem[]>([]); const selectableUsers = ref<ProjectMemberOption[]>([]); const llmModelOptions = ref<LlmModelOption[]>([]); const templates = ref<ProjectTemplateItem[]>([]); const templateOptions = ref<TemplateOption[]>([]); const gitlabBranchOptions = ref<GitLabBranchOption[]>([]);
const dialogVisible = ref(false); const dialogMode = ref<"create" | "edit" | "view">("create"); const projectFormRef = ref<FormInstance>();
const customReviewDialogVisible = ref(false); const customReviewFormRef = ref<FormInstance>();
const templateDialogVisible = ref(false); const templateDialogMode = ref<"create" | "edit">("create"); const templateFormRef = ref<FormInstance>();
const templateDetailVisible = ref(false);
const templateDetail = reactive<ProjectTemplateForm>({ templateName: "", templateDesc: "", fileExtensions: "", baseReviewPrompt: "" });
const pagination = reactive({ pageNo: 1, pageSize: 10, total: 0 });
const templatePagination = reactive({ pageNo: 1, pageSize: 10, total: 0 });
const queryForm = reactive({ projectName: "", gitlabProjectUrl: "", aiReviewEnabled: undefined as boolean | undefined, wecomNotifyEnabled: undefined as boolean | undefined });
const templateQueryForm = reactive({ templateName: "" });
const activeQuery = reactive({ projectName: "", gitlabProjectUrl: "", aiReviewEnabled: undefined as boolean | undefined, wecomNotifyEnabled: undefined as boolean | undefined });
const activeTemplateQuery = reactive({ templateName: "" });
const projectForm = reactive<ProjectForm>({ projectName: "", sourcePlatform: "gitlab", gitlabProjectUrl: "", gitlabWebhookToken: "", reviewBranches: [], ownerUserId: null, templateId: null, supportedFileExtensions: "", memberUserIds: [], llmModelId: null, aiReviewEnabled: true, reviewContextEnabled: true, gitlabNoteEnabled: true, wecomNotifyEnabled: false, wecomWebhookUrl: "", promptContent: "", active: true });
const projectRules: FormRules<ProjectForm> = { projectName: [{ required: true, message: texts.validateProjectName, trigger: "blur" }], gitlabProjectUrl: [{ required: true, message: texts.validateGitlabUrl, trigger: "blur" }], gitlabWebhookToken: [{ required: true, message: texts.warningGitlabToken, trigger: "blur" }] };
const customReviewForm = reactive<CustomReviewForm>({ projectId: undefined, projectName: "", selectedReviewBranches: [], reviewBranchOptions: [], dateRange: [], reviewMode: "SKIP_REVIEWED" });
const customReviewRules: FormRules<CustomReviewForm> = {
  selectedReviewBranches: [{
    validator: (_rule, value, callback) => {
      if (!customReviewForm.reviewBranchOptions.length) {
        callback();
        return;
      }
      if (Array.isArray(value) && value.length > 0) {
        callback();
        return;
      }
      callback(new Error(texts.validateCustomReviewBranches));
    },
    trigger: "change"
  }],
  dateRange: [{
    validator: (_rule, value, callback) => {
      if (Array.isArray(value) && value.length === 2 && value[0] && value[1]) {
        callback();
        return;
      }
      callback(new Error(texts.validateCustomReviewTimeRange));
    },
    trigger: "change"
  }]
};
const templateForm = reactive<ProjectTemplateForm>({ templateName: "", templateDesc: "", fileExtensions: "", baseReviewPrompt: "" });
const templateRules: FormRules<ProjectTemplateForm> = { templateName: [{ required: true, message: texts.validateTemplateName, trigger: "blur" }] };
const currentUserId = computed(() => authStore.user?.userId ?? null); const showOwnerField = computed(() => authStore.isAdmin || dialogMode.value !== "create"); const showCreatorHint = computed(() => dialogMode.value === "create" && !authStore.isAdmin);
const projectReadOnly = computed(() => dialogMode.value === "view");
const memberCandidateOptions = computed(() => selectableUsers.value.filter((item) => item.status === "ENABLE" && item.role !== "ADMIN")); const ownerCandidateOptions = computed(() => memberCandidateOptions.value); const projectLlmModelOptions = computed(() => llmModelOptions.value);
const reviewBranchOptions = computed<ReviewBranchOption[]>(() => {
  const selected = new Set(projectForm.reviewBranches);
  const remoteNames = new Set(gitlabBranchOptions.value.map((item) => item.name));
  const options: ReviewBranchOption[] = gitlabBranchOptions.value.map((item) => ({ ...item, missing: false }));
  selected.forEach((name) => {
    if (name && !remoteNames.has(name)) options.push({ name, missing: true });
  });
  return options;
});
const normalizeText = (value: string | null | undefined) => typeof value === "string" && value.trim() ? value.trim() : undefined;
const normalizeUserIds = (value: number[] | undefined | null) => Array.from(new Set((value || []).filter((item) => typeof item === "number" && Number.isFinite(item))));
const parseBranches = (value: string | null | undefined) => Array.from(new Set((value || "").split(/[,，\s]+/).map((item) => item.trim()).filter(Boolean)));
const serializeBranches = (value: string[]) => normalizeText(Array.from(new Set(value.map((item) => item.trim()).filter(Boolean))).join(","));
const buildCustomReviewSummary = (result: ProjectCustomReviewBatchResponse) => {
  const parts = [
    `batch #${result.batchId}`,
    `\u547d\u4e2d ${result.totalCommitCount || 0} \u4e2a commit`,
    `\u65b0\u5efa ${result.createdTaskCount || 0} \u4e2a\u4efb\u52a1`,
    `\u91cd\u5ba1 ${result.retriedTaskCount || 0} \u4e2a`,
    `\u8df3\u8fc7\u5df2\u5ba1\u67e5 ${result.skippedReviewedCount || 0} \u4e2a`,
    `\u8df3\u8fc7\u8fd0\u884c\u4e2d ${result.skippedRunningCount || 0} \u4e2a`,
    `\u8df3\u8fc7\u5931\u8d25 ${result.skippedFailedCount || 0} \u4e2a`
  ];
  if ((result.failedCount || 0) > 0) parts.push(`\u5931\u8d25 ${result.failedCount} \u4e2a`);
  return `${texts.customReviewSuccessPrefix}${parts.join("，")}`;
};
const ensureCreatorDefaults = () => { if (dialogMode.value === "create" && !authStore.isAdmin && currentUserId.value) { if (!projectForm.memberUserIds.includes(currentUserId.value)) projectForm.memberUserIds = [...projectForm.memberUserIds, currentUserId.value]; projectForm.ownerUserId = currentUserId.value; } };
const applyFilters = () => { activeQuery.projectName = normalizeText(queryForm.projectName) || ""; activeQuery.gitlabProjectUrl = normalizeText(queryForm.gitlabProjectUrl) || ""; activeQuery.aiReviewEnabled = queryForm.aiReviewEnabled; activeQuery.wecomNotifyEnabled = queryForm.wecomNotifyEnabled; };
const applyTemplateFilters = () => { activeTemplateQuery.templateName = normalizeText(templateQueryForm.templateName) || ""; };
const canEditProject = (row: ProjectItem) => authStore.isAdmin || row.ownerUserId === currentUserId.value;
const canCustomReviewProject = (row: ProjectItem) => authStore.isAdmin || row.ownerUserId === currentUserId.value;
const isCreatorLockedMember = (userId: number) => showCreatorHint.value && currentUserId.value === userId;
const canManageTemplate = (row: ProjectTemplateItem) => Boolean(row.manageable);
const openViewTemplateDialog = (row: ProjectTemplateItem) => {
  templateDetail.templateName = row.templateName || "";
  templateDetail.templateDesc = row.templateDesc || "";
  templateDetail.fileExtensions = row.fileExtensions || "";
  templateDetail.baseReviewPrompt = row.baseReviewPrompt || "";
  templateDetailVisible.value = true;
};
const formatUserLabel = (item: ProjectMemberOption) => { const displayName = (item.displayName || "").trim(); const username = (item.username || "").trim(); if (!displayName) return username; if (!username || displayName === username) return displayName; return `${displayName} (${username})`; };
const loadSelectableUsers = async () => {
  const response = await fetchUsers({ pageNo: 1, pageSize: 500, status: "ENABLE" });
  const records = response.data.data?.records || [];
  selectableUsers.value = records.filter((item: ProjectMemberOption) => item.status === "ENABLE" && item.role !== "ADMIN").map((item: ProjectMemberOption) => ({ id: item.id, displayName: item.displayName || "", username: item.username || "", role: item.role || "", status: item.status || "" }));
};
const loadGlobalLlmModels = async () => {
  const response = await fetchLlmModels({ pageNo: 1, pageSize: 500, scopeType: "GLOBAL", enabled: true });
  llmModelOptions.value = (response.data.data?.records || []).map((item: LlmModelOption) => ({ id: item.id, configName: item.configName || "", providerCode: item.providerCode || "", modelName: item.modelName || "", scopeType: item.scopeType || "GLOBAL" }));
};
const loadProjectLlmModels = async (projectId: number) => {
  const response = await fetchProjectLlmModels(projectId);
  llmModelOptions.value = (response.data.data || []).map((item: LlmModelOption) => ({ id: item.id, configName: item.configName || "", providerCode: item.providerCode || "", modelName: item.modelName || "", scopeType: item.scopeType || "GLOBAL" }));
};
const loadTemplateOptions = async () => {
  const response = await fetchProjectTemplates({ pageNo: 1, pageSize: 500 });
  const records = response.data.data?.records || [];
  templateOptions.value = records.map((item: TemplateOption) => ({ id: item.id, templateName: item.templateName || "", templateDesc: item.templateDesc || "" }));
};
const formatLlmModelLabel = (item: LlmModelOption) => { const modelName = (item.modelName || "").trim(); const providerCode = (item.providerCode || "").trim(); return modelName ? `${item.configName} (${providerCode} / ${modelName})` : item.configName; };
const formatBranchLabel = (item: ReviewBranchOption) => {
  const labels = [];
  if (item.defaultBranch) labels.push(texts.defaultBranch);
  if (item.protectedBranch) labels.push(texts.protectedBranch);
  if (item.missing) labels.push(texts.missingBranch);
  return labels.length ? `${item.name}（${labels.join(" / ")}）` : item.name;
};
const loadGitLabBranchOptions = async (showError = true) => {
  const gitlabProjectUrl = normalizeText(projectForm.gitlabProjectUrl);
  const gitlabWebhookToken = normalizeText(projectForm.gitlabWebhookToken);
  if (!gitlabProjectUrl) { if (showError) ElMessage.warning(texts.warningGitlabUrl); return; }
  if (!gitlabWebhookToken) { if (showError) ElMessage.warning(texts.warningGitlabToken); return; }
  branchLoading.value = true;
  try {
    const response = await fetchGitLabBranches({ gitlabProjectUrl, gitlabWebhookToken });
    gitlabBranchOptions.value = (response.data.data || []).map((item: GitLabBranchOption) => ({ name: item.name, defaultBranch: Boolean(item.defaultBranch), protectedBranch: Boolean(item.protectedBranch) }));
  } catch (error: any) {
    if (showError) ElMessage.error(error?.response?.data?.message || texts.loadBranchesFail);
  } finally { branchLoading.value = false; }
};
const handleLoadBranches = async () => { await loadGitLabBranchOptions(true); };
const handleTestGitLab = async () => {
  const gitlabProjectUrl = normalizeText(projectForm.gitlabProjectUrl);
  const gitlabWebhookToken = normalizeText(projectForm.gitlabWebhookToken);
  if (!gitlabProjectUrl) { ElMessage.warning(texts.warningGitlabUrl); return; }
  if (!gitlabWebhookToken) { ElMessage.warning(texts.warningGitlabToken); return; }
  gitlabTesting.value = true;
  try {
    const response = await testGitLabProject({ gitlabProjectUrl, gitlabWebhookToken });
    const result = response.data.data;
    ElMessage.success(`${texts.gitlabSuccessPrefix}${result.projectName} / ${result.pathWithNamespace} (ID: ${result.gitlabProjectId})`);
    await loadGitLabBranchOptions(false);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || texts.gitlabFail);
  } finally { gitlabTesting.value = false; }
};
const handleSearch = async () => { pagination.pageNo = 1; applyFilters(); await loadProjects(); };
const resetFilters = async () => { queryForm.projectName = ""; queryForm.gitlabProjectUrl = ""; queryForm.aiReviewEnabled = undefined; queryForm.wecomNotifyEnabled = undefined; pagination.pageNo = 1; applyFilters(); await loadProjects(); };
const handleTemplateSearch = async () => { templatePagination.pageNo = 1; applyTemplateFilters(); await loadTemplates(); };
const resetTemplateFilters = async () => { templateQueryForm.templateName = ""; templatePagination.pageNo = 1; applyTemplateFilters(); await loadTemplates(); };
const resetProjectForm = () => { projectForm.id = undefined; projectForm.projectName = ""; projectForm.sourcePlatform = "gitlab"; projectForm.gitlabProjectUrl = ""; projectForm.gitlabWebhookToken = ""; projectForm.reviewBranches = []; gitlabBranchOptions.value = []; projectForm.ownerUserId = null; projectForm.templateId = null; projectForm.supportedFileExtensions = ""; projectForm.memberUserIds = []; projectForm.llmModelId = null; projectForm.aiReviewEnabled = true; projectForm.reviewContextEnabled = true; projectForm.gitlabNoteEnabled = true; projectForm.wecomNotifyEnabled = false; projectForm.wecomWebhookUrl = ""; projectForm.promptContent = ""; projectForm.active = true; };
const resetCustomReviewForm = () => {
  customReviewForm.projectId = undefined;
  customReviewForm.projectName = "";
  customReviewForm.selectedReviewBranches = [];
  customReviewForm.reviewBranchOptions = [];
  customReviewForm.dateRange = [];
  customReviewForm.reviewMode = "SKIP_REVIEWED";
  customReviewFormRef.value?.clearValidate();
};
const resetTemplateForm = () => { templateForm.id = undefined; templateForm.templateName = ""; templateForm.templateDesc = ""; templateForm.fileExtensions = ""; templateForm.baseReviewPrompt = ""; };
const openCreateDialog = async () => {
  dialogMode.value = "create"; resetProjectForm(); drawerLoading.value = true; dialogVisible.value = true;
  try { await Promise.all([loadSelectableUsers(), loadGlobalLlmModels(), loadTemplateOptions()]); ensureCreatorDefaults(); }
  catch (error) { dialogVisible.value = false; ElMessage.error(texts.loadProjectConfigFail); }
  finally { drawerLoading.value = false; }
};
const openProjectDialog = async (mode: "edit" | "view", row: ProjectItem) => {
  dialogMode.value = mode; resetProjectForm(); drawerLoading.value = true; dialogVisible.value = true;
  try {
    const [detailResponse, usersResponse, memberResponse] = await Promise.all([fetchProjectDetail(row.id), fetchUsers({ pageNo: 1, pageSize: 500, status: "ENABLE" }), fetchProjectUsers(row.id), loadProjectLlmModels(row.id), loadTemplateOptions()]);
    const detail = detailResponse.data.data; const members = memberResponse.data.data?.users || [];
    selectableUsers.value = (usersResponse.data.data?.records || []).filter((item: ProjectMemberOption) => item.status === "ENABLE" && item.role !== "ADMIN").map((item: ProjectMemberOption) => ({ id: item.id, displayName: item.displayName || "", username: item.username || "", role: item.role || "", status: item.status || "" }));
    projectForm.id = detail.id; projectForm.projectName = detail.projectName || ""; projectForm.sourcePlatform = detail.sourcePlatform || "gitlab"; projectForm.gitlabProjectUrl = detail.gitlabProjectUrl || ""; projectForm.gitlabWebhookToken = detail.gitlabWebhookToken || ""; projectForm.reviewBranches = parseBranches(detail.reviewBranches); projectForm.ownerUserId = detail.ownerUserId ?? null; projectForm.templateId = detail.templateId ?? null; projectForm.supportedFileExtensions = detail.supportedFileExtensions || ""; projectForm.memberUserIds = normalizeUserIds(members.map((item: ProjectMemberOption) => item.id)); projectForm.llmModelId = detail.llmModelId ?? null; projectForm.aiReviewEnabled = Boolean(detail.aiReviewEnabled); projectForm.reviewContextEnabled = detail.reviewContextEnabled !== false; projectForm.gitlabNoteEnabled = Boolean(detail.gitlabNoteEnabled); projectForm.wecomNotifyEnabled = Boolean(detail.wecomNotifyEnabled); projectForm.wecomWebhookUrl = detail.wecomWebhookUrl || ""; projectForm.promptContent = detail.promptContent || ""; projectForm.active = detail.active !== false;
    if (projectForm.ownerUserId && !projectForm.memberUserIds.includes(projectForm.ownerUserId)) projectForm.memberUserIds = [...projectForm.memberUserIds, projectForm.ownerUserId];
    await loadGitLabBranchOptions(false);
  } catch (error) { dialogVisible.value = false; ElMessage.error(texts.loadProjectDetailFail); }
  finally { drawerLoading.value = false; }
};
const openEditDialog = async (row: ProjectItem) => { await openProjectDialog("edit", row); };
const openViewDialog = async (row: ProjectItem) => { await openProjectDialog("view", row); };
const openCustomReviewDialog = (row: ProjectItem) => {
  if (!canCustomReviewProject(row)) return;
  resetCustomReviewForm();
  const reviewBranches = parseBranches(row.reviewBranches);
  customReviewForm.projectId = row.id;
  customReviewForm.projectName = row.projectName || "";
  customReviewForm.reviewBranchOptions = reviewBranches;
  customReviewForm.selectedReviewBranches = [...reviewBranches];
  customReviewDialogVisible.value = true;
};
const onProjectMoreCommand = (row: ProjectItem, command: string | number | object) => {
  if (typeof command === "string") handleProjectMoreCommand(row, command);
};
const handleProjectMoreCommand = async (row: ProjectItem, command: string) => {
  if (command === "custom-review") openCustomReviewDialog(row);
};
const openCreateTemplateDialog = () => {
  templateDialogMode.value = "create";
  resetTemplateForm();
  templateDialogVisible.value = true;
};
const openEditTemplateDialog = async (row: ProjectTemplateItem) => {
  templateDialogMode.value = "edit";
  resetTemplateForm();
  templateDrawerLoading.value = true;
  templateDialogVisible.value = true;
  try {
    const response = await fetchProjectTemplateDetail(row.id);
    const detail = response.data.data;
    templateForm.id = detail.id;
    templateForm.templateName = detail.templateName || "";
    templateForm.templateDesc = detail.templateDesc || "";
    templateForm.fileExtensions = detail.fileExtensions || "";
    templateForm.baseReviewPrompt = detail.baseReviewPrompt || "";
  } catch (error) {
    templateDialogVisible.value = false;
    ElMessage.error(texts.loadTemplateDetailFail);
  } finally {
    templateDrawerLoading.value = false;
  }
};
const handleOwnerChange = (value: number | null | undefined) => { if (!value) { projectForm.ownerUserId = null; return; } if (!projectForm.memberUserIds.includes(value)) projectForm.memberUserIds = [...projectForm.memberUserIds, value]; };
const handleMemberChange = (value: number[]) => { let nextValues = normalizeUserIds(value); if (showCreatorHint.value && currentUserId.value && !nextValues.includes(currentUserId.value)) nextValues = [...nextValues, currentUserId.value]; projectForm.memberUserIds = nextValues; if (projectForm.ownerUserId && !nextValues.includes(projectForm.ownerUserId)) projectForm.ownerUserId = null; ensureCreatorDefaults(); };
const buildPayload = (): ProjectUpsertPayload => ({ projectName: projectForm.projectName.trim(), sourcePlatform: projectForm.sourcePlatform || "gitlab", gitlabProjectUrl: projectForm.gitlabProjectUrl.trim(), gitlabWebhookToken: normalizeText(projectForm.gitlabWebhookToken), reviewBranches: serializeBranches(projectForm.reviewBranches), ownerUserId: dialogMode.value === "edit" || authStore.isAdmin ? projectForm.ownerUserId ?? null : undefined, templateId: projectForm.templateId ?? null, supportedFileExtensions: normalizeText(projectForm.supportedFileExtensions), memberUserIds: normalizeUserIds(projectForm.memberUserIds), llmModelId: projectForm.llmModelId ?? null, aiReviewEnabled: projectForm.aiReviewEnabled, reviewContextEnabled: projectForm.reviewContextEnabled, gitlabNoteEnabled: projectForm.gitlabNoteEnabled, wecomNotifyEnabled: projectForm.wecomNotifyEnabled, wecomWebhookUrl: normalizeText(projectForm.wecomWebhookUrl), promptContent: projectForm.promptContent, active: projectForm.active });
const buildTemplatePayload = (): ProjectTemplateUpsertPayload => ({ templateName: templateForm.templateName.trim(), templateDesc: normalizeText(templateForm.templateDesc), fileExtensions: normalizeText(templateForm.fileExtensions), baseReviewPrompt: normalizeText(templateForm.baseReviewPrompt) });
const buildCustomReviewPayload = (): ProjectCustomReviewBatchPayload => {
  const dateRange = customReviewForm.dateRange;
  if (!Array.isArray(dateRange) || dateRange.length !== 2) throw new Error(texts.validateCustomReviewTimeRange);
  const [startTime, endTime] = dateRange;
  return {
    startTime,
    endTime,
    reviewMode: customReviewForm.reviewMode,
    reviewBranches: [...customReviewForm.selectedReviewBranches]
  };
};
const submitProject = async () => {
  if (!projectFormRef.value) return; ensureCreatorDefaults();
  await projectFormRef.value.validate(async (valid) => {
    if (!valid) return;
    if ((dialogMode.value === "edit" || authStore.isAdmin) && !projectForm.ownerUserId) { ElMessage.warning(texts.warningOwner); return; }
    if (projectForm.aiReviewEnabled && !projectForm.llmModelId) { ElMessage.warning(texts.warningReviewModel); return; }
    if (projectForm.wecomNotifyEnabled && !normalizeText(projectForm.wecomWebhookUrl)) { ElMessage.warning(texts.warningWecomWebhook); return; }
    saving.value = true;
    try {
      if (dialogMode.value === "create") { await createProject(buildPayload()); ElMessage.success(texts.createSuccess); }
      else if (projectForm.id) { await updateProject(projectForm.id, buildPayload()); ElMessage.success(texts.updateSuccess); }
      dialogVisible.value = false; await loadProjects();
    } catch (error) { ElMessage.error(dialogMode.value === "create" ? texts.createFail : texts.updateFail); }
    finally { saving.value = false; }
  });
};
const submitCustomReview = async () => {
  const projectId = customReviewForm.projectId;
  if (!customReviewFormRef.value || !projectId) return;
  await customReviewFormRef.value.validate(async (valid) => {
    if (!valid) return;
    customReviewSubmitting.value = true;
    try {
      const response = await createProjectCustomReviewBatch(projectId, buildCustomReviewPayload());
      const result = response.data.data as ProjectCustomReviewBatchResponse | undefined;
      ElMessage.success(result ? buildCustomReviewSummary(result) : texts.customReviewSuccessPrefix);
      customReviewDialogVisible.value = false;
      resetCustomReviewForm();
    } catch (error: any) {
      ElMessage.error(error?.response?.data?.message || texts.customReviewFail);
    } finally {
      customReviewSubmitting.value = false;
    }
  });
};
const submitTemplate = async () => {
  if (!templateFormRef.value) return;
  await templateFormRef.value.validate(async (valid) => {
    if (!valid) return;
    templateSaving.value = true;
    try {
      if (templateDialogMode.value === "create") {
        await createProjectTemplate(buildTemplatePayload());
        ElMessage.success(texts.createTemplateSuccess);
      } else if (templateForm.id) {
        await updateProjectTemplate(templateForm.id, buildTemplatePayload());
        ElMessage.success(texts.updateTemplateSuccess);
      }
      templateDialogVisible.value = false;
      await loadTemplates();
    } catch (error) {
      ElMessage.error(templateDialogMode.value === "create" ? texts.createTemplateFail : texts.updateTemplateFail);
    } finally {
      templateSaving.value = false;
    }
  });
};
const handleDelete = async (row: ProjectItem) => {
  try {
    await ElMessageBox.confirm(`${texts.deleteConfirmPrefix}${row.projectName}${texts.deleteConfirmSuffix}`, texts.deleteConfirmTitle, { confirmButtonText: texts.confirm, cancelButtonText: texts.cancel, type: "warning" });
    await deleteProject(row.id); ElMessage.success(texts.deleteSuccess); if (projects.value.length === 1 && pagination.pageNo > 1) pagination.pageNo -= 1; await loadProjects();
  } catch (error) { if (error !== "cancel") ElMessage.error(texts.deleteFail); }
};
const handleDeleteTemplate = async (row: ProjectTemplateItem) => {
  try {
    await ElMessageBox.confirm(`${texts.deleteTemplateConfirmPrefix}${row.templateName}${texts.deleteConfirmSuffix}`, texts.deleteConfirmTitle, { confirmButtonText: texts.confirm, cancelButtonText: texts.cancel, type: "warning" });
    await deleteProjectTemplate(row.id);
    ElMessage.success(texts.deleteTemplateSuccess);
    if (templates.value.length === 1 && templatePagination.pageNo > 1) templatePagination.pageNo -= 1;
    await loadTemplates();
  } catch (error) {
    if (error !== "cancel") ElMessage.error(texts.deleteTemplateFail);
  }
};
const loadProjects = async () => { loading.value = true; try { const response = await fetchProjects({ pageNo: pagination.pageNo, pageSize: pagination.pageSize, projectName: activeQuery.projectName || undefined, gitlabProjectUrl: activeQuery.gitlabProjectUrl || undefined, aiReviewEnabled: activeQuery.aiReviewEnabled, wecomNotifyEnabled: activeQuery.wecomNotifyEnabled }); const pageData = response.data.data; projects.value = pageData.records || []; pagination.total = pageData.total || 0; } catch (error) { ElMessage.error(texts.loadProjectListFail); } finally { loading.value = false; } };
const handlePageSizeChange = () => { pagination.pageNo = 1; loadProjects(); };
const loadTemplates = async () => {
  templateLoading.value = true;
  try {
    const response = await fetchProjectTemplates({ pageNo: templatePagination.pageNo, pageSize: templatePagination.pageSize, templateName: activeTemplateQuery.templateName || undefined });
    const pageData = response.data.data;
    templates.value = pageData.records || [];
    templatePagination.total = pageData.total || 0;
  } catch (error) {
    ElMessage.error(texts.loadTemplateListFail);
  } finally {
    templateLoading.value = false;
  }
};
const handleTemplatePageSizeChange = () => { templatePagination.pageNo = 1; loadTemplates(); };
const formatDateTime = (value: string | null | undefined) => !value ? "--" : value.replace("T", " ").slice(0, 19);
onMounted(() => {
  applyFilters();
  applyTemplateFilters();
  loadProjects();
  loadTemplates();
});
</script>

<style scoped>
.list-page { display: grid; gap: 20px; }
.page-tabs { padding: 0 6px; }
.page-tabs :deep(.el-tabs__header) { margin: 0 0 18px; }
.page-tabs :deep(.el-tabs__nav-wrap::after) { background: rgba(231, 223, 214, 0.82); }
.page-tabs :deep(.el-tabs__item) { height: 42px; color: var(--cr-text-soft); font-size: 15px; font-weight: 700; }
.page-tabs :deep(.el-tabs__item.is-active) { color: var(--cr-primary); }
.page-tabs :deep(.el-tabs__active-bar) { background: var(--cr-primary); }
.query-panel, .table-panel { padding: 20px 22px; border-radius: 16px; background: var(--cr-surface-paper); box-shadow: var(--cr-shadow-card); }
.actions { margin-left: auto; }
.token-input-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.token-input-row :deep(.el-input) { flex: 1; }
.branch-select-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.branch-select-row :deep(.el-select) { flex: 1; }
.branch-option-meta, .branch-option-missing { float: right; margin-left: 10px; color: var(--cr-text-soft); font-size: 12px; }
.branch-option-missing { color: #c45656; }
.branch-refresh-button { width: 34px; min-width: 34px; height: 34px; padding: 0; border-radius: 10px; }
.branch-refresh-icon { width: 16px; height: 16px; display: block; }
.token-test-button { width: 34px; min-width: 34px; height: 34px; padding: 0; border-radius: 10px; }
.token-test-icon { width: 16px; height: 16px; display: block; }
.query-select { width: 156px; }
.table-header-tip { cursor: help; }
.table-actions { display: inline-flex; align-items: center; flex-wrap: nowrap; gap: 10px; }
.table-actions :deep(.el-button.is-link:not(.more-actions-trigger)) { height: 22px; min-height: 22px; margin-left: 0; padding: 0 7px; border: 1px solid #e9ebec; border-radius: 4px; background: #ffffff; color: #303133; font-size: 12px; font-weight: 500; line-height: 20px; white-space: nowrap; }
.table-actions :deep(.el-button.is-link:not(.more-actions-trigger):hover), .table-actions :deep(.el-button.is-link:not(.more-actions-trigger):focus-visible) { border-color: var(--cr-primary); background: rgba(255, 140, 0, 0.06); color: var(--cr-primary); }
.table-actions :deep(.el-button.is-link:not(.more-actions-trigger).is-disabled) { border-color: #e9ebec; background: #ffffff; color: rgba(86, 67, 52, 0.34); }
.table-actions :deep(.el-dropdown) { display: inline-flex; align-items: center; margin-left: 0; }
.more-actions-trigger { width: 28px; height: 28px; padding: 0; border: none; border-radius: 8px; background: transparent; color: rgba(86, 67, 52, 0.72); display: inline-flex; align-items: center; justify-content: center; cursor: pointer; transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease; }
.more-actions-trigger svg { width: 16px; height: 16px; display: block; fill: currentColor; }
.more-actions-trigger:hover, .more-actions-trigger:focus-visible { outline: none; background: rgba(255, 140, 0, 0.1); color: var(--cr-primary); transform: translateY(-1px); }
.status-pill { display: inline-flex; align-items: center; justify-content: center; min-width: 0; padding: 0 7px; border: 1px solid currentColor; border-radius: 4px; background: #ffffff; font-size: 12px; font-weight: 500; line-height: 20px; }
.is-primary { color: #389e0d; border-color: #b7eb8f; background: #f6ffed; }
.is-neutral { color: #606266; border-color: #e9ebec; background: #ffffff; }
.gitlab-link { color: var(--cr-primary); text-decoration: none; }
.pagination-bar { margin-top: 18px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.pagination-total { color: var(--cr-text-soft); font-size: 13px; }
.pagination-actions { display: flex; align-items: center; gap: 12px; }
.page-size-select { width: 116px; }
:deep(.project-drawer .el-drawer__close-btn) { width: 30px; height: 30px; border: 1px solid #e9ebec; border-radius: 9px; background: #ffffff; }
:deep(.project-drawer .el-drawer__close-btn:hover), :deep(.project-drawer .el-drawer__close-btn:focus-visible) { border-color: var(--cr-primary); color: var(--cr-primary); }
.drawer-body { padding-right: 8px; }
.project-form :deep(.el-form-item__label) { padding-bottom: 8px; color: rgba(86, 67, 52, 0.78); font-size: 12px; font-weight: 700; }
.project-form :deep(.el-input__wrapper), .project-form :deep(.el-select__wrapper), .project-form :deep(.el-textarea__inner) { border-radius: 10px; box-shadow: 0 0 0 1px #ebeef5 inset; background: rgba(255, 255, 255, 0.96); }
.project-form :deep(.el-input__wrapper.is-focus), .project-form :deep(.el-select__wrapper.is-focused), .project-form :deep(.el-textarea__inner:focus) { box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.42) inset; }
.full-width { width: 100%; }
.member-hint { margin: 8px 0 0; color: var(--cr-text-soft); font-size: 12px; line-height: 1.6; }
.switch-section { display: grid; gap: 10px; margin-bottom: 18px; padding: 16px; border: 1px solid #ebeef5; border-radius: 12px; background: rgba(248, 250, 252, 0.86); }
.switch-section__title { margin: 0; color: var(--cr-text); font-size: 16px; font-weight: 800; }
.switch-panel { display: grid; gap: 10px; }
.template-config-panel { display: grid; gap: 10px; }
.template-config-item { display: grid; gap: 10px; padding: 14px; border-radius: 12px; background: rgba(255, 255, 255, 0.9); box-shadow: 0 0 0 1px rgba(235, 238, 245, 0.92) inset; }
.template-config-item__copy { display: grid; gap: 4px; }
.switch-item { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; padding: 12px 14px; border-radius: 12px; background: rgba(255, 255, 255, 0.9); box-shadow: 0 0 0 1px rgba(235, 238, 245, 0.92) inset; }
.switch-item__copy { display: grid; flex: 1 1 0; gap: 4px; min-width: 0; }
.switch-item :deep(.el-switch) { flex: 0 0 auto; margin-left: auto; }
.switch-item__title { color: var(--cr-text); font-size: 13px; font-weight: 700; }
.switch-item__desc { margin: 0; color: var(--cr-text-soft); font-size: 12px; line-height: 1.5; }
.switch-item__extra { width: 100%; display: grid; gap: 8px; padding-top: 10px; border-top: 1px solid rgba(235, 238, 245, 0.92); }
.switch-item__extra-head { display: grid; gap: 4px; }
.custom-review-form { padding-top: 4px; }
.custom-review-section { margin-bottom: 0; }
.custom-review-mode-block { display: grid; gap: 8px; }
.custom-review-mode-group { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; width: 100%; }
.custom-review-mode-group :deep(.el-radio) { margin-right: 0; }
.custom-review-mode-group :deep(.el-radio.is-bordered) { height: 40px; margin-left: 0; border-color: #dcdfe6; border-radius: 10px; background: #ffffff; color: #303133; }
.custom-review-mode-group :deep(.el-radio.is-bordered .el-radio__label) { padding-left: 8px; font-size: 13px; font-weight: 500; }
.custom-review-mode-group :deep(.el-radio.is-bordered .el-radio__inner) { border-color: #c0c4cc; background: #ffffff; }
.custom-review-mode-group :deep(.el-radio__input + .el-radio__label) { color: #303133; }
.custom-review-mode-group :deep(.el-radio__input.is-checked + .el-radio__label) { color: #e6a23c; }
.custom-review-mode-group :deep(.el-radio.is-bordered.is-checked) { border-color: #e6a23c; background: #fff8ee; color: #e6a23c; box-shadow: none; }
.custom-review-mode-group :deep(.el-radio.is-bordered.is-checked .el-radio__inner) { border-color: #e6a23c; background: #e6a23c; }
.custom-review-mode-group :deep(.el-radio.is-bordered:hover) { border-color: #e6a23c; color: #8a5a22; }
.custom-review-mode-group :deep(.el-radio.is-bordered:hover .el-radio__inner) { border-color: #e6a23c; }
.custom-review-mode-note { line-height: 1.6; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 12px; padding-top: 20px; }
.drawer-footer :deep(.el-button:not(.el-button--warning)) { border-color: #e9ebec; }
.drawer-footer :deep(.el-button:not(.el-button--warning):hover), .drawer-footer :deep(.el-button:not(.el-button--warning):focus-visible) { border-color: var(--cr-primary); color: var(--cr-primary); }
:global(.el-dropdown__popper.project-more-actions-menu) { --el-dropdown-menuItem-hover-fill: #fff2df; --el-dropdown-menuItem-hover-color: #d46b08; }
:global(.el-dropdown__popper.project-more-actions-menu .el-dropdown-menu__item) { color: #5f4b3b; font-size: 13px; }
:global(.el-dropdown__popper.project-more-actions-menu .el-dropdown-menu__item:not(.is-disabled):hover), :global(.el-dropdown__popper.project-more-actions-menu .el-dropdown-menu__item:not(.is-disabled):focus) { background: #fff2df !important; color: #d46b08 !important; }

@media (max-width: 720px) {
  .custom-review-mode-group { grid-template-columns: 1fr; }
}
</style>
