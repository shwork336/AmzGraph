<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import {
  approveImageAssetCompliance,
  confirmWarningImageAssetCompliance,
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
import { loadOperatorId, saveOperatorId } from '../utils/operator';
import { statusTagType } from '../utils/status';
import { taskDetailPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const assetsLoading = ref(false);
const submitting = ref(false);
const errorText = ref('');
const task = ref<ListingTaskDetailResponse | null>(null);
const versions = ref<ImageVersionResponse[]>([]);
const assets = ref<ImageAssetResponse[]>([]);
const selectedVersionId = ref('');
const selectedAssetId = ref('');
const modalVisible = ref(false);
const reviewMode = ref<'FAIL_APPROVE' | 'WARNING_CONFIRM'>('WARNING_CONFIRM');
const reviewReason = ref('');
const operatorId = ref(loadOperatorId());

const currentTaskId = computed(() => String(route.params.taskId ?? ''));
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
    message.error(errorMessage(error, '合规资产加载失败'));
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
    selectedVersionId.value = taskDetail.selectedImageVersionId ?? imageVersions[0]?.versionId ?? '';
    await loadAssets(selectedVersionId.value);
  } catch (error) {
    errorText.value = errorMessage(error, '合规报告加载失败');
  } finally {
    loading.value = false;
  }
}

async function selectVersion(row: ImageVersionResponse) {
  selectedVersionId.value = row.versionId;
  await loadAssets(row.versionId);
}

function openReviewModal(mode: 'FAIL_APPROVE' | 'WARNING_CONFIRM', asset: ImageAssetResponse) {
  selectedAssetId.value = asset.assetId;
  reviewMode.value = mode;
  reviewReason.value = '';
  modalVisible.value = true;
}

async function submitReview() {
  if (!selectedAsset.value) {
    message.warning('需要选择图片资产');
    return;
  }
  if (!operatorId.value.trim() || !reviewReason.value.trim()) {
    message.warning('需要填写操作人和原因');
    return;
  }

  submitting.value = true;
  const savedOperatorId = saveOperatorId(operatorId.value);
  try {
    if (reviewMode.value === 'FAIL_APPROVE') {
      await approveImageAssetCompliance(
        currentTaskId.value,
        selectedAsset.value.imageVersionId,
        selectedAsset.value.assetId,
        savedOperatorId,
        reviewReason.value.trim()
      );
      message.success('FAIL 资产已记录管理员豁免');
    } else {
      await confirmWarningImageAssetCompliance(
        currentTaskId.value,
        selectedAsset.value.imageVersionId,
        selectedAsset.value.assetId,
        savedOperatorId,
        reviewReason.value.trim()
      );
      message.success('WARNING 资产已记录人工确认');
    }
    modalVisible.value = false;
    await loadAssets(selectedVersionId.value);
  } catch (error) {
    message.error(errorMessage(error, '提交失败'));
  } finally {
    submitting.value = false;
  }
}

