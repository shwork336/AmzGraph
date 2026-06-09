<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import {
  approveFinalSelection,
  getTaskDetail,
  listImageVersions,
  listTextVersions
} from '../api/client';
import type {
  ImageVersionResponse,
  ListingTaskDetailResponse,
  TextVersionResponse
} from '../api/types';
import { displayTime, displayValue, errorMessage } from '../utils/display';
import { statusTagType } from '../utils/status';
import { taskDetailPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const submitting = ref(false);
const errorText = ref('');
const task = ref<ListingTaskDetailResponse | null>(null);
const textVersions = ref<TextVersionResponse[]>([]);
const imageVersions = ref<ImageVersionResponse[]>([]);
const selectedTextVersionId = ref('');
const selectedImageVersionId = ref('');

const currentTaskId = computed(() => String(route.params.taskId ?? ''));
const selectedTextVersion = computed(() =>
  textVersions.value.find((version) => version.versionId === selectedTextVersionId.value) ?? null
);
const selectedImageVersion = computed(() =>
  imageVersions.value.find((version) => version.versionId === selectedImageVersionId.value) ?? null
);

async function loadPage() {
  loading.value = true;
  errorText.value = '';
  try {
    const [taskDetail, texts, images] = await Promise.all([
      getTaskDetail(currentTaskId.value),
      listTextVersions(currentTaskId.value),
      listImageVersions(currentTaskId.value)
    ]);
    task.value = taskDetail;
    textVersions.value = texts;
    imageVersions.value = images;
    selectedTextVersionId.value = taskDetail.selectedTextVersionId ?? texts[0]?.versionId ?? '';
    selectedImageVersionId.value = taskDetail.selectedImageVersionId ?? images[0]?.versionId ?? '';
  } catch (error) {
    errorText.value = errorMessage(error, '终审数据加载失败');
  } finally {
    loading.value = false;
  }
}

async function submitFinalSelection() {
  if (!selectedTextVersionId.value || !selectedImageVersionId.value) {
    message.warning('需要同时选择文案版本和图片版本');
    return;
  }

  submitting.value = true;
  try {
    const result = await approveFinalSelection(
      currentTaskId.value,
      selectedTextVersionId.value,
      selectedImageVersionId.value
    );
    message.success(`终审完成，任务状态 ${result.status}`);
    await loadPage();
  } catch (error) {
    message.error(errorMessage(error, '终审提交失败'));
  } finally {
    submitting.value = false;
  }
}

const textColumns: DataTableColumns<TextVersionResponse> = [
  { title: '文案版本', key: 'versionId', width: 190, ellipsis: { tooltip: true } },
  { title: '标题', key: 'title', ellipsis: { tooltip: true }, render(row) { return row.title ?? '-'; } },
  { title: '质量分', key: 'qualityScore', width: 90, render(row) { return row.qualityScore ?? '-'; } },
  {
    title: '当前',
    key: 'selected',
    width: 90,
    render(row) {
      return row.selected
        ? h(NTag, { type: 'success', size: 'small' }, { default: () => '最终' })
        : '-';
    }
  },
  { title: '创建时间', key: 'createdAt', width: 170, render(row) { return displayTime(row.createdAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render(row) {
      return h(
        NButton,
        { size: 'small', type: selectedTextVersionId.value === row.versionId ? 'primary' : 'default', onClick: () => { selectedTextVersionId.value = row.versionId; } },
        { default: () => '选择' }
      );
    }
  }
];

const imageColumns: DataTableColumns<ImageVersionResponse> = [
  { title: '图片版本', key: 'versionId', width: 190, ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render(row) {
      return h(NTag, { type: statusTagType(row.status), size: 'small' }, { default: () => row.status });
    }
  },
  { title: '供应商', key: 'imageProvider', width: 140, render(row) { return row.imageProvider ?? '-'; } },
  { title: '质量分', key: 'qualityScore', width: 90, render(row) { return row.qualityScore ?? '-'; } },
  {
    title: '当前',
    key: 'selected',
    width: 90,
    render(row) {
      return row.selected
        ? h(NTag, { type: 'success', size: 'small' }, { default: () => '最终' })
        : '-';
    }
  },
  { title: '创建时间', key: 'createdAt', width: 170, render(row) { return displayTime(row.createdAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render(row) {
      return h(
        NButton,
        { size: 'small', type: selectedImageVersionId.value === row.versionId ? 'primary' : 'default', onClick: () => { selectedImageVersionId.value = row.versionId; } },
        { default: () => '选择' }
      );
    }
  }
];

watch(
  () => route.params.taskId,
  () => {
    loadPage();
  }
);

onMounted(loadPage);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card>
        <n-space vertical>
          <div class="toolbar">
            <n-button @click="router.push(taskDetailPath(currentTaskId))">返回详情</n-button>
            <n-button type="primary" :loading="loading" @click="loadPage">刷新</n-button>
            <n-button type="success" :loading="submitting" @click="submitFinalSelection">提交终审</n-button>
          </div>
          <n-alert v-if="errorText" type="error">{{ errorText }}</n-alert>
        </n-space>
      </n-card>

      <n-spin :show="loading">
        <n-space vertical size="large">
          <n-card title="任务状态">
            <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen">
              <n-gi>
                <div class="status-tile">
                  <span>主流程</span>
                  <n-tag :type="statusTagType(task?.status)" size="small">{{ task?.status ?? '-' }}</n-tag>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>文案</span>
                  <n-tag :type="statusTagType(task?.textStatus)" size="small">{{ task?.textStatus ?? '-' }}</n-tag>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>图片</span>
                  <n-tag :type="statusTagType(task?.imageStatus)" size="small">{{ task?.imageStatus ?? '-' }}</n-tag>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>更新时间</span>
                  <strong>{{ displayTime(task?.updatedAt) }}</strong>
                </div>
              </n-gi>
            </n-grid>
          </n-card>

          <n-card title="当前选择">
            <n-descriptions :column="2" bordered label-placement="left">
              <n-descriptions-item label="文案版本">
                <n-text class="mono">{{ selectedTextVersionId || '-' }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="图片版本">
                <n-text class="mono">{{ selectedImageVersionId || '-' }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="文案标题">
                {{ displayValue(selectedTextVersion?.title) }}
              </n-descriptions-item>
              <n-descriptions-item label="图片状态">
                <n-tag :type="statusTagType(selectedImageVersion?.status)" size="small">
                  {{ selectedImageVersion?.status ?? '-' }}
                </n-tag>
              </n-descriptions-item>
            </n-descriptions>
          </n-card>

          <n-card title="选择文案版本">
            <n-data-table
              :columns="textColumns"
              :data="textVersions"
              :loading="loading"
              :bordered="false"
            />
          </n-card>

          <n-card title="选择图片版本">
            <n-data-table
              :columns="imageColumns"
              :data="imageVersions"
              :loading="loading"
              :bordered="false"
            />
          </n-card>
        </n-space>
      </n-spin>
    </n-space>
  </main>
</template>
