import { createRouter, createWebHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/tasks' },
    { path: '/audit-logs', component: () => import('./views/AuditLogPage.vue') },
    { path: '/tasks', component: () => import('./views/TaskListPage.vue') },
    { path: '/tasks/new', component: () => import('./views/TaskCreatePage.vue') },
    { path: '/tasks/:taskId', component: () => import('./views/TaskDetailPage.vue'), props: true },
    { path: '/tasks/:taskId/brief', component: () => import('./views/BriefReviewPage.vue'), props: true },
    { path: '/tasks/:taskId/compliance', component: () => import('./views/ComplianceReportPage.vue'), props: true },
    { path: '/tasks/:taskId/competitors', component: () => import('./views/CompetitorDataPage.vue'), props: true },
    { path: '/tasks/:taskId/final', component: () => import('./views/FinalReviewPage.vue'), props: true },
    { path: '/tasks/:taskId/images', component: () => import('./views/ImageVersionPage.vue'), props: true },
    { path: '/tasks/:taskId/text', component: () => import('./views/TextVersionPage.vue'), props: true },
    { path: '/tasks/:taskId/export', component: () => import('./views/ExportArchivePage.vue'), props: true },
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFoundPage.vue') }
  ]
});
