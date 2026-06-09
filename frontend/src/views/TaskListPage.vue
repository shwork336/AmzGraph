<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import { listTasks } from '../api/client';
import type { ListingTaskSummaryResponse } from '../api/types';
import { displayTime, errorMessage } from '../utils/display';
import { categoryOptions, marketplaceOptions, taskStatusOptions } from '../utils/options';
import { createPaginationState, requestPage, resetPaginationPage, syncPagination } from '../utils/pagination';
import { statusTagType } from '../utils/status';
import { taskCreatePath, taskDetailPath, taskSubPath } from '../utils/taskRoutes';

const router = useRouter();
const message = useMessage();
const loading = ref(false);
const tasks = ref<ListingTaskSummaryResponse[]>([]);
const errorText = ref('');

const filters = reactive({
  status: null as string | null,
  marketplace: 'US' as string | null,
  categoryCode: 'CAR_STEREO' as string | null
});

const pagination = createPaginationState();

async function loadTasks() {
  loading.value = true;
  errorText.value = '';
  try {
    const page = await listTasks({
      status: filters.status,
      marketplace: filters.marketplace,
      categoryCode: filters.categoryCode,
      page: requestPage(pagination.page),
      size: pagination.size
    });
    tasks.value = page.items;
    syncPagination(pagination, page);
  } catch (error) {
    errorText.value = errorMessage(error, '任务列表加载失败');
    message.error(errorText.value);
  } finally {
    loading.value = false;
  }
}

function resetPageAndLoad() {
  resetPaginationPage(pagination);
  loadTasks();
}

function openExport(taskId: string) {
  router.push(taskSubPath(taskId, 'export'));
}

function openDetail(taskId: string) {
  router.push(taskDetailPath(taskId));
}

function rowKey(row: ListingTaskSummaryResponse) {
  return row.taskId;
}

const columns: DataTableColumns<ListingTaskSummaryResponse> = [
  {
    title: '任务 ID',
    key: 'taskId',
    width: 220,
    ellipsis: { tooltip: true },
    render(row) {
      return h(
        NButton,
        { text: true, type: 'primary', onClick: () => openDetail(row.taskId) },
        { default: () => row.taskId }
      );
    }
  },
  {
    title: '主状态',
    key: 'status',
    width: 170,
    render(row) {
      return h(NTag, { type: statusTagType(row.status), size: 'small' }, { default: () => row.status });
    }
  },
  { title: 'Brief', key: 'briefStatus', width: 140 },
  { title: '文案', key: 'textStatus', width: 140 },
  { title: '图片', key: 'imageStatus', width: 140 },
  { title: '类目', key: 'categoryCode', width: 140 },
  { title: '站点', key: 'marketplace', width: 90 },
  { title: '最终文案', key: 'selectedTextVersionId', width: 180, ellipsis: { tooltip: true }, render(row) { return row.selectedTextVersionId ?? '-'; } },
  { title: '最终图片', key: 'selectedImageVersionId', width: 180, ellipsis: { tooltip: true }, render(row) { return row.selectedImageVersionId ?? '-'; } },
  { title: '更新时间', key: 'updatedAt', width: 170, render(row) { return displayTime(row.updatedAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 140,
    render(row) {
      return h(NButton, { size: 'small', type: 'primary', onClick: () => openExport(row.taskId) }, { default: () => '归档导出' });
    }
  }
];

onMounted(loadTasks);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card title="任务列表">
        <n-space vertical>
          <div class="toolbar">
            <n-button type="primary" @click="router.push(taskCreatePath())">创建任务</n-button>
            <n-select
              v-model:value="filters.status"
              class="toolbar-field"
              clearable
              :options="taskStatusOptions"
              @update:value="resetPageAndLoad"
            />
            <n-select
              v-model:value="filters.marketplace"
              class="toolbar-field"
              clearable
              :options="marketplaceOptions"
              @update:value="resetPageAndLoad"
            />
            <n-select
              v-model:value="filters.categoryCode"
              class="toolbar-field"
              clearable
              :options="categoryOptions"
              @update:value="resetPageAndLoad"
            />
            <n-button :loading="loading" @click="loadTasks">刷新</n-button>
          </div>
          <n-alert v-if="errorText" type="error">{{ errorText }}</n-alert>
          <n-data-table
            :columns="columns"
            :data="tasks"
            :loading="loading"
            :bordered="false"
            remote
            :row-key="rowKey"
            :pagination="false"
            :empty-description="'暂无任务'"
          />
          <n-pagination
            v-model:page="pagination.page"
            v-model:page-size="pagination.size"
            :item-count="pagination.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadTasks"
            @update:page-size="resetPageAndLoad"
          />
        </n-space>
      </n-card>
    </n-space>
  </main>
</template>
