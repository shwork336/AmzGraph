<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import {
  generateImageVersion,
  getTaskDetail,
  listImageAssets,
  listImageVersions
} from '../api/client';
import type {
  ImageAssetResponse,
  ImageVersionResponse,
  ListingTaskDetailResponse
} from '../api/types';
import { displayTime, displayValue, errorMessage } from '../utils/display';
import { statusTagType } from '../utils/status';
import { taskDetailPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const assetsLoading = ref(false);
const generating = ref(false);
const errorText = ref('');
const task = ref<ListingTaskDetailResponse | null>(null);
const versions = ref<ImageVersionResponse[]>([]);
const assets = ref<ImageAssetResponse[]>([]);
const selectedVersionId = ref('');
const selectedAssetId = ref('');

const currentTaskId = computed(() => String(route.params.taskId ?? ''));
const selectedVersion = computed(() =>
  versions.value.find((version) => version.versionId === selectedVersionId.value) ?? versions.value[0] ?? null
);
const selectedAsset = computed(() =>
  assets.value.find((asset) => asset.assetId === selectedAssetId.value) ?? assets.value[0] ?? null
);

function imageSize(asset: ImageAssetResponse) {
  if (!asset.targetWidth || !asset.targetHeight) {
    return '-';
  }
  return `${asset.targetWidth} x ${asset.targetHeight}`;
}

async function loadAssets(imageVersionId: string) {
  if (!imageVersionId) {
    assets.value = [];
    selectedAssetId.value = '';
    return;
  }
  assetsLoading.value = true;
  try {
    assets.value = await listImageAssets(currentTaskId.value, imageVersionId);
    selectedAssetId.value = assets.value[0]?.assetId ?? '';
  } catch (error) {
    message.error(errorMessage(error, '图片资产加载失败'));
  } finally {
    assetsLoading.value = false;
  }
}

async function loadPage() {
  loading.value = true;
  errorText.value = '';
  try {
    const [taskDetail, imageVersions] = await Promise.all([
      getTaskDetail(currentTaskId.value),
      listImageVersions(currentTaskId.value)
    ]);
    task.value = taskDetail;
    versions.value = imageVersions;
    selectedVersionId.value = imageVersions[0]?.versionId ?? '';
    await loadAssets(selectedVersionId.value);
  } catch (error) {
    errorText.value = errorMessage(error, '图片版本加载失败');
  } finally {
    loading.value = false;
  }
}

async function generateImages() {
  generating.value = true;
  try {
    const generated = await generateImageVersion(currentTaskId.value);
    message.success('已生成图片版本');
    await loadPage();
    selectedVersionId.value = generated.versionId;
    await loadAssets(generated.versionId);
  } catch (error) {
    message.error(errorMessage(error, '生成失败'));
  } finally {
    generating.value = false;
  }
}

async function selectVersion(row: ImageVersionResponse) {
  selectedVersionId.value = row.versionId;
  await loadAssets(row.versionId);
}

const versionColumns: DataTableColumns<ImageVersionResponse> = [
  { title: '版本 ID', key: 'versionId', width: 190, ellipsis: { tooltip: true } },
  { title: 'Brief', key: 'briefVersionId', width: 190, ellipsis: { tooltip: true },
    render(row) { return row.briefVersionId ?? '-'; } },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render(row) {
      return h(NTag, { type: statusTagType(row.status), size: 'small' }, { default: () => row.status });
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
        { size: 'small', type: selectedVersionId.value === row.versionId ? 'primary' : 'default', onClick: () => selectVersion(row) },
        { default: () => '查看' }
      );
    }
  }
];

