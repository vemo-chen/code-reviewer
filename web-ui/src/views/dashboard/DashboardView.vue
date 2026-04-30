<template>
  <section class="dashboard-page">
    <section class="toolbar-panel">
      <div class="range-actions">
        <template v-for="option in quickRanges" :key="option.value">
          <button
            :class="['range-chip', { active: filter.rangeType === option.value }]"
            type="button"
            @click="handleQuickRange(option.value)"
          >
            {{ option.label }}
          </button>
          <div v-if="option.value === 'custom' && filter.rangeType === 'custom'" class="custom-inline">
            <el-date-picker
              v-model="filter.customRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              unlink-panels
            />
            <el-button type="warning" @click="loadDashboard">查询</el-button>
          </div>
        </template>
      </div>
    </section>

    <div class="metric-grid">
      <article v-for="card in metricCards" :key="card.label" class="metric-card">
        <div class="metric-head">
          <span>{{ card.label }}</span>
          <em>{{ card.badge }}</em>
        </div>
        <strong>{{ card.value }}</strong>
        <small>{{ card.note }}</small>
      </article>
    </div>

    <div class="insight-grid">
      <section class="chart-card primary-card">
        <div class="card-head">
          <div>
            <h4>项目提交统计</h4>
            <p>按项目维度统计当前时间范围内的代码提交和合并请求总数。</p>
          </div>
          <span class="live-tag">Project View</span>
        </div>
        <div ref="projectChartRef" class="chart-box"></div>
      </section>

      <section class="summary-card">
        <h4>任务分布</h4>
        <p class="summary-text">
          当前统计区间内的审查任务、高风险任务与活跃项目占比，用于快速判断团队审查负载。
        </p>
        <div class="distribution-list">
          <div v-for="item in distributionItems" :key="item.label" class="distribution-item">
            <div class="distribution-head">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}%</strong>
            </div>
            <div class="distribution-track">
              <div class="distribution-bar" :style="{ width: `${item.value}%` }"></div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div class="chart-grid">
      <section class="chart-card">
        <div class="card-head compact">
          <div>
            <h4>项目平均得分</h4>
            <p>近 {{ currentRangeLabel }}</p>
          </div>
        </div>
        <div ref="riskChartRef" class="chart-box small"></div>
      </section>
      <section class="chart-card">
        <div class="card-head compact">
          <div>
            <h4>开发者平均得分</h4>
            <p>Top 8</p>
          </div>
        </div>
        <div ref="developerChartRef" class="chart-box small"></div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import * as echarts from "echarts";
import { ElMessage } from "element-plus";
import { fetchDeveloperStats, fetchProjectStats, fetchScoreStats } from "../../api/dashboard";

type RangeType = "week" | "twoWeeks" | "month" | "custom";

interface MetricCard {
  label: string;
  value: string;
  note: string;
  badge: string;
}

interface ProjectStatItem {
  projectId: number;
  projectName: string;
  taskCount: number;
  successCount: number;
  failedCount: number;
  highRiskCount: number;
  averageFinalScore: number | null;
}

interface DeveloperStatItem {
  developerId: string;
  developerName: string;
  reviewCount: number;
  successCount: number;
  highRiskCount: number;
  averageFinalScore: number | null;
}

const quickRanges = [
  { label: "近一周", value: "week" },
  { label: "近两周", value: "twoWeeks" },
  { label: "近一月", value: "month" },
  { label: "自定义", value: "custom" }
] as const;

const filter = reactive<{
  rangeType: RangeType;
  customRange: string[];
}>({
  rangeType: "week",
  customRange: []
});

const projectStats = ref<ProjectStatItem[]>([]);
const developerStats = ref<DeveloperStatItem[]>([]);
const scoreStats = ref({
  reviewCount: 0,
  averageFinalScore: 0,
  startDate: "",
  endDate: ""
});
const overview = ref({
  totalProjects: 0,
  totalTasks: 0,
  highRiskTasks: 0,
  totalDevelopers: 0
});

const projectChartRef = ref<HTMLDivElement>();
const riskChartRef = ref<HTMLDivElement>();
const developerChartRef = ref<HTMLDivElement>();

let projectChart: echarts.ECharts | null = null;
let riskChart: echarts.ECharts | null = null;
let developerChart: echarts.ECharts | null = null;

const currentRangeLabel = computed(() => {
  if (filter.rangeType === "week") {
    return "一周";
  }
  if (filter.rangeType === "twoWeeks") {
    return "两周";
  }
  if (filter.rangeType === "month") {
    return "一月";
  }
  if (filter.customRange.length === 2) {
    return `${filter.customRange[0]} ~ ${filter.customRange[1]}`;
  }
  return "自定义";
});

const activeProjectCount = computed(() =>
  projectStats.value.filter((item) => item.taskCount > 0).length
);

const totalReviewCount = computed(() => overview.value.totalTasks || 0);

