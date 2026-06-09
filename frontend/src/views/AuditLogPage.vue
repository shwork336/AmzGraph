<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import { useMessage } from 'naive-ui';
import { listAuditLogs } from '../api/client';
import type { OperationAuditLogResponse } from '../api/types';
import { displayTime, errorMessage } from '../utils/display';
import { auditActionOptions, auditTargetTypeOptions } from '../utils/options';
import { createPaginationState, requestPage, resetPaginationPage, syncPagination } from '../utils/pagination';

const message = useMessage();
const loading = ref(false);
const auditLogs = ref<OperationAuditLogResponse[]>([]);
const errorText = ref('');

const filters = reactive({
  taskId: '',
  action: '',
  operatorId: '',
  targetType: '',
  targetId: ''
});

const pagination = createPaginationState();

async function loadAuditLogs() {
  loading.value = true;
  errorText.value = '';
  try {
    const page = await listAuditLogs({
      taskId: filters.taskId,
      action: filters.action,
      operatorId: filters.operatorId,
      targetType: filters.targetType,
      targetId: filters.targetId,
      page: requestPage(pagination.page),
      size: pagination.size
    });
    auditLogs.value = page.items;
    syncPagination(pagination, page);
  } catch (error) {
    errorText.value = errorMessage(error, '审计日志加载失败');
    message.error(errorText.value);
  } finally {
    loading.value = false;
  }
}

function resetPageAndLoad() {
  resetPaginationPage(pagination);
  loadAuditLogs();
}

function resetFilters() {
  filters.taskId = '';
  filters.action = '';
  filters.operatorId = '';
  filters.targetType = '';
  filters.targetId = '';
  resetPageAndLoad();
}

const columns: DataTableColumns<OperationAuditLogResponse> = [
  { title: '时间', key: 'createdAt', width: 170, render(row) { return displayTime(row.createdAt); } },
  { title: '动作', key: 'action', width: 260 },
  { title: '操作人', key: 'operatorId', width: 180 },
  { title: '任务 ID', key: 'taskId', width: 190, ellipsis: { tooltip: true }, render(row) { return row.taskId ?? '-'; } },
  { title: '目标类型', key: 'targetType', width: 150 },
  { title: '目标 ID', key: 'targetId', width: 210, ellipsis: { tooltip: true } },
  { title: '原因', key: 'reason', width: 240, ellipsis: { tooltip: true }, render(row) { return row.reason ?? '-'; } },
  { title: '详情 JSON', key: 'detailJson', ellipsis: { tooltip: true }, render(row) { return row.detailJson ?? '-'; } }
];

onMounted(loadAuditLogs);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card title="操作审计">
        <n-space vertical>
          <div class="toolbar">
            <n-input
              v-model:value="filters.taskId"
              class="toolbar-field"
              clearable
              placeholder="任务 ID"
              @keyup.enter="resetPageAndLoad"
            />
            <n-select
              v-model:value="filters.action"
              class="toolbar-field"
              clearable
              :options="auditActionOptions"
              @update:value="resetPageAndLoad"
            />
            <n-input
              v-model:value="filters.operatorId"
              class="toolbar-field"
              clearable
              placeholder="操作人"
              @keyup.enter="resetPageAndLoad"
            />
            <n-select
              v-model:value="filters.targetType"
              class="toolbar-field"
              clearable
              :options="auditTargetTypeOptions"
              @update:value="resetPageAndLoad"
            />
            <n-input
              v-model:value="filters.targetId"
              class="toolbar-field"
              clearable
              placeholder="目标 ID"
              @keyup.enter="resetPageAndLoad"
            />
            <n-button type="primary" :loading="loading" @click="resetPageAndLoad">查询</n-button>
            <n-button @click="resetFilters">重置</n-button>
          </div>
          <n-alert v-if="errorText" type="error">{{ errorText }}</n-alert>

          <n-data-table
            :columns="columns"
            :data="auditLogs"
            :loading="loading"
            :bordered="false"
            :empty-description="'暂无审计日志'"
          />

          <n-pagination
            v-model:page="pagination.page"
            v-model:page-size="pagination.size"
            :item-count="pagination.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadAuditLogs"
            @update:page-size="resetPageAndLoad"
          />
        </n-space>
      </n-card>
    </n-space>
  </main>
</template>
