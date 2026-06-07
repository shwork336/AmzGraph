import { createRouter, createWebHistory } from 'vue-router';
import ExportArchivePage from './views/ExportArchivePage.vue';
import TaskCreatePage from './views/TaskCreatePage.vue';
import TaskListPage from './views/TaskListPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/tasks', component: TaskListPage },
    { path: '/tasks/new', component: TaskCreatePage },
    { path: '/tasks/:taskId/export', component: ExportArchivePage, props: true }
  ]
});