const metricCards = computed<MetricCard[]>(() => [
  {
    label: "活跃项目数",
    value: String(activeProjectCount.value || overview.value.totalProjects || 0),
    note: "当前时间范围内存在审查任务的项目数量",
    badge: `${Math.max(activeProjectCount.value, 0)} active`
  },
  {
    label: "提交人数",
    value: String(overview.value.totalDevelopers),
    note: "参与代码提交或合并请求的开发者数量",
    badge: `${overview.value.totalDevelopers || 0} users`
  },
  {
    label: "代码提交和合并请求总数",
    value: String(totalReviewCount.value),
    note: "按审查任务口径统计代码提交与合并请求数量",
    badge: `${totalReviewCount.value} events`
  },
  {
    label: "平均得分",
    value: formatScore(scoreStats.value.averageFinalScore),
    note: "当前时间区间内最终得分的平均值",
    badge: `${formatScore(scoreStats.value.averageFinalScore)} score`
  }
]);

const distributionItems = computed(() => {
  const total = Math.max(overview.value.totalTasks, 1);
  return [
    {
      label: "高风险任务",
      value: Math.round((overview.value.highRiskTasks / total) * 100)
    },
    {
      label: "成功任务",
      value: Math.round((Math.max(overview.value.totalTasks - overview.value.highRiskTasks, 0) / total) * 100)
    },
    {
      label: "活跃项目",
      value: Math.min(Math.round((activeProjectCount.value / Math.max(overview.value.totalProjects, 1)) * 100), 100)
    }
  ];
});

const formatScore = (value: number | null | undefined) => {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return "--";
  }
  return Number(value).toFixed(1);
};

const getDateRange = () => {
  const end = new Date();
  const start = new Date(end);
  if (filter.rangeType === "week") {
    start.setDate(end.getDate() - 6);
  } else if (filter.rangeType === "twoWeeks") {
    start.setDate(end.getDate() - 13);
  } else if (filter.rangeType === "month") {
    start.setDate(end.getDate() - 29);
  } else if (filter.customRange.length === 2) {
    return {
      startDate: filter.customRange[0],
      endDate: filter.customRange[1]
    };
  }
  return {
    startDate: formatDate(start),
    endDate: formatDate(end)
  };
};

