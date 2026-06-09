<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import {
  createPendingExportPackage,
  cancelExportPackage,
  getTaskDetail,
  listAuditLogs,
  listExportPackages,
  retryExportPackage,
  runExportPackage
} from '../api/client';
import type {
  ExportPackageResponse,
  ListingTaskDetailResponse,
  OperationAuditLogResponse
} from '../api/types';
import { displayTime, errorMessage } from '../utils/display';
import { loadOperatorId, saveOperatorId } from '../utils/operator';
import { exportFormatOptions, exportStatusOptions } from '../utils/options';
import { createPaginationState, requestPage, resetPaginationPage, syncPagination } from '../utils/pagination';
import { statusTagType } from '../utils/status';
import { taskSubPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const taskIdInput = ref(String(route.params.taskId ?? ''));
const operatorId = ref(loadOperatorId());
const loading = ref(false);
const exportsLoading = ref(false);
const auditLoading = ref(false);
const task = ref<ListingTaskDetailResponse | null>(null);
const exportsData = ref<ExportPackageResponse[]>([]);
const auditLogs = ref<OperationAuditLogResponse[]>([]);
const errorText = ref('');
const cancelModalVisible = ref(false);
const cancelTarget = ref<ExportPackageResponse | null>(null);
const cancelReason = ref('');
const selectedExportPackageId = ref('');

const filters = reactive({
  format: null as string | null,
  status: null as string | null
});

const exportPagination = createPaginationState();
const auditPagination = createPaginationState();

const currentTaskId = computed(() => String(route.params.taskId ?? ''));
const selectedExportPackage = computed(() =>
  exportsData.value.find((item) => item.exportPackageId === selectedExportPackageId.value) ?? exportsData.value[0] ?? null
);
const exportStatusSummary = computed(() => {
  const summary = {
    total: exportsData.value.length,
    pending: 0,
    running: 0,
    succeeded: 0,
    failed: 0,
    canceled: 0
  };
  exportsData.value.forEach((item) => {
    if (item.status === 'PENDING') summary.pending += 1;
    if (item.status === 'RUNNING') summary.running += 1;
    if (item.status === 'SUCCEEDED') summary.succeeded += 1;
    if (item.status === 'FAILED') summary.failed += 1;
    if (item.status === 'CANCELED') summary.canceled += 1;
  });
  return summary;
});

async function loadTask() {
  task.value = await getTaskDetail(currentTaskId.value);
}

async function loadExports() {
  exportsLoading.value = true;
  try {
    const page = await listExportPackages(currentTaskId.value, {
      format: filters.format,
      status: filters.status,
      page: requestPage(exportPagination.page),
      size: exportPagination.size
    });
    exportsData.value = page.items;
    selectedExportPackageId.value = page.items[0]?.exportPackageId ?? '';
    syncPagination(exportPagination, page);
  } finally {
    exportsLoading.value = false;
  }
}

async function loadAuditLogs() {
  auditLoading.value = true;
  try {
    const page = await listAuditLogs({
      taskId: currentTaskId.value,
      page: requestPage(auditPagination.page),
      size: auditPagination.size
    });
    auditLogs.value = page.items;
    syncPagination(auditPagination, page);
  } finally {
    auditLoading.value = false;
  }
}

async function refreshAll() {
  errorText.value = '';
  loading.value = true;
  try {
    await Promise.all([loadTask(), loadExports(), loadAuditLogs()]);
  } catch (error) {
    errorText.value = errorMessage(error, '加载失败');
  } finally {
    loading.value = false;
  }
}

function resetExportPageAndLoad() {
  resetPaginationPage(exportPagination);
  loadExports();
}

function resetAuditPageAndLoad() {
  resetPaginationPage(auditPagination);
  loadAuditLogs();
}

function openTask() {
  const value = taskIdInput.value.trim();
  if (value && value !== currentTaskId.value) {
    router.push(taskSubPath(value, 'export'));
  }
}

async function createExport(format: string) {
  try {
    await createPendingExportPackage(currentTaskId.value, format);
    message.success(`已创建 ${format} 导出记录`);
    await loadExports();
    await loadAuditLogs();
  } catch (error) {
    message.error(errorMessage(error, '创建失败'));
  }
}

async function runExport(row: ExportPackageResponse) {
  try {
    await runExportPackage(row.exportPackageId);
    message.success('导出已执行');
    await loadExports();
  } catch (error) {
    message.error(errorMessage(error, '执行失败'));
  }
}

async function retryExport(row: ExportPackageResponse) {
  try {
    await retryExportPackage(row.exportPackageId);
    message.success('已创建重试导出');
    await loadExports();
  } catch (error) {
    message.error(errorMessage(error, '重试失败'));
  }
}

function openCancel(row: ExportPackageResponse) {
  cancelTarget.value = row;
  cancelReason.value = '';
  cancelModalVisible.value = true;
}

function displayLink(value?: string | null) {
  if (!value) {
    return '-';
  }
  return h('a', { href: value, target: '_blank', rel: 'noreferrer' }, value);
}

async function submitCancel() {
  if (!cancelTarget.value || !cancelReason.value.trim()) {
    message.warning('需要填写取消原因');
    return;
  }
  const savedOperatorId = saveOperatorId(operatorId.value);
  try {
    await cancelExportPackage(
      cancelTarget.value.exportPackageId,
      savedOperatorId,
      cancelReason.value.trim()
    );
    message.success('导出记录已取消');
    cancelModalVisible.value = false;
    await loadExports();
    await loadAuditLogs();
  } catch (error) {
    message.error(errorMessage(error, '取消失败'));
  }
}

const exportColumns: DataTableColumns<ExportPackageResponse> = [
  { title: '导出包', key: 'exportPackageId', width: 210, ellipsis: { tooltip: true } },
  { title: '格式', key: 'format', width: 110 },
  {
    title: '状态',
    key: 'status',
    width: 120,
    render(row) {
      return h(NTag, { type: statusTagType(row.status), size: 'small' }, { default: () => row.status });
    }
  },
  { title: '文件', key: 'fileUrl', ellipsis: { tooltip: true }, render(row) { return displayLink(row.fileUrl); } },
  { title: 'Manifest', key: 'manifestUrl', width: 170, ellipsis: { tooltip: true }, render(row) { return displayLink(row.manifestUrl); } },
  { title: '失败/取消原因', key: 'failureReason', ellipsis: { tooltip: true },
    render(row) { return row.failureReason ?? row.cancelReason ?? '-'; } },
  { title: '更新时间', key: 'updatedAt', width: 170, render(row) { return displayTime(row.updatedAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 230,
    render(row) {
      return h('div', { class: 'table-actions' }, [
        row.status === 'PENDING'
          ? h(NButton, { size: 'small', type: 'primary', onClick: () => runExport(row) }, { default: () => '运行' })
          : null,
        row.status === 'PENDING'
          ? h(NButton, { size: 'small', onClick: () => openCancel(row) }, { default: () => '取消' })
          : null,
        row.status === 'FAILED'
          ? h(NButton, { size: 'small', type: 'warning', onClick: () => retryExport(row) }, { default: () => '重试' })
          : null,
        h(NButton, { size: 'small', onClick: () => { selectedExportPackageId.value = row.exportPackageId; } }, { default: () => '详情' })
      ]);
    }
  }
];

const auditColumns: DataTableColumns<OperationAuditLogResponse> = [
  { title: '时间', key: 'createdAt', width: 170, render(row) { return displayTime(row.createdAt); } },
  { title: '动作', key: 'action', width: 260 },
  { title: '操作人', key: 'operatorId', width: 180 },
  { title: '目标', key: 'targetId', width: 190, ellipsis: { tooltip: true } },
  { title: '原因', key: 'reason', ellipsis: { tooltip: true } }
];

watch(
  () => route.params.taskId,
  () => {
    taskIdInput.value = currentTaskId.value;
    refreshAll();
  }
);

onMounted(refreshAll);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card>
        <n-space vertical>
          <div class="toolbar">
            <n-input-group class="toolbar-field">
              <n-input-group-label>Task ID</n-input-group-label>
              <n-input v-model:value="taskIdInput" @keyup.enter="openTask" />
            </n-input-group>
            <n-input-group class="toolbar-field">
              <n-input-group-label>Operator</n-input-group-label>
              <n-input v-model:value="operatorId" />
            </n-input-group>
            <n-button type="primary" @click="openTask">打开任务</n-button>
            <n-button :loading="loading" @click="refreshAll">刷新</n-button>
          </div>
          <n-alert v-if="errorText" type="error">{{ errorText }}</n-alert>
        </n-space>
      </n-card>

      <n-spin :show="loading">
        <n-grid :cols="4" :x-gap="16" :y-gap="16" responsive="screen">
          <n-gi>
            <n-card>
              <n-statistic label="任务状态" :value="task?.status ?? '-'" />
            </n-card>
          </n-gi>
          <n-gi>
            <n-card>
              <n-statistic label="站点" :value="task?.marketplace ?? '-'" />
            </n-card>
          </n-gi>
          <n-gi>
            <n-card>
              <n-statistic label="最终文案版本" :value="task?.selectedTextVersionId ?? '-'" />
            </n-card>
          </n-gi>
          <n-gi>
            <n-card>
              <n-statistic label="最终图片版本" :value="task?.selectedImageVersionId ?? '-'" />
            </n-card>
          </n-gi>
        </n-grid>
      </n-spin>

      <n-card title="导出状态摘要">
        <n-grid :cols="6" :x-gap="12" :y-gap="12" responsive="screen">
          <n-gi>
            <div class="status-tile">
              <span>当前页总数</span>
              <strong>{{ exportStatusSummary.total }}</strong>
            </div>
          </n-gi>
          <n-gi>
            <div class="status-tile">
              <span>PENDING</span>
              <strong>{{ exportStatusSummary.pending }}</strong>
            </div>
          </n-gi>
          <n-gi>
            <div class="status-tile">
              <span>RUNNING</span>
              <strong>{{ exportStatusSummary.running }}</strong>
            </div>
          </n-gi>
          <n-gi>
            <div class="status-tile">
              <span>SUCCEEDED</span>
              <strong>{{ exportStatusSummary.succeeded }}</strong>
            </div>
          </n-gi>
          <n-gi>
            <div class="status-tile">
              <span>FAILED</span>
              <strong>{{ exportStatusSummary.failed }}</strong>
            </div>
          </n-gi>
          <n-gi>
            <div class="status-tile">
              <span>CANCELED</span>
              <strong>{{ exportStatusSummary.canceled }}</strong>
            </div>
          </n-gi>
        </n-grid>
      </n-card>

      <n-card title="导出历史">
        <n-space vertical>
          <div class="toolbar">
            <n-select
              v-model:value="filters.format"
              class="toolbar-field"
              clearable
              :options="exportFormatOptions"
              @update:value="resetExportPageAndLoad"
            />
            <n-select
              v-model:value="filters.status"
              class="toolbar-field"
              clearable
              :options="exportStatusOptions"
              @update:value="resetExportPageAndLoad"
            />
            <n-button @click="createExport('ZIP')">创建 ZIP</n-button>
            <n-button @click="createExport('MARKDOWN')">创建 Markdown</n-button>
            <n-button @click="createExport('EXCEL')">创建 Excel</n-button>
            <n-button @click="createExport('WORD')">创建 Word</n-button>
          </div>
          <n-data-table
            :columns="exportColumns"
            :data="exportsData"
            :loading="exportsLoading"
            :bordered="false"
            :empty-description="'暂无导出记录'"
          />
          <n-pagination
            v-model:page="exportPagination.page"
            v-model:page-size="exportPagination.size"
            :item-count="exportPagination.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadExports"
            @update:page-size="resetExportPageAndLoad"
          />
        </n-space>
      </n-card>

      <n-card title="导出详情">
        <n-empty v-if="!selectedExportPackage" description="暂无导出记录" />
        <n-space v-else vertical size="large">
          <n-descriptions :column="2" bordered label-placement="left">
            <n-descriptions-item label="导出包">
              <n-text class="mono">{{ selectedExportPackage.exportPackageId }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="格式">{{ selectedExportPackage.format }}</n-descriptions-item>
            <n-descriptions-item label="状态">
              <n-tag :type="statusTagType(selectedExportPackage.status)" size="small">
                {{ selectedExportPackage.status }}
              </n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="任务 ID">
              <n-text class="mono">{{ selectedExportPackage.taskId }}</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="文件">
              <a v-if="selectedExportPackage.fileUrl" :href="selectedExportPackage.fileUrl" target="_blank" rel="noreferrer">
                {{ selectedExportPackage.fileUrl }}
              </a>
              <span v-else>-</span>
            </n-descriptions-item>
            <n-descriptions-item label="Manifest">
              <a v-if="selectedExportPackage.manifestUrl" :href="selectedExportPackage.manifestUrl" target="_blank" rel="noreferrer">
                {{ selectedExportPackage.manifestUrl }}
              </a>
              <span v-else>-</span>
            </n-descriptions-item>
            <n-descriptions-item label="失败原因">
              {{ selectedExportPackage.failureReason ?? '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="取消原因">
              {{ selectedExportPackage.cancelReason ?? '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="取消人">
              {{ selectedExportPackage.canceledBy ?? '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="取消时间">
              {{ displayTime(selectedExportPackage.canceledAt) }}
            </n-descriptions-item>
            <n-descriptions-item label="创建时间">
              {{ displayTime(selectedExportPackage.createdAt) }}
            </n-descriptions-item>
            <n-descriptions-item label="开始时间">
              {{ displayTime(selectedExportPackage.startedAt) }}
            </n-descriptions-item>
            <n-descriptions-item label="更新时间">
              {{ displayTime(selectedExportPackage.updatedAt) }}
            </n-descriptions-item>
          </n-descriptions>

          <n-thing title="包含资产">
            <n-space v-if="selectedExportPackage.includedAssetIds.length">
              <n-tag
                v-for="assetId in selectedExportPackage.includedAssetIds"
                :key="assetId"
                class="mono"
                size="small"
              >
                {{ assetId }}
              </n-tag>
            </n-space>
            <n-empty v-else description="暂无资产 ID" />
          </n-thing>
        </n-space>
      </n-card>

      <n-card title="操作审计">
        <n-space vertical>
          <n-data-table
            :columns="auditColumns"
            :data="auditLogs"
            :loading="auditLoading"
            :bordered="false"
            :empty-description="'暂无审计日志'"
          />
          <n-pagination
            v-model:page="auditPagination.page"
            v-model:page-size="auditPagination.size"
            :item-count="auditPagination.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadAuditLogs"
            @update:page-size="resetAuditPageAndLoad"
          />
        </n-space>
      </n-card>
    </n-space>

    <n-modal v-model:show="cancelModalVisible" preset="card" title="取消导出记录" style="width: 520px">
      <n-form>
        <n-form-item label="导出包">
          <n-text class="mono">{{ cancelTarget?.exportPackageId }}</n-text>
        </n-form-item>
        <n-form-item label="取消原因">
          <n-input v-model:value="cancelReason" type="textarea" placeholder="说明取消原因" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="cancelModalVisible = false">关闭</n-button>
          <n-button type="warning" @click="submitCancel">确认取消</n-button>
        </n-space>
      </template>
    </n-modal>
  </main>
</template>
