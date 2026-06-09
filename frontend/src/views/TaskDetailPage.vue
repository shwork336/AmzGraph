<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getTaskDetail } from '../api/client';
import type { ListingTaskDetailResponse } from '../api/types';
import { displayTime, displayValue, errorMessage } from '../utils/display';
import { statusTagType } from '../utils/status';
import { taskListPath, taskSubPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const errorText = ref('');
const task = ref<ListingTaskDetailResponse | null>(null);

const currentTaskId = computed(() => String(route.params.taskId ?? ''));

const workflowItems = computed(() => [
  { label: '主流程', value: task.value?.status ?? '-' },
  { label: 'Brief', value: task.value?.briefStatus ?? '-' },
  { label: '文案', value: task.value?.textStatus ?? '-' },
  { label: '图片', value: task.value?.imageStatus ?? '-' }
]);

async function loadTask() {
  loading.value = true;
  errorText.value = '';
  try {
    task.value = await getTaskDetail(currentTaskId.value);
  } catch (error) {
    errorText.value = errorMessage(error, '任务详情加载失败');
  } finally {
    loading.value = false;
  }
}

function openExport() {
  router.push(taskSubPath(currentTaskId.value, 'export'));
}

function openBriefReview() {
  router.push(taskSubPath(currentTaskId.value, 'brief'));
}

function openCompetitors() {
  router.push(taskSubPath(currentTaskId.value, 'competitors'));
}

function openTextVersions() {
  router.push(taskSubPath(currentTaskId.value, 'text'));
}

function openImageVersions() {
  router.push(taskSubPath(currentTaskId.value, 'images'));
}

function openFinalReview() {
  router.push(taskSubPath(currentTaskId.value, 'final'));
}

function openComplianceReport() {
  router.push(taskSubPath(currentTaskId.value, 'compliance'));
}

watch(
  () => route.params.taskId,
  () => {
    loadTask();
  }
);

onMounted(loadTask);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card>
        <n-space vertical>
          <div class="toolbar">
            <n-button @click="router.push(taskListPath())">返回列表</n-button>
            <n-button type="primary" :loading="loading" @click="loadTask">刷新</n-button>
          </div>
          <n-alert v-if="errorText" type="error">{{ errorText }}</n-alert>
        </n-space>
      </n-card>

      <n-spin :show="loading">
        <n-space vertical size="large">
          <n-card title="任务状态">
            <n-space vertical>
              <n-descriptions :column="2" bordered label-placement="left">
                <n-descriptions-item label="任务 ID">
                  <n-text class="mono">{{ currentTaskId }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="主状态">
                  <n-tag :type="statusTagType(task?.status)" size="small">{{ task?.status ?? '-' }}</n-tag>
                </n-descriptions-item>
                <n-descriptions-item label="类目">{{ task?.categoryCode ?? '-' }}</n-descriptions-item>
                <n-descriptions-item label="模板">{{ task?.categoryTemplateId ?? '-' }}</n-descriptions-item>
                <n-descriptions-item label="站点">{{ task?.marketplace ?? '-' }}</n-descriptions-item>
                <n-descriptions-item label="语言">{{ task?.language ?? '-' }}</n-descriptions-item>
                <n-descriptions-item label="创建时间">{{ displayTime(task?.createdAt) }}</n-descriptions-item>
                <n-descriptions-item label="更新时间">{{ displayTime(task?.updatedAt) }}</n-descriptions-item>
              </n-descriptions>

              <n-grid :cols="4" :x-gap="12" :y-gap="12" responsive="screen">
                <n-gi v-for="item in workflowItems" :key="item.label">
                  <div class="status-tile">
                    <span>{{ item.label }}</span>
                    <n-tag :type="statusTagType(item.value)" size="small">{{ item.value }}</n-tag>
                  </div>
                </n-gi>
              </n-grid>
            </n-space>
          </n-card>

          <n-card title="工作流入口">
            <n-grid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
              <n-gi>
                <div class="action-group">
                  <strong>数据准备</strong>
                  <span>补齐竞品输入并确认 Brief。</span>
                  <n-space>
                    <n-button @click="openCompetitors">竞品数据</n-button>
                    <n-button @click="openBriefReview">Brief 审核</n-button>
                  </n-space>
                </div>
              </n-gi>
              <n-gi>
                <div class="action-group">
                  <strong>内容生成</strong>
                  <span>查看图文版本和图片合规状态。</span>
                  <n-space>
                    <n-button @click="openTextVersions">文案版本</n-button>
                    <n-button @click="openImageVersions">图片版本</n-button>
                    <n-button @click="openComplianceReport">合规报告</n-button>
                  </n-space>
                </div>
              </n-gi>
              <n-gi>
                <div class="action-group">
                  <strong>终审交付</strong>
                  <span>确认最终版本并创建交付包。</span>
                  <n-space>
                    <n-button @click="openFinalReview">终审选择</n-button>
                    <n-button type="primary" @click="openExport">归档导出</n-button>
                  </n-space>
                </div>
              </n-gi>
            </n-grid>
          </n-card>

          <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
            <n-gi>
              <n-card title="输入信息">
                <n-space vertical>
                  <n-thing title="原始产品图">
                    <template v-if="task?.originalProductUrls?.length">
                      <n-space>
                        <n-tag
                          v-for="fileKey in task.originalProductUrls"
                          :key="fileKey"
                          class="mono"
                          size="small"
                        >
                          {{ fileKey }}
                        </n-tag>
                      </n-space>
                    </template>
                    <n-empty v-else description="暂无产品图文件键" />
                  </n-thing>
                  <n-divider />
                  <n-thing title="竞品 ASIN">
                    <template v-if="task?.competitorAsins?.length">
                      <n-space>
                        <n-tag
                          v-for="asin in task.competitorAsins"
                          :key="asin"
                          class="mono"
                          size="small"
                        >
                          {{ asin }}
                        </n-tag>
                      </n-space>
                    </template>
                    <n-empty v-else description="暂无竞品 ASIN" />
                  </n-thing>
                </n-space>
              </n-card>
            </n-gi>

            <n-gi>
              <n-card title="最新 Brief 摘要">
                <n-empty v-if="!task?.latestBrief" description="暂无 Brief" />
                <n-descriptions v-else :column="1" bordered label-placement="left">
                  <n-descriptions-item label="Brief 版本">
                    <n-text class="mono">{{ task.latestBrief.briefVersionId }}</n-text>
                  </n-descriptions-item>
                  <n-descriptions-item label="目标受众">
                    {{ displayValue(task.latestBrief.targetAudience) }}
                  </n-descriptions-item>
                  <n-descriptions-item label="审核结果">
                    <n-tag :type="task.latestBrief.approved ? 'success' : 'warning'" size="small">
                      {{ task.latestBrief.approved ? '已批准' : '待批准' }}
                    </n-tag>
                  </n-descriptions-item>
                </n-descriptions>
              </n-card>
            </n-gi>
          </n-grid>

          <n-card title="最终版本">
            <n-descriptions :column="2" bordered label-placement="left">
              <n-descriptions-item label="最终文案版本">
                <n-text class="mono">{{ displayValue(task?.selectedTextVersionId) }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="最终图片版本">
                <n-text class="mono">{{ displayValue(task?.selectedImageVersionId) }}</n-text>
              </n-descriptions-item>
            </n-descriptions>
          </n-card>
        </n-space>
      </n-spin>
    </n-space>
  </main>
</template>