const versionColumns: DataTableColumns<ImageVersionResponse> = [
  { title: '图片版本', key: 'versionId', width: 190, ellipsis: { tooltip: true } },
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
  { title: '尺寸', key: 'size', width: 120, render(row) { return imageSize(row); } },
  { title: '确认/豁免人', key: 'complianceReviewedBy', width: 170, render(row) { return row.complianceReviewedBy ?? '-'; } },
  { title: '原因', key: 'complianceReviewReason', ellipsis: { tooltip: true }, render(row) { return row.complianceReviewReason ?? '-'; } },
  {
    title: '操作',
    key: 'actions',
    width: 210,
    render(row) {
      return h('div', { class: 'table-actions' }, [
        h(NButton, { size: 'small', onClick: () => { selectedAssetId.value = row.assetId; } }, { default: () => '详情' }),
        row.complianceStatus === 'FAIL'
          ? h(NButton, { size: 'small', type: 'warning', onClick: () => openReviewModal('FAIL_APPROVE', row) }, { default: () => '豁免' })
          : null,
        row.complianceStatus === 'WARNING'
          ? h(NButton, { size: 'small', type: 'primary', onClick: () => openReviewModal('WARNING_CONFIRM', row) }, { default: () => '确认' })
          : null
      ]);
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
                  <n-tag :type="statusTagType(task?.status)" size="small">{{ task?.status ?? '-' }}</n-tag>
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
                  <span>最终图片版本</span>
                  <strong>{{ task?.selectedImageVersionId ?? '-' }}</strong>
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

          <n-card title="图片版本">
            <n-data-table
              :columns="versionColumns"
              :data="versions"
              :loading="loading"
              :bordered="false"
            />
          </n-card>

          <n-card title="资产合规列表">
            <n-data-table
              :columns="assetColumns"
              :data="assets"
              :loading="assetsLoading"
              :bordered="false"
            />
          </n-card>

          <n-card title="合规详情">
            <n-empty v-if="!selectedAsset" description="暂无图片资产" />
            <n-space v-else vertical size="large">
              <n-descriptions :column="2" bordered label-placement="left">
                <n-descriptions-item label="资产 ID">
                  <n-text class="mono">{{ selectedAsset.assetId }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="图片版本">
                  <n-text class="mono">{{ selectedAsset.imageVersionId }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="类型">{{ selectedAsset.type }}</n-descriptions-item>
                <n-descriptions-item label="尺寸">{{ imageSize(selectedAsset) }}</n-descriptions-item>
                <n-descriptions-item label="合规状态">
                  <n-tag :type="statusTagType(selectedAsset.complianceStatus)" size="small">
                    {{ selectedAsset.complianceStatus ?? '-' }}
                  </n-tag>
                </n-descriptions-item>
                <n-descriptions-item label="确认/豁免时间">
                  {{ displayTime(selectedAsset.complianceReviewedAt) }}
                </n-descriptions-item>
                <n-descriptions-item label="确认/豁免人">
                  {{ selectedAsset.complianceReviewedBy ?? '-' }}
                </n-descriptions-item>
                <n-descriptions-item label="原因">
                  {{ selectedAsset.complianceReviewReason ?? '-' }}
                </n-descriptions-item>
                <n-descriptions-item label="生成 URL">
                  <n-text class="mono">{{ displayValue(selectedAsset.generatedImageUrl) }}</n-text>
                </n-descriptions-item>
                <n-descriptions-item label="源文件 URL">
                  <n-text class="mono">{{ displayValue(selectedAsset.sourceEditableFileUrl) }}</n-text>
                </n-descriptions-item>
              </n-descriptions>

              <n-thing title="合规方法">
                <n-space v-if="selectedAsset.complianceMethods.length">
                  <n-tag v-for="method in selectedAsset.complianceMethods" :key="method" size="small">
                    {{ method }}
                  </n-tag>
                </n-space>
                <n-empty v-else description="暂无合规方法" />
              </n-thing>

              <n-thing title="合规问题">
                <n-space v-if="selectedAsset.complianceIssues.length">
                  <n-tag v-for="issue in selectedAsset.complianceIssues" :key="issue" type="warning" size="small">
                    {{ issue }}
                  </n-tag>
                </n-space>
                <n-empty v-else description="暂无合规问题" />
              </n-thing>

              <n-thing title="Prompt">
                {{ displayValue(selectedAsset.prompt) }}
              </n-thing>
            </n-space>
          </n-card>
        </n-space>
      </n-spin>

      <n-modal
        v-model:show="modalVisible"
        preset="card"
        :title="reviewMode === 'FAIL_APPROVE' ? '管理员合规豁免' : 'WARNING 人工确认'"
        style="width: 560px"
      >
        <n-form label-placement="top">
          <n-form-item label="图片资产">
            <n-text class="mono">{{ selectedAsset?.assetId }}</n-text>
          </n-form-item>
          <n-form-item label="操作人">
            <n-input v-model:value="operatorId" />
          </n-form-item>
          <n-form-item label="原因">
            <n-input v-model:value="reviewReason" type="textarea" :autosize="{ minRows: 3 }" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="modalVisible = false">关闭</n-button>
            <n-button type="primary" :loading="submitting" @click="submitReview">提交</n-button>
          </n-space>
        </template>
      </n-modal>
    </n-space>
  </main>
</template>
