<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import type { DataTableColumns } from 'naive-ui';
import { NButton, NTag, useMessage } from 'naive-ui';
import {
  approveBriefVersion,
  createBriefVersion,
  getLatestBrief,
  listBriefVersions
} from '../api/client';
import type { BriefVersionResponse } from '../api/types';
import { displayTime, errorMessage } from '../utils/display';
import { loadOperatorId, saveOperatorId } from '../utils/operator';
import { taskDetailPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();
const message = useMessage();

const loading = ref(false);
const saving = ref(false);
const approving = ref(false);
const errorText = ref('');
const latestBrief = ref<BriefVersionResponse | null>(null);
const briefVersions = ref<BriefVersionResponse[]>([]);

const operatorId = ref(loadOperatorId());

const form = reactive({
  targetAudience: '',
  coreSellingPoints: '',
  targetKeywords: '',
  forbiddenClaims: '',
  imageDirectionPrompts: '',
  complianceNotes: ''
});

const currentTaskId = computed(() => String(route.params.taskId ?? ''));

function toLines(values?: string[]) {
  return values?.join('\n') ?? '';
}

function fromLines(value: string) {
  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
}

function hydrateForm(brief: BriefVersionResponse) {
  form.targetAudience = brief.targetAudience;
  form.coreSellingPoints = toLines(brief.coreSellingPoints);
  form.targetKeywords = toLines(brief.targetKeywords);
  form.forbiddenClaims = toLines(brief.forbiddenClaims);
  form.imageDirectionPrompts = toLines(brief.imageDirectionPrompts);
  form.complianceNotes = toLines(brief.complianceNotes);
}

async function loadBriefs() {
  loading.value = true;
  errorText.value = '';
  try {
    const [latest, versions] = await Promise.all([
      getLatestBrief(currentTaskId.value),
      listBriefVersions(currentTaskId.value)
    ]);
    latestBrief.value = latest;
    briefVersions.value = versions;
    hydrateForm(latest);
  } catch (error) {
    errorText.value = errorMessage(error, 'Brief 加载失败');
  } finally {
    loading.value = false;
  }
}

async function saveVersion() {
  if (!latestBrief.value) {
    message.warning('暂无可编辑的最新 Brief');
    return;
  }
  if (!operatorId.value.trim()) {
    message.warning('需要填写操作人');
    return;
  }
  if (!form.targetAudience.trim()) {
    message.warning('需要填写目标受众');
    return;
  }

  saving.value = true;
  const savedOperatorId = saveOperatorId(operatorId.value);
  try {
    await createBriefVersion(currentTaskId.value, {
      baseBriefVersionId: latestBrief.value.briefVersionId,
      createdBy: savedOperatorId,
      targetAudience: form.targetAudience.trim(),
      coreSellingPoints: fromLines(form.coreSellingPoints),
      targetKeywords: fromLines(form.targetKeywords),
      forbiddenClaims: fromLines(form.forbiddenClaims),
      imageDirectionPrompts: fromLines(form.imageDirectionPrompts),
      complianceNotes: fromLines(form.complianceNotes)
    });
    message.success('已保存新的 Brief 版本');
    await loadBriefs();
  } catch (error) {
    message.error(errorMessage(error, '保存失败'));
  } finally {
    saving.value = false;
  }
}

async function approveLatest() {
  if (!latestBrief.value) {
    message.warning('暂无可批准的最新 Brief');
    return;
  }
  if (!operatorId.value.trim()) {
    message.warning('需要填写审批人');
    return;
  }

  approving.value = true;
  const savedOperatorId = saveOperatorId(operatorId.value);
  try {
    await approveBriefVersion(
      currentTaskId.value,
      latestBrief.value.briefVersionId,
      savedOperatorId
    );
    message.success('Brief 已批准');
    await loadBriefs();
  } catch (error) {
    message.error(errorMessage(error, '批准失败'));
  } finally {
    approving.value = false;
  }
}

function loadVersionIntoForm(row: BriefVersionResponse) {
  hydrateForm(row);
  message.info(`已载入 ${row.briefVersionId}`);
}

const columns: DataTableColumns<BriefVersionResponse> = [
  { title: 'Brief 版本', key: 'briefVersionId', width: 190, ellipsis: { tooltip: true } },
  { title: '父版本', key: 'parentBriefVersionId', width: 190, ellipsis: { tooltip: true },
    render(row) { return row.parentBriefVersionId ?? '-'; } },
  { title: '创建人', key: 'createdBy', width: 180 },
  {
    title: '状态',
    key: 'approved',
    width: 110,
    render(row) {
      return h(
        NTag,
        { type: row.approved ? 'success' : 'warning', size: 'small' },
        { default: () => (row.approved ? '已批准' : '待批准') }
      );
    }
  },
  { title: '批准人', key: 'approvedBy', width: 180, render(row) { return row.approvedBy ?? '-'; } },
  { title: '创建时间', key: 'createdAt', width: 170, render(row) { return displayTime(row.createdAt); } },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render(row) {
      return h(NButton, { size: 'small', onClick: () => loadVersionIntoForm(row) }, { default: () => '载入编辑' });
    }
  }
];

