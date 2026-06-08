<template>
  <n-config-provider>
    <n-layout class="app-shell">
      <n-layout-header class="app-header">
        <div>
          <strong>AmzGraph</strong>
          <span>电商 AI 图文资产流水线</span>
        </div>
      </n-layout-header>
      <n-layout-content class="app-content">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-config-provider>
</template>
