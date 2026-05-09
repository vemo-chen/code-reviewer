<template>
  <section class="list-page">
    <section class="query-panel">
      <el-form :inline="true" :model="queryForm">
        <el-form-item>
          <el-input v-model="queryForm.configName" placeholder="请输入模型配置名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="queryForm.providerCode"
            class="query-select"
            placeholder="供应商"
            clearable
            filterable
          >
            <el-option
              v-for="item in providerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="queryForm.scopeType"
            class="query-select"
            placeholder="范围类型"
            clearable
          >
            <el-option label="公共模型" value="GLOBAL" />
            <el-option label="项目私有" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select
            v-model="queryForm.enabled"
            class="query-select"
            placeholder="状态"
            clearable
          >
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="actions">
          <el-button type="warning" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="warning" plain @click="openCreateDialog">新建模型</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="models" stripe>
        <el-table-column prop="configName" label="配置名称" min-width="180" />
        <el-table-column label="供应商 / 模型" min-width="220">
          <template #default="{ row }">
            <div class="stack-text">
              <strong>{{ row.providerName || row.providerCode }}</strong>
              <span>{{ row.modelName || "--" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="范围类型" min-width="110">
          <template #default="{ row }">
            <span :class="['status-pill', row.scopeType === 'GLOBAL' ? 'is-accent' : 'is-neutral']">
              {{ row.scopeType === "GLOBAL" ? "公共模型" : "项目私有" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="维护项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.maintainerProjectName || "--" }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <span :class="['status-pill', row.enabled ? 'is-primary' : 'is-neutral']">
              {{ row.enabled ? "启用" : "停用" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openViewDialog(row)">查看</el-button>
              <template v-if="row.manageable">
                <el-button link type="warning" @click="openEditDialog(row)">编辑</el-button>
                <el-button
                  v-if="!row.enabled"
                  link
                  type="warning"
                  @click="handleEnable(row)"
                >
                  启用
                </el-button>
                <el-button
                  v-else
                  link
                  type="danger"
                  @click="handleDisable(row)"
                >
                  停用
                </el-button>
                <el-dropdown
                  trigger="click"
                  popper-class="llm-more-actions-menu"
                  @command="onMoreCommand(row, $event)"
                >
                  <button type="button" class="more-actions-trigger" aria-label="更多操作">
                    <svg viewBox="0 0 16 16" aria-hidden="true">
                      <circle cx="8" cy="3.25" r="1.2" />
                      <circle cx="8" cy="8" r="1.2" />
                      <circle cx="8" cy="12.75" r="1.2" />
                    </svg>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="test" :disabled="testingId === row.id">
                        {{ testingId === row.id ? "测试中..." : "测试连接" }}
                      </el-dropdown-item>
                      <el-dropdown-item
                        command="delete"
                        class="danger-dropdown-item"
                      >
                        {{ deleteActionLabel }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <div class="pagination-total">共 {{ pagination.total }} 条</div>
        <div class="pagination-actions">
          <el-pagination
            v-model:current-page="pagination.pageNo"
            :page-size="pagination.pageSize"
            :total="pagination.total"
            layout="prev, pager, next"
            background
            @current-change="loadModels"
          />
          <el-select v-model="pagination.pageSize" class="page-size-select" @change="handlePageSizeChange">
            <el-option label="10条/页" :value="10" />
            <el-option label="20条/页" :value="20" />
            <el-option label="50条/页" :value="50" />
          </el-select>
        </div>
      </div>
    </section>

    <el-drawer
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建模型' : dialogMode === 'edit' ? '编辑模型' : '查看模型'"
      size="620px"
      destroy-on-close
      class="llm-drawer"
    >
      <div class="drawer-body" v-loading="drawerLoading">
        <el-form ref="formRef" :model="form" :rules="dialogMode === 'view' ? undefined : formRules" :disabled="dialogMode === 'view'" label-position="top" class="llm-form">
          <section class="form-section">
            <h4 class="form-section__title">{{ basicSectionTitle }}</h4>
            <el-form-item :label="configNameLabel" prop="configName">
              <el-input v-model="form.configName" :placeholder="configNamePlaceholder" />
            </el-form-item>
            <el-form-item :label="providerCodeLabel" prop="providerCode">
              <el-select v-model="form.providerCode" class="full-width" filterable @change="handleProviderChange">
                <el-option
                  v-for="item in providerOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="providerTypeLabel" prop="providerType">
              <el-select v-model="form.providerType" class="full-width">
                <el-option label="OpenAI Compatible" value="OPENAI_COMPATIBLE" />
                <el-option label="Anthropic" value="ANTHROPIC" />
              </el-select>
            </el-form-item>
            <el-form-item label="Base URL" prop="baseUrl">
              <el-input v-model="form.baseUrl" :placeholder="baseUrlPlaceholder" />
            </el-form-item>
            <el-form-item :label="apiKeyLabel">
              <el-input
                v-model="form.apiKey"
                show-password
                :placeholder="apiKeyPlaceholder"
                clearable
              />
            </el-form-item>
            <el-form-item :label="modelNameLabel" prop="modelName">
              <el-input v-model="form.modelName" :placeholder="modelNamePlaceholder" />
            </el-form-item>
          </section>

          <section class="form-section">
            <h4 class="form-section__title">调用参数</h4>
            <div class="grid-two">
              <el-form-item label="超时时间 (ms)">
                <el-input-number v-model="form.timeoutMs" class="full-width" :min="1000" :step="1000" />
              </el-form-item>
              <el-form-item label="Max Tokens">
                <el-input-number v-model="form.maxTokens" class="full-width" :min="1" :step="256" />
              </el-form-item>
            </div>
            <el-form-item label="Temperature">
              <el-input-number v-model="form.temperature" class="full-width" :min="0" :max="2" :step="0.1" />
            </el-form-item>
          </section>

          <section class="form-section">
            <h4 class="form-section__title">权限范围</h4>
            <el-form-item label="范围类型" prop="scopeType">
              <p class="field-hint field-hint--above">公共模型面向全平台可用，仅管理员可以创建和管理；项目私有模型由维护项目的 owner 管理。</p>
              <el-select v-model="form.scopeType" class="full-width">
                <el-option label="公共模型" value="GLOBAL" />
                <el-option label="项目私有" value="PROJECT" />
              </el-select>
            </el-form-item>
            <template v-if="form.scopeType === 'PROJECT'">
              <el-form-item label="维护项目" prop="maintainerProjectId">
                <el-select v-model="form.maintainerProjectId" class="full-width" filterable @change="handleMaintainerChange">
                  <el-option
                    v-for="item in projectOptions"
                    :key="item.id"
                    :label="item.projectName"
                    :value="item.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="共享项目">
                <el-select
                  v-model="form.projectIds"
                  class="full-width"
                  multiple
                  filterable
                  :collapse-tags="form.projectIds.length > 10"
                  :max-collapse-tags="10"
                  collapse-tags-tooltip
                >
                  <el-option
                    v-for="item in shareProjectOptions"
                    :key="item.id"
                    :label="item.projectName"
                    :value="item.id"
                  />
                </el-select>
                <p class="field-hint">维护项目会自动加入可用范围，共享项目仅拥有使用权，不拥有编辑权。</p>
              </el-form-item>
            </template>
            <el-form-item label="是否启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="4" placeholder="可选，补充这条模型配置的用途说明" />
            </el-form-item>
          </section>
        </el-form>
      </div>
      <div class="drawer-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="dialogMode !== 'view'" type="warning" :loading="saving" @click="submitForm">保存</el-button>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createLlmModel,
  deleteLlmModel,
  disableLlmModel,
  enableLlmModel,
  fetchLlmModelDetail,
  fetchLlmModels,
  testLlmModel,
  updateLlmModel,
  type LlmModelUpsertPayload
} from "../../api/llm";
import { fetchProjects } from "../../api/projects";
import { useAuthStore } from "../../stores/auth";

interface ProjectOption {
  id: number;
  projectName: string;
}

interface LlmModelItem {
  id: number;
  configName: string;
  providerCode: string;
  providerName: string;
  providerType: string;
  modelName: string;
  scopeType: string;
  maintainerProjectId?: number | null;
  maintainerProjectName?: string | null;
  manageable?: boolean;
  enabled: boolean;
  updatedAt?: string | null;
}

interface LlmModelForm {
  id?: number;
  configName: string;
  providerCode: string;
  providerName: string;
  providerType: string;
  baseUrl: string;
  apiKey: string;
  apiKeyMasked: string;
  modelName: string;
  enabled: boolean;
  scopeType: string;
  maintainerProjectId?: number | null;
  timeoutMs?: number | null;
  maxTokens?: number | null;
  temperature?: number | null;
  remark: string;
  projectIds: number[];
}

const authStore = useAuthStore();
const DEFAULT_TIMEOUT_MS = 180000;
const DEFAULT_MAX_TOKENS = 8192;
const DEFAULT_TEMPERATURE = 0.1;
const loading = ref(false);
const saving = ref(false);
const drawerLoading = ref(false);
const testingId = ref<number | null>(null);
const dialogVisible = ref(false);
const dialogMode = ref<"create" | "edit" | "view">("create");
const models = ref<LlmModelItem[]>([]);
const projectOptions = ref<ProjectOption[]>([]);
const formRef = ref<FormInstance>();

const basicSectionTitle = "\u57fa\u7840\u914d\u7f6e";
const configNameLabel = "\u914d\u7f6e\u540d\u79f0";
const configNamePlaceholder = "\u8bf7\u8f93\u5165\u6a21\u578b\u914d\u7f6e\u540d\u79f0";
const providerCodeLabel = "\u4f9b\u5e94\u5546";
const providerTypeLabel = "\u534f\u8bae\u7c7b\u578b";
const baseUrlPlaceholder = "\u8bf7\u8f93\u5165\u6a21\u578b\u63a5\u53e3\u5730\u5740";
const apiKeyLabel = "API Key";
const apiKeyPlaceholder = computed(() => dialogMode.value === "create" ? "\u8bf7\u8f93\u5165 API Key" : `\u5f53\u524d\uff1a${form.apiKeyMasked || "\u5df2\u8131\u654f"}`);
const modelNameLabel = "\u6a21\u578b/\u6a21\u5f0f\u6807\u8bc6";
const modelNamePlaceholder = "\u8bf7\u8f93\u5165\u6a21\u578b\u6216\u6a21\u5f0f\u6807\u8bc6";
const modelNameValidateMessage = "\u8bf7\u8f93\u5165\u6a21\u578b\u6216\u6a21\u5f0f\u6807\u8bc6";
const deleteActionLabel = "\u5220\u9664";

const providerOptions = [
  { label: "DeepSeek", value: "DEEPSEEK", providerType: "OPENAI_COMPATIBLE" },
  { label: "OpenAI", value: "OPENAI", providerType: "OPENAI_COMPATIBLE" },
  { label: "Anthropic", value: "ANTHROPIC", providerType: "ANTHROPIC" },
  { label: "智谱AI", value: "ZHIPU", providerType: "OPENAI_COMPATIBLE" },
  { label: "通义千问", value: "QWEN", providerType: "OPENAI_COMPATIBLE" },
  { label: "SiliconFlow", value: "SILICONFLOW", providerType: "OPENAI_COMPATIBLE" },
  { label: "Ollama", value: "OLLAMA", providerType: "OPENAI_COMPATIBLE" }
];

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0
});

const queryForm = reactive<{
  configName: string;
  providerCode?: string;
  scopeType?: string;
  enabled?: boolean;
}>({
  configName: "",
  providerCode: undefined,
  scopeType: undefined,
  enabled: undefined
});

const activeQuery = reactive({
  configName: "",
  providerCode: undefined as string | undefined,
  scopeType: undefined as string | undefined,
  enabled: undefined as boolean | undefined
});

const form = reactive<LlmModelForm>({
  configName: "",
  providerCode: "DEEPSEEK",
  providerName: "DeepSeek",
  providerType: "OPENAI_COMPATIBLE",
  baseUrl: "",
  apiKey: "",
  apiKeyMasked: "",
  modelName: "",
  enabled: true,
  scopeType: "GLOBAL",
  maintainerProjectId: null,
  timeoutMs: DEFAULT_TIMEOUT_MS,
  maxTokens: DEFAULT_MAX_TOKENS,
  temperature: DEFAULT_TEMPERATURE,
  remark: "",
  projectIds: []
});

const formRules: FormRules<LlmModelForm> = {
  configName: [{ required: true, message: "请输入配置名称", trigger: "blur" }],
  providerCode: [{ required: true, message: "请选择供应商", trigger: "change" }],
  providerName: [{ required: true, message: "请输入供应商名称", trigger: "blur" }],
  providerType: [{ required: true, message: "请选择协议类型", trigger: "change" }],
  baseUrl: [{ required: true, message: "请输入 Base URL", trigger: "blur" }],
  modelName: [{ required: true, message: modelNameValidateMessage, trigger: "blur" }],
  scopeType: [{ required: true, message: "请选择范围类型", trigger: "change" }],
  maintainerProjectId: [
    {
      validator: (_rule, value, callback) => {
        if (form.scopeType === "PROJECT" && !value) {
          callback(new Error("请选择维护项目"));
          return;
        }
        callback();
      },
      trigger: "change"
    }
  ]
};

const shareProjectOptions = computed(() =>
  projectOptions.value.filter((item) => item.id !== form.maintainerProjectId)
);

const normalizeText = (value: string | null | undefined) => {
  if (typeof value !== "string") {
    return undefined;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
};

const normalizeNumber = (value: number | null | undefined) => {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
};

const normalizeProjectIds = (value: number[] | undefined | null) => {
  const unique = new Set<number>();
  (value || []).forEach((item) => {
    if (typeof item === "number" && Number.isFinite(item)) {
      unique.add(item);
    }
  });
  return Array.from(unique);
};

const normalizeSharedProjectIds = (value: number[] | undefined | null, maintainerProjectId?: number | null) =>
  normalizeProjectIds(value).filter((item) => item !== maintainerProjectId);

const applyFilters = () => {
  activeQuery.configName = normalizeText(queryForm.configName) || "";
  activeQuery.providerCode = queryForm.providerCode || undefined;
  activeQuery.scopeType = queryForm.scopeType || undefined;
  activeQuery.enabled = queryForm.enabled;
};

const resetForm = () => {
  form.id = undefined;
  form.configName = "";
  form.providerCode = "DEEPSEEK";
  form.providerName = "DeepSeek";
  form.providerType = "OPENAI_COMPATIBLE";
  form.baseUrl = "";
  form.apiKey = "";
  form.apiKeyMasked = "";
  form.modelName = "";
  form.enabled = true;
  form.scopeType = "GLOBAL";
  form.maintainerProjectId = null;
  form.timeoutMs = DEFAULT_TIMEOUT_MS;
  form.maxTokens = DEFAULT_MAX_TOKENS;
  form.temperature = DEFAULT_TEMPERATURE;
  form.remark = "";
  form.projectIds = [];
};

const loadProjectOptions = async () => {
  const response = await fetchProjects({ pageNo: 1, pageSize: 500 });
  const records = response.data.data?.records || [];
  projectOptions.value = records.map((item: ProjectOption) => ({
    id: item.id,
    projectName: item.projectName
  }));
};

const loadModels = async () => {
  loading.value = true;
  try {
    const response = await fetchLlmModels({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      configName: activeQuery.configName || undefined,
      providerCode: activeQuery.providerCode,
      scopeType: activeQuery.scopeType,
      enabled: activeQuery.enabled
    });
    const pageData = response.data.data;
    models.value = pageData.records || [];
    pagination.total = pageData.total || 0;
  } catch (error) {
    ElMessage.error("加载模型列表失败");
  } finally {
    loading.value = false;
  }
};

const handleSearch = async () => {
  pagination.pageNo = 1;
  applyFilters();
  await loadModels();
};

const resetFilters = async () => {
  queryForm.configName = "";
  queryForm.providerCode = undefined;
  queryForm.scopeType = undefined;
  queryForm.enabled = undefined;
  pagination.pageNo = 1;
  applyFilters();
  await loadModels();
};

const handlePageSizeChange = () => {
  pagination.pageNo = 1;
  loadModels();
};

const handleProviderChange = (value: string) => {
  const matched = providerOptions.find((item) => item.value === value);
  if (!matched) {
    return;
  }
  form.providerName = matched.label;
  form.providerType = matched.providerType;
};

const handleMaintainerChange = (value: number | null | undefined) => {
  if (!value) {
    form.maintainerProjectId = null;
    form.projectIds = normalizeProjectIds(form.projectIds);
    return;
  }
  form.projectIds = normalizeSharedProjectIds(form.projectIds, value);
};

const openCreateDialog = async () => {
  dialogMode.value = "create";
  resetForm();
  drawerLoading.value = true;
  dialogVisible.value = true;
  try {
    await loadProjectOptions();
  } catch (error) {
    dialogVisible.value = false;
    ElMessage.error("加载项目列表失败");
  } finally {
    drawerLoading.value = false;
  }
};

const openViewDialog = async (row: LlmModelItem) => {
  dialogMode.value = "view";
  resetForm();
  drawerLoading.value = true;
  dialogVisible.value = true;
  try {
    const [detailResponse] = await Promise.all([
      fetchLlmModelDetail(row.id),
      loadProjectOptions()
    ]);
    applyDetail(detailResponse.data.data);
  } catch (error) {
    dialogVisible.value = false;
    ElMessage.error("加载模型详情失败");
  } finally {
    drawerLoading.value = false;
  }
};

const applyDetail = (detail: any) => {
  form.id = detail.id;
  form.configName = detail.configName || "";
  form.providerCode = detail.providerCode || "";
  form.providerName = detail.providerName || "";
  form.providerType = detail.providerType || "OPENAI_COMPATIBLE";
  form.baseUrl = detail.baseUrl || "";
  form.apiKey = "";
  form.apiKeyMasked = detail.apiKeyMasked || "";
  form.modelName = detail.modelName || "";
  form.enabled = Boolean(detail.enabled);
  form.scopeType = detail.scopeType || "GLOBAL";
  form.maintainerProjectId = detail.maintainerProjectId ?? null;
  form.timeoutMs = detail.timeoutMs ?? null;
  form.maxTokens = detail.maxTokens ?? null;
  form.temperature = detail.temperature ?? null;
  form.remark = detail.remark || "";
  form.projectIds = normalizeSharedProjectIds(detail.projectIds, detail.maintainerProjectId ?? null);
};

const openEditDialog = async (row: LlmModelItem) => {
  dialogMode.value = "edit";
  resetForm();
  drawerLoading.value = true;
  dialogVisible.value = true;
  try {
    const [detailResponse] = await Promise.all([
      fetchLlmModelDetail(row.id),
      loadProjectOptions()
    ]);
    applyDetail(detailResponse.data.data);
  } catch (error) {
    dialogVisible.value = false;
    ElMessage.error("加载模型详情失败");
  } finally {
    drawerLoading.value = false;
  }
};

const buildPayload = (): LlmModelUpsertPayload => ({
  configName: form.configName.trim(),
  providerCode: form.providerCode,
  providerName: form.providerName.trim(),
  providerType: form.providerType,
  baseUrl: form.baseUrl.trim(),
  apiKey: normalizeText(form.apiKey),
  modelName: form.modelName.trim(),
  enabled: form.enabled,
  scopeType: form.scopeType,
  maintainerProjectId: form.scopeType === "PROJECT" ? form.maintainerProjectId ?? null : null,
  timeoutMs: normalizeNumber(form.timeoutMs) ?? null,
  maxTokens: normalizeNumber(form.maxTokens) ?? null,
  temperature: normalizeNumber(form.temperature) ?? null,
  remark: normalizeText(form.remark),
  projectIds: form.scopeType === "PROJECT" ? normalizeProjectIds(form.projectIds) : []
});

const submitForm = async () => {
  if (!formRef.value) {
    return;
  }
  await formRef.value.validate(async (valid) => {
    if (!valid) {
      return;
    }
    if (!authStore.isAdmin && form.scopeType === "GLOBAL") {
      ElMessage.warning("只有管理员能创建公共模型");
      return;
    }
    saving.value = true;
    try {
      if (dialogMode.value === "create") {
        await createLlmModel(buildPayload());
        ElMessage.success("模型配置创建成功");
      } else if (form.id) {
        await updateLlmModel(form.id, buildPayload());
        ElMessage.success("模型配置更新成功");
      }
      dialogVisible.value = false;
      await loadModels();
    } catch (error: any) {
      const fallbackMessage = dialogMode.value === "create" ? "模型配置创建失败" : "模型配置更新失败";
      ElMessage.error(error?.response?.data?.message || fallbackMessage);
    } finally {
      saving.value = false;
    }
  });
};

const handleTest = async (row: LlmModelItem) => {
  testingId.value = row.id;
  try {
    const response = await testLlmModel(row.id);
    const message = response.data.data?.message || "连接测试成功";
    ElMessage.success(message);
  } catch (error) {
    ElMessage.error("模型连接测试失败");
  } finally {
    testingId.value = null;
  }
};

const handleEnable = async (row: LlmModelItem) => {
  try {
    await enableLlmModel(row.id);
    ElMessage.success("模型已启用");
    await loadModels();
  } catch (error) {
    ElMessage.error("启用模型失败");
  }
};

const handleDisable = async (row: LlmModelItem) => {
  try {
    await disableLlmModel(row.id);
    ElMessage.success("模型已停用");
    await loadModels();
  } catch (error) {
    ElMessage.error("停用模型失败");
  }
};

const onMoreCommand = (row: LlmModelItem, command: string | number | object) => {
  if (typeof command === "string") {
    handleMoreCommand(row, command);
  }
};

const handleMoreCommand = async (row: LlmModelItem, command: string) => {
  if (command === "test") {
    await handleTest(row);
    return;
  }
  if (command === "delete") {
    await handleDelete(row);
  }
};

const handleDelete = async (row: LlmModelItem) => {
  try {
    await ElMessageBox.confirm(
      "\u5220\u9664\u540e\u65e0\u6cd5\u6062\u590d\uff0c\u5df2\u88ab\u9879\u76ee\u7ed1\u5b9a\u7684\u6a21\u578b\u4e0d\u5141\u8bb8\u5220\u9664\u3002\u662f\u5426\u7ee7\u7eed\uff1f",
      "\u786e\u8ba4\u5220\u9664",
      {
        type: "warning",
        confirmButtonText: "\u5220\u9664",
        cancelButtonText: "\u53d6\u6d88"
      }
    );
    await deleteLlmModel(row.id);
    ElMessage.success("\u6a21\u578b\u5df2\u5220\u9664");
    await loadModels();
  } catch (error: any) {
    if (error === "cancel") {
      return;
    }
    ElMessage.error(error?.response?.data?.message || "\u5220\u9664\u6a21\u578b\u5931\u8d25");
  }
};

const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return "--";
  }
  return value.replace("T", " ").slice(0, 19);
};

onMounted(() => {
  applyFilters();
  loadModels();
});
</script>

<style scoped>
.list-page {
  display: grid;
  gap: 20px;
}

.query-panel,
.table-panel {
  padding: 20px 22px;
  border-radius: 16px;
  background: var(--cr-surface-paper);
  box-shadow: var(--cr-shadow-card);
}

.actions {
  margin-left: auto;
}

.query-select {
  width: 156px;
}

.stack-text {
  display: grid;
  gap: 3px;
}

.stack-text strong {
  color: var(--cr-text);
  font-size: 13px;
  font-weight: 700;
}

.stack-text span {
  color: var(--cr-text-soft);
  font-size: 12px;
}

.table-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
}

.table-actions :deep(.el-button.is-link:not(.more-actions-trigger)) {
  height: 22px;
  min-height: 22px;
  margin-left: 0;
  padding: 0 7px;
  border: 1px solid #e9ebec;
  border-radius: 4px;
  background: #ffffff;
  color: #303133;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}

.table-actions :deep(.el-button.is-link:not(.more-actions-trigger):hover),
.table-actions :deep(.el-button.is-link:not(.more-actions-trigger):focus-visible) {
  border-color: var(--cr-primary);
  background: rgba(255, 140, 0, 0.06);
  color: var(--cr-primary);
}

.table-actions :deep(.el-button.is-link:not(.more-actions-trigger).is-disabled) {
  border-color: #e9ebec;
  background: #ffffff;
  color: rgba(86, 67, 52, 0.34);
}

.table-actions :deep(.el-dropdown) {
  display: inline-flex;
  align-items: center;
  margin-left: 0;
}

.more-actions-trigger {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(86, 67, 52, 0.72);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.more-actions-trigger svg {
  width: 16px;
  height: 16px;
  display: block;
  fill: currentColor;
}

.more-actions-trigger:hover,
.more-actions-trigger:focus-visible {
  outline: none;
  background: rgba(255, 140, 0, 0.1);
  color: var(--cr-primary);
  transform: translateY(-1px);
}

:global(.el-dropdown__popper.llm-more-actions-menu) {
  --el-dropdown-menuItem-hover-fill: #fff2df;
  --el-dropdown-menuItem-hover-color: #d46b08;
}

:global(.el-dropdown__popper.llm-more-actions-menu .el-dropdown-menu__item) {
  color: #5f4b3b;
  font-size: 13px;
}

:global(.el-dropdown__popper.llm-more-actions-menu .el-dropdown-menu__item:not(.is-disabled):hover),
:global(.el-dropdown__popper.llm-more-actions-menu .el-dropdown-menu__item:not(.is-disabled):focus) {
  background: #fff2df !important;
  color: #d46b08 !important;
}

:global(.el-dropdown__popper.llm-more-actions-menu .danger-dropdown-item) {
  color: #c45656;
}

:global(.el-dropdown__popper.llm-more-actions-menu .danger-dropdown-item:not(.is-disabled):hover),
:global(.el-dropdown__popper.llm-more-actions-menu .danger-dropdown-item:not(.is-disabled):focus) {
  background: #fff1e8 !important;
  color: #b54708 !important;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 0 7px;
  border: 1px solid currentColor;
  border-radius: 4px;
  background: #ffffff;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}

.is-primary {
  color: #389e0d;
  border-color: #b7eb8f;
  background: #f6ffed;
}

.is-accent {
  color: #d46b08;
  border-color: #ffd591;
  background: #fff7e6;
}

.is-neutral {
  color: #606266;
  border-color: #e9ebec;
  background: #ffffff;
}

.pagination-bar {
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.pagination-total {
  color: var(--cr-text-soft);
  font-size: 13px;
}

.pagination-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-size-select {
  width: 116px;
}

:deep(.llm-drawer .el-drawer__close-btn) {
  width: 30px;
  height: 30px;
  border: 1px solid #e9ebec;
  border-radius: 9px;
  background: #ffffff;
}

:deep(.llm-drawer .el-drawer__close-btn:hover),
:deep(.llm-drawer .el-drawer__close-btn:focus-visible) {
  border-color: var(--cr-primary);
  color: var(--cr-primary);
}

.drawer-body {
  padding-right: 8px;
}

.llm-form {
  display: grid;
  gap: 18px;
}

.form-section {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.86);
}

.form-section__title {
  margin: 0 0 14px;
  color: var(--cr-text);
  font-size: 16px;
  font-weight: 800;
}

.llm-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  color: rgba(86, 67, 52, 0.78);
  font-size: 12px;
  font-weight: 700;
}

.llm-form :deep(.el-input__wrapper),
.llm-form :deep(.el-select__wrapper),
.llm-form :deep(.el-input-number),
.llm-form :deep(.el-textarea__inner) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #ebeef5 inset;
  background: rgba(255, 255, 255, 0.96);
}

.llm-form :deep(.el-input__wrapper.is-focus),
.llm-form :deep(.el-select__wrapper.is-focused),
.llm-form :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.42) inset;
}

.grid-two {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.full-width {
  width: 100%;
}

.field-hint {
  margin: 8px 0 0;
  color: var(--cr-text-soft);
  font-size: 12px;
  line-height: 1.6;
}

.field-hint--above {
  margin: 0 0 8px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
}

.drawer-footer :deep(.el-button:not(.el-button--warning)) {
  border-color: #e9ebec;
}

.drawer-footer :deep(.el-button:not(.el-button--warning):hover),
.drawer-footer :deep(.el-button:not(.el-button--warning):focus-visible) {
  border-color: var(--cr-primary);
  color: var(--cr-primary);
}

@media (max-width: 900px) {
  .grid-two {
    grid-template-columns: 1fr;
  }
}
</style>
