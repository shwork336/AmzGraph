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

const route = useRoute();
const router = useRouter();
const message = useMessage();

const taskIdInput = ref(String(route.params.taskId ?? ''));
const operatorId = ref(localStorage.getItem('amzgraph.operatorId') ?? 'operator@example.com');
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

const filters = reactive({
  format: null as string | null,
  status: null as string | null,
  page: 1,
  size: 10
});

const exportPage = reactive({
  totalItems: 0,
  totalPages: 0
});

const auditPage = reactive({
  page: 1,
  size: 10,
  totalItems: 0,
  totalPages: 0
});

const formatOptions = [
  { label: '全部格式', value: '' },
  { label: 'ZIP', value: 'ZIP' },
  { label: 'Markdown', value: 'MARKDOWN' },
  { label: 'Excel', value: 'EXCEL' },
  { label: 'Word', value: 'WORD' }
];

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: 'PENDING', value: 'PENDING' },
  { label: 'RUNNING', value: 'RUNNING' },
  { label: 'SUCCEEDED', value: 'SUCCEEDED' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'CANCELED', value: 'CANCELED' }
];

const currentTaskId = computed(() => String(route.params.taskId ?? ''));

function statusType(status: string) {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'FAILED') return 'error';
  if (status === 'CANCELED') return 'warning';
  if (status === 'RUNNING') return 'info';
  return 'default';
}

function displayTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

async function loadTask() {
  task.value = await getTaskDetail(currentTaskId.value);
}

async function loadExports() {
  exportsLoading.value = true;
  try {
    const page = await listExportPackages(currentTaskId.value, {
      format: filters.format,
      status: filters.status,
      page: filters.page - 1,
      size: filters.size
    });
    exportsData.value = page.items;
    exportPage.totalItems = page.totalItems;
    exportPage.totalPages = page.totalPages;
  } finally {
    exportsLoading.value = false;
  }
}

async function loadAuditLogs() {
  auditLoading.value = true;
  try {
    const page = await listAuditLogs(currentTaskId.value, auditPage.page - 1, auditPage.size);
    auditLogs.value = page.items;
    auditPage.totalItems = page.totalItems;
    auditPage.totalPages = page.totalPages;
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
    errorText.value = error instanceof Error ? error.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

function openTask() {
  const value = taskIdInput.value.trim();
  if (value && value !== currentTaskId.value) {
    router.push(`/tasks/${encodeURIComponent(value)}/export`);
  }
}

async function createExport(format: string) {
  try {
    await createPendingExportPackage(currentTaskId.value, format);
    message.success(`已创建 ${format} 导出记录`);
    await loadExports();
    await loadAuditLogs();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建失败');
  }
}

async function runExport(row: ExportPackageResponse) {
  try {
    await runExportPackage(row.exportPackageId);
    message.success('导出已执行');
    await loadExports();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '执行失败');
  }
}

async function retryExport(row: ExportPackageResponse) {
  try {
    await retryExportPackage(row.exportPackageId);
    message.success('已创建重试导出');
    await loadExports();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '重试失败');
  }
}

function openCancel(row: ExportPackageResponse) {
  cancelTarget.value = row;
  cancelReason.value = '';
  cancelModalVisible.value = true;
}

async function submitCancel() {
  if (!cancelTarget.value || !cancelReason.value.trim()) {
    message.warning('需要填写取消原因');
    return;
  }
  localStorage.setItem('amzgraph.operatorId', operatorId.value);
  try {
    await cancelExportPackage(
      cancelTarget.value.exportPackageId,
      operatorId.value,
      cancelReason.value.trim()
    );
    message.success('导出记录已取消');
    cancelModalVisible.value = false;
    await loadExports();
    await loadAuditLogs();
  } catch (error) {
    message.error(error instanceof Error ? error.message : '取消失败');
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
      return h(NTag, { type: statusType(row.status), size: 'small' }, { default: () => row.status });
    }
  },
  { title: '文件', key: 'fileUrl', ellipsis: { tooltip: true } },
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
          : null
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

      <n-card title="导出历史">
        <n-space vertical>
          <div class="toolbar">
            <n-select
              v-model:value="filters.format"
              class="toolbar-field"
              clearable
              :options="formatOptions"
              @update:value="filters.page = 1; loadExports()"
            />
            <n-select
              v-model:value="filters.status"
              class="toolbar-field"
              clearable
              :options="statusOptions"
              @update:value="filters.page = 1; loadExports()"
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
          />
          <n-pagination
            v-model:page="filters.page"
            v-model:page-size="filters.size"
            :item-count="exportPage.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadExports"
            @update:page-size="filters.page = 1; loadExports()"
          />
        </n-space>
      </n-card>

      <n-card title="操作审计">
        <n-space vertical>
          <n-data-table
            :columns="auditColumns"
            :data="auditLogs"
            :loading="auditLoading"
            :bordered="false"
          />
          <n-pagination
            v-model:page="auditPage.page"
            v-model:page-size="auditPage.size"
            :item-count="auditPage.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadAuditLogs"
            @update:page-size="auditPage.page = 1; loadAuditLogs()"
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
