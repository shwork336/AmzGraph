<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { taskCreatePath, taskListPath } from '../utils/taskRoutes';

const route = useRoute();
const router = useRouter();

const currentPath = computed(() => route.fullPath);
</script>

<template>
  <main class="page">
    <n-card title="页面未找到">
      <n-space vertical size="large">
        <n-alert type="warning">
          当前路径没有对应的工作台页面。
        </n-alert>

        <n-descriptions :column="1" bordered label-placement="left">
          <n-descriptions-item label="访问路径">
            <n-text class="mono">{{ currentPath }}</n-text>
          </n-descriptions-item>
        </n-descriptions>

        <n-space>
          <n-button type="primary" @click="router.push(taskListPath())">返回任务列表</n-button>
          <n-button @click="router.push(taskCreatePath())">创建任务</n-button>
        </n-space>
      </n-space>
    </n-card>
  </main>
</template>
