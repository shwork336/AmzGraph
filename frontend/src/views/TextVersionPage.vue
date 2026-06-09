<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import { generateTextVersion, getTaskDetail, listTextVersions } from '../api/client';
import type { ListingTaskDetailResponse, TextVersionResponse } from '../api/types';
import { displayTime, displayValue, errorMessage } from '../utils/display';
import { statusTagType } from '../utils/status';
import { taskDetailPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const generating = ref(false);
const errorText = ref('');
const task = ref<ListingTaskDetailResponse | null>(null);
const versions = ref<TextVersionResponse[]>([]);
const selectedVersionId = ref('');

const currentTaskId = computed(() => String(route.params.taskId ?? ''));
const selectedVersion = computed(() =>
  versions.value.find((version) => version.versionId === selectedVersionId.value) ?? versions.value[0] ?? null
);

async function loadPage() {
  loading.value = true;
  errorText.value = '';
  try {
    const [taskDetail, textVersions] = await Promise.all([
      getTaskDetail(currentTaskId.value),
      listTextVersions(currentTaskId.value)
    ]);
    task.value = taskDetail;
    versions.value = textVersions;
    selectedVersionId.value = textVersions[0]?.versionId ?? '';
  } catch (error) {
    errorText.value = errorMessage(error, '文案版本加载失败');
  } finally {
    loading.value = false;
  }
}

async function generateText() {
  generating.value = true;
  try {
    const generated = await generateTextVersion(currentTaskId.value);
    message.success('已生成文案版本');
    await loadPage();
    selectedVersionId.value = generated.versionId;
  } catch (error) {
    message.error(errorMessage(error, '生成失败'));
  } finally {
    generating.value = false;
  }
}

const columns: DataTableColumns<TextVersionResponse> = [
  { title: '版本 ID', key: 'versionId', width: 190, ellipsis: { tooltip: true } },
  { title: 'Brief', key: 'briefVersionId', width: 190, ellipsis: { tooltip: true },
    render(row) { return row.briefVersionId ?? '-'; } },
  {
    title: '选中',
    key: 'selected',
    width: 90,
    render(row) {
      return row.selected
        ? h(NTag, { type: 'success', size: 'small' }, { default: () => '最终' })
        : '-';
    }
  },
  { title: '质量分', key: 'qualityScore', width: 90, render(row) { return row.qualityScore ?? '-'; } },
  { title: '创建时间', key: 'createdAt', width: 170, render(row) { return displayTime(row.createdAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render(row) {
      return h(
        NButton,
        { size: 'small', type: selectedVersionId.value === row.versionId ? 'primary' : 'default', onClick: () => { selectedVersionId.value = row.versionId; } },
        { default: () => '查看' }
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
            <n-button type="success" :loading="generating" @click="generateText">生成文案</n-button>
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
                  <span>Brief</span>
                  <n-tag :type="statusTagType(task?.briefStatus)" size="small">{{ task?.briefStatus ?? '-' }}</n-tag>
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
                  <span>版本数</span>
                  <strong>{{ versions.length }}</strong>
                </div>
              </n-gi>
            </n-grid>
          </n-card>

          <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
            <n-gi>
              <n-card title="文案版本历史">
                <n-data-table
                  :columns="columns"
                  :data="versions"
                  :loading="loading"
                  :bordered="false"
                />
              </n-card>
            </n-gi>

            <n-gi>
              <n-card title="版本详情">
                <n-empty v-if="!selectedVersion" description="暂无文案版本" />
                <n-space v-else vertical size="large">
                  <n-descriptions :column="1" bordered label-placement="left">
                    <n-descriptions-item label="版本 ID">
                      <n-text class="mono">{{ selectedVersion.versionId }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="父版本">
                      <n-text class="mono">{{ selectedVersion.parentVersionId ?? '-' }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="Brief 版本">
                      <n-text class="mono">{{ selectedVersion.briefVersionId ?? '-' }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="质量评分">
                      {{ selectedVersion.qualityScore ?? '-' }}
                    </n-descriptions-item>
                    <n-descriptions-item label="创建时间">
                      {{ displayTime(selectedVersion.createdAt) }}
                    </n-descriptions-item>
                  </n-descriptions>

                  <n-thing title="标题">
                    {{ displayValue(selectedVersion.title) }}
                  </n-thing>

                  <n-thing title="五点描述">
                    <n-empty v-if="!selectedVersion.bulletPoints.length" description="暂无五点描述" />
                    <ol v-else class="content-list">
                      <li v-for="point in selectedVersion.bulletPoints" :key="point">{{ point }}</li>
                    </ol>
                  </n-thing>

                  <n-thing title="产品描述">
                    {{ displayValue(selectedVersion.description) }}
                  </n-thing>

                  <n-thing title="后台搜索词">
                    <n-text class="mono">{{ displayValue(selectedVersion.backendSearchTerms) }}</n-text>
                  </n-thing>

                  <n-thing title="目标关键词">
                    <n-space v-if="selectedVersion.targetKeywords.length">
                      <n-tag
                        v-for="keyword in selectedVersion.targetKeywords"
                        :key="keyword"
                        size="small"
                      >
                        {{ keyword }}
                      </n-tag>
                    </n-space>
                    <n-empty v-else description="暂无目标关键词" />
                  </n-thing>

                  <n-thing title="合规警告">
                    <n-space v-if="selectedVersion.complianceWarnings.length">
                      <n-tag
                        v-for="warning in selectedVersion.complianceWarnings"
                        :key="warning"
                        type="warning"
                        size="small"
                      >
                        {{ warning }}
                      </n-tag>
                    </n-space>
                    <n-empty v-else description="暂无合规警告" />
                  </n-thing>
                </n-space>
              </n-card>
            </n-gi>
          </n-grid>
        </n-space>
      </n-spin>
    </n-space>
  </main>
</template>