const formatDate = (date: Date) => {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const handleQuickRange = (value: RangeType) => {
  filter.rangeType = value;
  if (value !== "custom") {
    loadDashboard();
  }
};

const loadDashboard = async () => {
  if (filter.rangeType === "custom" && filter.customRange.length !== 2) {
    ElMessage.warning("请选择完整的时间范围");
    return;
  }
  try {
    const { startDate, endDate } = getDateRange();
    const [projectResp, developerResp, scoreResp] = await Promise.all([
      fetchProjectStats(),
      fetchDeveloperStats(),
      fetchScoreStats(startDate, endDate)
    ]);
    const projectData = projectResp.data.data;
    const developerData = developerResp.data.data;
    const scoreData = scoreResp.data.data;
    overview.value = {
      totalProjects: projectData.totalProjects || 0,
      totalTasks: projectData.totalTasks || 0,
      highRiskTasks: projectData.highRiskTasks || 0,
      totalDevelopers: developerData.totalDevelopers || 0
    };
    projectStats.value = projectData.projects || [];
    developerStats.value = developerData.developers || [];
    scoreStats.value = {
      reviewCount: scoreData.reviewCount || 0,
      averageFinalScore: scoreData.averageFinalScore || 0,
      startDate: scoreData.startDate || startDate,
      endDate: scoreData.endDate || endDate
    };
    await nextTick();
    renderCharts();
  } catch (error) {
    ElMessage.error("加载看板数据失败");
  }
};

const renderCharts = () => {
  initProjectChart();
  initRiskChart();
  initDeveloperChart();
};

const initProjectChart = () => {
  if (!projectChartRef.value) {
    return;
  }
  projectChart = projectChart ?? echarts.init(projectChartRef.value);
  const chartData = [...projectStats.value]
    .sort((a, b) => b.taskCount - a.taskCount)
    .slice(0, 12);
  projectChart.setOption({
    color: ["#ff8c00"],
    grid: { left: 36, right: 18, top: 18, bottom: 56, containLabel: true },
    xAxis: {
      type: "category",
      data: chartData.map((item) => item.projectName),
      axisLabel: { color: "#564334", interval: 0, rotate: 22 },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    yAxis: {
      type: "value",
      name: "提交次数",
      nameTextStyle: { color: "#564334" },
      axisLabel: { color: "#564334" },
      splitLine: { lineStyle: { color: "rgba(221,193,174,0.22)" } },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    tooltip: {
      trigger: "axis",
      formatter: (params) => {
        const item = Array.isArray(params) ? params[0] : params;
        return `${item.name}<br/>提交次数：${item.value}`;
      }
    },
    series: [
      {
        name: "提交次数",
        type: "bar",
        barWidth: 24,
        itemStyle: {
          color: "#ff8c00",
          borderRadius: [4, 4, 0, 0]
        },
        data: chartData.map((item) => item.taskCount)
      }
    ]
  });
};

const initRiskChart = () => {
  if (!riskChartRef.value) {
    return;
  }
  riskChart = riskChart ?? echarts.init(riskChartRef.value);
  const topProjects = [...projectStats.value]
    .sort((a, b) => (b.averageFinalScore || 0) - (a.averageFinalScore || 0))
    .slice(0, 6);
  riskChart.setOption({
    color: ["#ff8c00"],
    grid: { left: 48, right: 18, top: 18, bottom: 36 },
    xAxis: {
      type: "category",
      data: topProjects.map((item) => item.projectName),
      axisLabel: { color: "#564334" },
      axisLine: { lineStyle: { color: "rgba(221,193,174,0.4)" } }
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 100,
      axisLabel: { color: "#564334" },
      splitLine: { lineStyle: { color: "rgba(221,193,174,0.18)" } }
    },
    tooltip: { trigger: "axis" },
    series: [
      {
        type: "bar",
        barWidth: 20,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        data: topProjects.map((item) => item.averageFinalScore || 0)
      }
    ]
  });
};

const initDeveloperChart = () => {
  if (!developerChartRef.value) {
    return;
  }
  developerChart = developerChart ?? echarts.init(developerChartRef.value);
  const topDevelopers = [...developerStats.value]
    .sort((a, b) => (b.averageFinalScore || 0) - (a.averageFinalScore || 0))
    .slice(0, 8);
  developerChart.setOption({
    color: ["#904d00"],
    grid: { left: 48, right: 18, top: 18, bottom: 36 },
    xAxis: {
      type: "category",
      data: topDevelopers.map((item) => item.developerName),
      axisLabel: { color: "#564334", interval: 0, rotate: 18 },
      axisLine: { lineStyle: { color: "rgba(221,193,174,0.4)" } }
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 100,
      axisLabel: { color: "#564334" },
      splitLine: { lineStyle: { color: "rgba(221,193,174,0.18)" } }
    },
    tooltip: { trigger: "axis" },
    series: [
      {
        type: "line",
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 },
        data: topDevelopers.map((item) => item.averageFinalScore || 0)
      }
    ]
  });
};

const handleResize = () => {
  projectChart?.resize();
  riskChart?.resize();
  developerChart?.resize();
};

watch(
  () => [projectStats.value, developerStats.value, scoreStats.value.reviewCount],
  async () => {
    await nextTick();
    renderCharts();
  }
);

onMounted(() => {
  loadDashboard();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  projectChart?.dispose();
  riskChart?.dispose();
  developerChart?.dispose();
});
</script>

<style scoped>
.dashboard-page {
  display: grid;
  gap: 28px;
}

.toolbar-panel,
.metric-card,
.chart-card,
.summary-card {
  background: var(--cr-surface-low);
  box-shadow: var(--cr-shadow-card);
  border-radius: 8px;
}

.toolbar-panel {
  padding: 14px 16px;
}

.range-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: nowrap;
  overflow-x: auto;
}

.range-chip {
  border: none;
  background: transparent;
  color: var(--cr-text-soft);
  border-radius: 8px;
  padding: 10px 14px;
  font-weight: 700;
  cursor: pointer;
}

.range-chip.active {
  background: var(--cr-surface-paper);
  color: var(--cr-primary-deep);
  box-shadow: 0 8px 18px rgba(144, 77, 0, 0.06);
}

.custom-inline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.custom-inline :deep(.el-date-editor) {
  width: 320px;
  max-width: 320px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.metric-card {
  padding: 22px 24px;
  background: var(--cr-surface-paper);
}

.metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 22px;
}

.metric-head span {
  color: rgba(86, 67, 52, 0.78);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-head em {
  font-style: normal;
  font-size: 10px;
  font-weight: 800;
  color: var(--cr-primary);
  background: rgba(255, 140, 0, 0.12);
  padding: 4px 8px;
  border-radius: 999px;
}

.metric-card strong {
  display: block;
  font-size: 42px;
  line-height: 1;
  color: var(--cr-text);
}

.metric-card small {
  display: block;
  margin-top: 12px;
  color: var(--cr-text-soft);
  line-height: 1.7;
}

.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 0.9fr);
  gap: 20px;
}

.chart-card,
.summary-card {
  padding: 26px;
}

.primary-card {
  background: var(--cr-surface-low);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.card-head h4,
.summary-card h4 {
  margin: 0;
  font-size: 30px;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--cr-text);
}

.card-head p,
.summary-text {
  margin: 8px 0 0;
  color: var(--cr-text-soft);
  line-height: 1.8;
}

.compact h4 {
  font-size: 22px;
}

.live-tag {
  color: var(--cr-primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  white-space: nowrap;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.chart-box.small {
  height: 280px;
}

.distribution-list {
  display: grid;
  gap: 18px;
  margin-top: 24px;
}

.distribution-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.distribution-head span {
  color: var(--cr-text-soft);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.distribution-head strong {
  font-size: 12px;
  color: var(--cr-text-soft);
}

.distribution-track {
  height: 6px;
  border-radius: 999px;
  background: rgba(26, 28, 25, 0.06);
  overflow: hidden;
}

.distribution-bar {
  height: 100%;
  background: var(--cr-primary);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

@media (max-width: 1080px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .insight-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }

  .custom-inline :deep(.el-date-editor) {
    width: 280px;
    max-width: 280px;
  }
}
</style>
