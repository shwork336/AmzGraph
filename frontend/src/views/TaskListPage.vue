<script setup lang="ts">
import { h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import { listTasks } from '../api/client';
import type { ListingTaskSummaryResponse } from '../api/types';

const router = useRouter();
const message = useMessage();
const loading = ref(false);
const tasks = ref<ListingTaskSummaryResponse[]>([]);

const filters = reactive({
  status: null as string | null,
  marketplace: 'US' as string | null,
  categoryCode: 'CAR_STEREO' as string | null,
  page: 1,
  size: 10
});

const pageState = reactive({
  totalItems: 0,
  totalPages: 0
});

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: 'WAIT_BRIEF_APPROVE', value: 'WAIT_BRIEF_APPROVE' },
  { label: 'GENERATING', value: 'GENERATING' },
  { label: 'WAIT_FINAL_APPROVE', value: 'WAIT_FINAL_APPROVE' },
  { label: 'COMPLETED', value: 'COMPLETED' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'CANCELLED', value: 'CANCELLED' }
];

const marketplaceOptions = [
  { label: '全部站点', value: '' },
  { label: 'US', value: 'US' },
  { label: 'UK', value: 'UK' }
];

const categoryOptions = [
  { label: '全部类目', value: '' },
  { label: 'CAR_STEREO', value: 'CAR_STEREO' }
];

function statusType(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'error';
  if (status === 'WAIT_FINAL_APPROVE') return 'warning';
  if (status === 'GENERATING') return 'info';
  return 'default';
}

function displayTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

async function loadTasks() {
  loading.value = true;
  try {
    const page = await listTasks({
      status: filters.status,
      marketplace: filters.marketplace,
      categoryCode: filters.categoryCode,
      page: filters.page - 1,
      size: filters.size
    });
    tasks.value = page.items;
    pageState.totalItems = page.totalItems;
    pageState.totalPages = page.totalPages;
  } catch (error) {
    message.error(error instanceof Error ? error.message : '任务列表加载失败');
  } finally {
    loading.value = false;
  }
}

function resetPageAndLoad() {
  filters.page = 1;
  loadTasks();
}

function openExport(taskId: string) {
  router.push(`/tasks/${encodeURIComponent(taskId)}/export`);
}

const columns: DataTableColumns<ListingTaskSummaryResponse> = [
  { title: '任务 ID', key: 'taskId', width: 220, ellipsis: { tooltip: true } },
  {
    title: '主状态',
    key: 'status',
    width: 170,
    render(row) {
      return h(NTag, { type: statusType(row.status), size: 'small' }, { default: () => row.status });
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
            <n-button type="primary" @click="router.push('/tasks/new')">创建任务</n-button>
            <n-select
              v-model:value="filters.status"
              class="toolbar-field"
              clearable
              :options="statusOptions"
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
          <n-data-table
            :columns="columns"
            :data="tasks"
            :loading="loading"
            :bordered="false"
          />
          <n-pagination
            v-model:page="filters.page"
            v-model:page-size="filters.size"
            :item-count="pageState.totalItems"
            show-size-picker
            :page-sizes="[10, 20, 50]"
            @update:page="loadTasks"
            @update:page-size="filters.page = 1; loadTasks()"
          />
        </n-space>
      </n-card>
    </n-space>
  </main>
</template>
