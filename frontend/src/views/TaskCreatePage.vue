<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { UploadFileInfo } from 'naive-ui';
import { useMessage } from 'naive-ui';
import { submitListingTask } from '../api/client';
import { errorMessage } from '../utils/display';
import { taskListPath, taskSubPath } from '../utils/taskRoutes';

const router = useRouter();
const message = useMessage();
const submitting = ref(false);
const documentFiles = ref<UploadFileInfo[]>([]);
const imageFiles = ref<UploadFileInfo[]>([]);
const asinText = ref('');
const marketplace = ref('US');
const language = ref('en-US');

const asinList = computed(() =>
  asinText.value
    .split(/[\n,，\s]+/)
    .map((value) => value.trim())
    .filter(Boolean)
);

function fileFromUpload(file: UploadFileInfo): File | null {
  return file.file ?? null;
}

function validateForm(): { documentFile: File; productImages: File[] } | null {
  const documentFile = documentFiles.value[0] ? fileFromUpload(documentFiles.value[0]) : null;
  if (!documentFile) {
    message.warning('需要上传 Markdown 产品资料');
    return null;
  }
  if (!documentFile.name.toLowerCase().endsWith('.md')) {
    message.warning('产品资料必须是 .md 文件');
    return null;
  }

  const productImages = imageFiles.value
    .map(fileFromUpload)
    .filter((file): file is File => file !== null);
  if (productImages.length < 1 || productImages.length > 4) {
    message.warning('产品图数量必须为 1-4 张');
    return null;
  }
  if (productImages.some((file) => !file.type.startsWith('image/'))) {
    message.warning('产品图必须是图片文件');
    return null;
  }
  return { documentFile, productImages };
}

async function submitTask() {
  const files = validateForm();
  if (!files) {
    return;
  }
  submitting.value = true;
  try {
    const response = await submitListingTask({
      file: files.documentFile,
      productImages: files.productImages,
      asins: asinList.value,
      marketplace: marketplace.value || 'US',
      language: language.value || 'en-US'
    });
    message.success('任务已创建');
    router.push(taskSubPath(response.taskId, 'export'));
  } catch (error) {
    message.error(errorMessage(error, '创建任务失败'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="page">
    <n-card title="创建 Listing 任务">
      <n-space vertical size="large">
        <n-alert type="info">
          产品资料使用 Markdown 文件，产品图数量限制为 1-4 张。类目模板由后端默认使用 CAR_STEREO。
        </n-alert>

        <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
          <n-gi>
            <n-form-item label="Marketplace">
              <n-input v-model:value="marketplace" placeholder="US" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="Language">
              <n-input v-model:value="language" placeholder="en-US" />
            </n-form-item>
          </n-gi>
        </n-grid>

        <n-form-item label="产品资料 Markdown">
          <n-upload
            v-model:file-list="documentFiles"
            :max="1"
            accept=".md,text/markdown,text/plain"
          >
            <n-button>选择 Markdown 文件</n-button>
          </n-upload>
        </n-form-item>

        <n-form-item label="产品图">
          <n-upload
            v-model:file-list="imageFiles"
            multiple
            :max="4"
            accept="image/*"
            list-type="image-card"
          />
        </n-form-item>

        <n-form-item label="竞品 ASIN">
          <n-input
            v-model:value="asinText"
            type="textarea"
            placeholder="每行一个 ASIN，或用逗号分隔"
            :autosize="{ minRows: 4, maxRows: 8 }"
          />
        </n-form-item>

        <n-space justify="end">
          <n-button @click="router.push(taskListPath())">返回列表</n-button>
          <n-button type="primary" :loading="submitting" @click="submitTask">提交任务</n-button>
        </n-space>
      </n-space>
    </n-card>
  </main>
</template>
