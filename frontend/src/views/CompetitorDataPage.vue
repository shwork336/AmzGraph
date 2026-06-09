<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, useMessage } from 'naive-ui';
import {
  getTaskDetail,
  listCompetitors,
  listLatestCompetitors,
  submitManualCompetitors
} from '../api/client';
import type { CompetitorSnapshotResponse, ListingTaskDetailResponse } from '../api/types';
import { displayTime, errorMessage } from '../utils/display';
import { loadOperatorId, saveOperatorId } from '../utils/operator';
import { taskDetailPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const submitting = ref(false);
const errorText = ref('');
const task = ref<ListingTaskDetailResponse | null>(null);
const latestSnapshots = ref<CompetitorSnapshotResponse[]>([]);
const historySnapshots = ref<CompetitorSnapshotResponse[]>([]);
const selectedSnapshotId = ref('');
const operatorId = ref(loadOperatorId());

const form = reactive({
  asin: '',
  title: '',
  bulletPoints: '',
  rating: null as number | null,
  reviewCount: null as number | null,
  reviewPainPoints: '',
  keywordSignals: '',
  sourceName: 'MANUAL'
});

const currentTaskId = computed(() => String(route.params.taskId ?? ''));
const selectedSnapshot = computed(() =>
  [...latestSnapshots.value, ...historySnapshots.value]
    .find((snapshot) => snapshot.snapshotId === selectedSnapshotId.value) ?? latestSnapshots.value[0] ?? historySnapshots.value[0] ?? null
);

function displayNumber(value?: number | null) {
  return value === null || value === undefined ? '-' : String(value);
}

function toLines(value: string) {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
}

async function loadPage() {
  loading.value = true;
  errorText.value = '';
  try {
    const [taskDetail, latest, history] = await Promise.all([
      getTaskDetail(currentTaskId.value),
      listLatestCompetitors(currentTaskId.value),
      listCompetitors(currentTaskId.value)
    ]);
    task.value = taskDetail;
    latestSnapshots.value = latest;
    historySnapshots.value = history;
    selectedSnapshotId.value = latest[0]?.snapshotId ?? history[0]?.snapshotId ?? '';
  } catch (error) {
    errorText.value = errorMessage(error, '竞品数据加载失败');
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.asin = '';
  form.title = '';
  form.bulletPoints = '';
  form.rating = null;
  form.reviewCount = null;
  form.reviewPainPoints = '';
  form.keywordSignals = '';
  form.sourceName = 'MANUAL';
}

async function submitManualSnapshot() {
  if (!operatorId.value.trim()) {
    message.warning('需要填写操作人');
    return;
  }
  if (!form.asin.trim() || !form.title.trim()) {
    message.warning('需要填写 ASIN 和标题');
    return;
  }
  submitting.value = true;
  const savedOperatorId = saveOperatorId(operatorId.value);
  try {
    const created = await submitManualCompetitors(currentTaskId.value, savedOperatorId, [
      {
        asin: form.asin.trim(),
        title: form.title.trim(),
        bulletPoints: toLines(form.bulletPoints),
        rating: form.rating,
        reviewCount: form.reviewCount,
        reviewPainPoints: toLines(form.reviewPainPoints),
        keywordSignals: toLines(form.keywordSignals),
        sourceName: form.sourceName.trim() || 'MANUAL'
      }
    ]);
    message.success('竞品快照已补录');
    resetForm();
    await loadPage();
    selectedSnapshotId.value = created[0]?.snapshotId ?? selectedSnapshotId.value;
  } catch (error) {
    message.error(errorMessage(error, '补录失败'));
  } finally {
    submitting.value = false;
  }
}

const snapshotColumns: DataTableColumns<CompetitorSnapshotResponse> = [
  { title: 'ASIN', key: 'asin', width: 130 },
  { title: '标题', key: 'title', ellipsis: { tooltip: true } },
  { title: '评分', key: 'rating', width: 80, render(row) { return displayNumber(row.rating); } },
  { title: '评论数', key: 'reviewCount', width: 100, render(row) { return displayNumber(row.reviewCount); } },
  { title: '来源', key: 'sourceName', width: 130 },
  { title: '采集时间', key: 'capturedAt', width: 170, render(row) { return displayTime(row.capturedAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render(row) {
      return h(
        NButton,
        { size: 'small', type: selectedSnapshotId.value === row.snapshotId ? 'primary' : 'default', onClick: () => { selectedSnapshotId.value = row.snapshotId; } },
        { default: () => '详情' }
      );
    }
  }
];

onMounted(loadPage);

watch(
  () => route.params.taskId,
  () => {
    loadPage();
  }
);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card>
        <n-space vertical>
          <div class="toolbar">
            <n-button @click="router.push(taskDetailPath(currentTaskId))">返回详情</n-button>
            <n-button type="primary" :loading="loading" @click="loadPage">刷新</n-button>
            <n-input-group class="toolbar-field">
              <n-input-group-label>Operator</n-input-group-label>
              <n-input v-model:value="operatorId" />
            </n-input-group>
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
                  <strong>{{ task?.status ?? '-' }}</strong>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>站点</span>
                  <strong>{{ task?.marketplace ?? '-' }}</strong>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>竞品 ASIN</span>
                  <strong>{{ task?.competitorAsins?.length ?? 0 }}</strong>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>快照数</span>
                  <strong>{{ historySnapshots.length }}</strong>
                </div>
              </n-gi>
            </n-grid>
          </n-card>

          <n-card title="手工补录">
            <n-form label-placement="top">
              <n-grid :cols="2" :x-gap="16" :y-gap="8" responsive="screen">
                <n-gi>
                  <n-form-item label="ASIN">
                    <n-input v-model:value="form.asin" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="来源名称">
                    <n-input v-model:value="form.sourceName" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="标题">
                    <n-input v-model:value="form.title" type="textarea" :autosize="{ minRows: 2 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="Bullet Points">
                    <n-input v-model:value="form.bulletPoints" type="textarea" :autosize="{ minRows: 2 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="评分">
                    <n-input-number v-model:value="form.rating" :min="0" :max="5" :step="0.1" clearable />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="评论数">
                    <n-input-number v-model:value="form.reviewCount" :min="0" :step="1" clearable />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="评论痛点">
                    <n-input v-model:value="form.reviewPainPoints" type="textarea" :autosize="{ minRows: 2 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="关键词信号">
                    <n-input v-model:value="form.keywordSignals" type="textarea" :autosize="{ minRows: 2 }" />
                  </n-form-item>
                </n-gi>
              </n-grid>
              <n-space justify="end">
                <n-button @click="resetForm">清空</n-button>
                <n-button type="primary" :loading="submitting" @click="submitManualSnapshot">提交补录</n-button>
              </n-space>
            </n-form>
          </n-card>

          <n-card title="每个 ASIN 最新快照">
            <n-data-table
              :columns="snapshotColumns"
              :data="latestSnapshots"
              :loading="loading"
              :bordered="false"
            />
          </n-card>

          <n-card title="全部历史">
            <n-data-table
              :columns="snapshotColumns"
              :data="historySnapshots"
              :loading="loading"
              :bordered="false"
            />
          </n-card>

          <n-card title="快照详情">
            <n-empty v-if="!selectedSnapshot" description="暂无竞品快照" />
            <n-space v-else vertical size="large">
              <n-descriptions :column="2" bordered label-placement="left">
                <n-descriptions-item label="快照 ID">
                  <n-text class="mono">{{ selectedSnapshot.snapshotId }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="ASIN">{{ selectedSnapshot.asin }}</n-descriptions-item>
                <n-descriptions-item label="评分">{{ displayNumber(selectedSnapshot.rating) }}</n-descriptions-item>
                <n-descriptions-item label="评论数">{{ displayNumber(selectedSnapshot.reviewCount) }}</n-descriptions-item>
                <n-descriptions-item label="来源类型">{{ selectedSnapshot.sourceType }}</n-descriptions-item>
                <n-descriptions-item label="来源名称">{{ selectedSnapshot.sourceName }}</n-descriptions-item>
                <n-descriptions-item label="录入人">{{ selectedSnapshot.createdBy }}</n-descriptions-item>
                <n-descriptions-item label="采集时间">{{ displayTime(selectedSnapshot.capturedAt) }}</n-descriptions-item>
              </n-descriptions>

              <n-thing title="标题">
                {{ selectedSnapshot.title }}
              </n-thing>

              <n-thing title="Bullet Points">
                <n-empty v-if="!selectedSnapshot.bulletPoints.length" description="暂无 Bullet" />
                <ul v-else class="content-list">
                  <li v-for="point in selectedSnapshot.bulletPoints" :key="point">{{ point }}</li>
                </ul>
              </n-thing>

              <n-thing title="评论痛点">
                <n-space v-if="selectedSnapshot.reviewPainPoints.length">
                  <n-tag v-for="point in selectedSnapshot.reviewPainPoints" :key="point" type="warning" size="small">
                    {{ point }}
                  </n-tag>
                </n-space>
                <n-empty v-else description="暂无评论痛点" />
              </n-thing>

              <n-thing title="关键词信号">
                <n-space v-if="selectedSnapshot.keywordSignals.length">
                  <n-tag v-for="keyword in selectedSnapshot.keywordSignals" :key="keyword" size="small">
                    {{ keyword }}
                  </n-tag>
                </n-space>
                <n-empty v-else description="暂无关键词信号" />
              </n-thing>
            </n-space>
          </n-card>
        </n-space>
      </n-spin>
    </n-space>
  </main>
</template>