watch(
  () => route.params.taskId,
  () => {
    loadBriefs();
  }
);

onMounted(loadBriefs);
</script>

<template>
  <main class="page">
    <n-space vertical size="large">
      <n-card>
        <n-space vertical>
          <div class="toolbar">
            <n-button @click="router.push(taskDetailPath(currentTaskId))">返回详情</n-button>
            <n-button type="primary" :loading="loading" @click="loadBriefs">刷新</n-button>
            <n-input-group class="toolbar-field">
              <n-input-group-label>Operator</n-input-group-label>
              <n-input v-model:value="operatorId" />
            </n-input-group>
            <n-button type="success" :loading="approving" @click="approveLatest">批准最新 Brief</n-button>
          </div>
          <n-alert v-if="errorText" type="error">{{ errorText }}</n-alert>
        </n-space>
      </n-card>

      <n-spin :show="loading">
        <n-space vertical size="large">
          <n-card title="最新 Brief">
            <n-empty v-if="!latestBrief" description="暂无 Brief" />
            <n-descriptions v-else :column="2" bordered label-placement="left">
              <n-descriptions-item label="Brief 版本">
                <n-text class="mono">{{ latestBrief.briefVersionId }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="父版本">
                <n-text class="mono">{{ latestBrief.parentBriefVersionId ?? '-' }}</n-text>
              </n-descriptions-item>
              <n-descriptions-item label="目标受众">{{ latestBrief.targetAudience }}</n-descriptions-item>
              <n-descriptions-item label="状态">
                <n-tag :type="latestBrief.approved ? 'success' : 'warning'" size="small">
                  {{ latestBrief.approved ? '已批准' : '待批准' }}
                </n-tag>
              </n-descriptions-item>
              <n-descriptions-item label="创建人">{{ latestBrief.createdBy }}</n-descriptions-item>
              <n-descriptions-item label="批准人">{{ latestBrief.approvedBy ?? '-' }}</n-descriptions-item>
              <n-descriptions-item label="创建时间">{{ displayTime(latestBrief.createdAt) }}</n-descriptions-item>
              <n-descriptions-item label="批准时间">{{ displayTime(latestBrief.approvedAt) }}</n-descriptions-item>
            </n-descriptions>
          </n-card>

          <n-card title="编辑 Brief">
            <n-form label-placement="top">
              <n-grid :cols="2" :x-gap="16" :y-gap="8" responsive="screen">
                <n-gi>
                  <n-form-item label="目标受众">
                    <n-input v-model:value="form.targetAudience" type="textarea" :autosize="{ minRows: 3 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="核心卖点">
                    <n-input v-model:value="form.coreSellingPoints" type="textarea" :autosize="{ minRows: 3 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="目标关键词">
                    <n-input v-model:value="form.targetKeywords" type="textarea" :autosize="{ minRows: 3 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="禁用宣称">
                    <n-input v-model:value="form.forbiddenClaims" type="textarea" :autosize="{ minRows: 3 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="图片方向提示">
                    <n-input v-model:value="form.imageDirectionPrompts" type="textarea" :autosize="{ minRows: 3 }" />
                  </n-form-item>
                </n-gi>
                <n-gi>
                  <n-form-item label="合规备注">
                    <n-input v-model:value="form.complianceNotes" type="textarea" :autosize="{ minRows: 3 }" />
                  </n-form-item>
                </n-gi>
              </n-grid>
              <n-space justify="end">
                <n-button @click="latestBrief && hydrateForm(latestBrief)">重置为最新</n-button>
                <n-button type="primary" :loading="saving" @click="saveVersion">保存为新版本</n-button>
              </n-space>
            </n-form>
          </n-card>

          <n-card title="Brief 历史">
            <n-data-table
              :columns="columns"
              :data="briefVersions"
              :loading="loading"
              :bordered="false"
            />
          </n-card>
        </n-space>
      </n-spin>
    </n-space>
  </main>
</template>
