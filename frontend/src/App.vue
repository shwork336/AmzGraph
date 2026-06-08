<template>
  <n-config-provider>
    <n-layout class="app-shell">
      <n-layout-header class="app-header">
        <div>
          <strong>AmzGraph</strong>
          <span>电商 AI 图文资产流水线</span>
        </div>
        <nav class="app-nav">
          <router-link to="/tasks">任务列表</router-link>
          <router-link to="/tasks/new">创建任务</router-link>
          <router-link to="/audit-logs">操作审计</router-link>
        </nav>
      </n-layout-header>
      <n-layout-content class="app-content">
        <n-message-provider>
          <router-view />
        </n-message-provider>
      </n-layout-content>
    </n-layout>
  </n-config-provider>
</template>