const assetColumns: DataTableColumns<ImageAssetResponse> = [
  { title: '排序', key: 'sortOrder', width: 70, render(row) { return row.sortOrder ?? '-'; } },
  { title: '类型', key: 'type', width: 170 },
  { title: '尺寸', key: 'size', width: 120, render(row) { return imageSize(row); } },
  {
    title: '合规',
    key: 'complianceStatus',
    width: 110,
    render(row) {
      return h(
        NTag,
        { type: statusTagType(row.complianceStatus), size: 'small' },
        { default: () => row.complianceStatus ?? '-' }
      );
    }
  },
  { title: '生成 URL', key: 'generatedImageUrl', ellipsis: { tooltip: true },
    render(row) { return row.generatedImageUrl ?? '-'; } },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render(row) {
      return h(
        NButton,
        { size: 'small', type: selectedAssetId.value === row.assetId ? 'primary' : 'default', onClick: () => { selectedAssetId.value = row.assetId; } },
        { default: () => '详情' }
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
            <n-button type="success" :loading="generating" @click="generateImages">生成图片</n-button>
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
                  <span>图片</span>
                  <n-tag :type="statusTagType(task?.imageStatus)" size="small">{{ task?.imageStatus ?? '-' }}</n-tag>
                </div>
              </n-gi>
              <n-gi>
                <div class="status-tile">
                  <span>资产数</span>
                  <strong>{{ assets.length }}</strong>
                </div>
              </n-gi>
            </n-grid>
          </n-card>

          <n-card title="图片版本历史">
            <n-data-table
              :columns="versionColumns"
              :data="versions"
              :loading="loading"
              :bordered="false"
            />
          </n-card>

          <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
            <n-gi>
              <n-card title="图片资产">
                <n-data-table
                  :columns="assetColumns"
                  :data="assets"
                  :loading="assetsLoading"
                  :bordered="false"
                />
              </n-card>
            </n-gi>

            <n-gi>
              <n-card title="资产详情">
                <n-empty v-if="!selectedAsset" description="暂无图片资产" />
                <n-space v-else vertical size="large">
                  <n-descriptions :column="1" bordered label-placement="left">
                    <n-descriptions-item label="资产 ID">
                      <n-text class="mono">{{ selectedAsset.assetId }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="图片版本">
                      <n-text class="mono">{{ selectedAsset.imageVersionId }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="类型">{{ selectedAsset.type }}</n-descriptions-item>
                    <n-descriptions-item label="尺寸档位">{{ displayValue(selectedAsset.sizeProfile) }}</n-descriptions-item>
                    <n-descriptions-item label="目标尺寸">{{ imageSize(selectedAsset) }}</n-descriptions-item>
                    <n-descriptions-item label="合规状态">
                      <n-tag :type="statusTagType(selectedAsset.complianceStatus)" size="small">
                        {{ selectedAsset.complianceStatus ?? '-' }}
                      </n-tag>
                    </n-descriptions-item>
                    <n-descriptions-item label="生成 URL">
                      <n-text class="mono">{{ displayValue(selectedAsset.generatedImageUrl) }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="源文件 URL">
                      <n-text class="mono">{{ displayValue(selectedAsset.sourceEditableFileUrl) }}</n-text>
                    </n-descriptions-item>
                    <n-descriptions-item label="创建时间">
                      {{ displayTime(selectedAsset.createdAt) }}
                    </n-descriptions-item>
                  </n-descriptions>

                  <n-thing title="原始 Prompt">
                    {{ displayValue(selectedAsset.prompt) }}
                  </n-thing>

                  <n-thing title="改写 Prompt">
                    {{ displayValue(selectedAsset.rewrittenPrompt) }}
                  </n-thing>

                  <n-thing title="合规方法">
                    <n-space v-if="selectedAsset.complianceMethods.length">
                      <n-tag
                        v-for="method in selectedAsset.complianceMethods"
                        :key="method"
                        size="small"
                      >
                        {{ method }}
                      </n-tag>
                    </n-space>
                    <n-empty v-else description="暂无合规方法" />
                  </n-thing>

                  <n-thing title="合规问题">
                    <n-space v-if="selectedAsset.complianceIssues.length">
                      <n-tag
                        v-for="issue in selectedAsset.complianceIssues"
                        :key="issue"
                        type="warning"
                        size="small"
                      >
                        {{ issue }}
                      </n-tag>
                    </n-space>
                    <n-empty v-else description="暂无合规问题" />
                  </n-thing>
                </n-space>
              </n-card>
            </n-gi>
          </n-grid>

          <n-card v-if="selectedVersion" title="当前图片版本参数">
            <n-descriptions :column="2" bordered label-placement="left">
              <n-descriptions-item label="版本 ID">
                <n-text class="mono">{{ selectedVersion.versionId }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="父版本">
                <n-text class="mono">{{ selectedVersion.parentVersionId ?? '-' }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="供应商">{{ displayValue(selectedVersion.imageProvider) }}</n-descriptions-item>
              <n-descriptions-item label="模型">{{ displayValue(selectedVersion.imageModel) }}</n-descriptions-item>
              <n-descriptions-item label="参考图">{{ displayValue(selectedVersion.referenceImageUrl) }}</n-descriptions-item>
              <n-descriptions-item label="生成参数">
                <n-text class="mono">{{ displayValue(selectedVersion.generationParams) }}</n-text>
              </n-descriptions-item>
            </n-descriptions>
          </n-card>
        </n-space>
      </n-spin>
    </n-space>
  </main>
</template>
