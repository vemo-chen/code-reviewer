<template>
  <RouterView />
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
</script>

<style>
@import url("https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&display=swap");

* {
  box-sizing: border-box;
}

:root {
  color-scheme: light;
  --cr-background: #fafaf5;
  --cr-surface: #fafaf5;
  --cr-surface-low: #f4f4ef;
  --cr-surface-high: #e2e3de;
  --cr-surface-paper: #ffffff;
  --cr-outline: rgba(221, 193, 174, 0.2);
  --cr-text: #1a1c19;
  --cr-text-soft: #564334;
  --cr-primary: #ff8c00;
  --cr-primary-deep: #904d00;
  --cr-primary-soft: rgba(255, 140, 0, 0.12);
  --cr-shadow-soft: 0 24px 40px rgba(144, 77, 0, 0.08);
  --cr-shadow-card: 0 12px 32px rgba(144, 77, 0, 0.04);
  font-family: "Manrope", "PingFang SC", "Microsoft YaHei", "Noto Sans SC", sans-serif;
  color: var(--cr-text);
  background: var(--cr-background);
}

html,
body,
#app {
  margin: 0;
  min-height: 100%;
}

body {
  min-width: 320px;
  background: var(--cr-background);
}

button,
input,
textarea,
select {
  font: inherit;
}

a {
  color: inherit;
}

.el-drawer__close-btn {
  width: 30px !important;
  height: 30px !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 1px solid #e9ebec !important;
  border-radius: 9px;
  background: #ffffff !important;
  color: rgba(96, 98, 102, 0.9);
}

.el-drawer__close-btn:hover,
.el-drawer__close-btn:focus-visible {
  border-color: var(--cr-primary) !important;
  color: var(--cr-primary) !important;
}

.el-drawer .drawer-footer .el-button:not(.el-button--warning) {
  --el-button-border-color: #e9ebec;
  --el-button-hover-border-color: var(--cr-primary);
  --el-button-hover-text-color: var(--cr-primary);
  border-color: #e9ebec !important;
}

.el-button:not(.el-button--warning):not(.el-button--primary):not(.el-button--success):not(.el-button--danger):not(.is-link) {
  --el-button-hover-border-color: var(--cr-primary);
  --el-button-hover-bg-color: rgba(255, 140, 0, 0.06);
  --el-button-hover-text-color: var(--cr-primary);
}

.el-button:not(.el-button--warning):not(.el-button--primary):not(.el-button--success):not(.el-button--danger):not(.is-link):hover,
.el-button:not(.el-button--warning):not(.el-button--primary):not(.el-button--success):not(.el-button--danger):not(.is-link):focus-visible {
  border-color: var(--cr-primary);
  background-color: rgba(255, 140, 0, 0.06);
  color: var(--cr-primary);
}

.el-pagination {
  --el-color-primary: var(--cr-primary);
  --el-pagination-hover-color: var(--cr-primary);
}

.el-pagination.is-background .btn-prev,
.el-pagination.is-background .btn-next,
.el-pagination.is-background .el-pager li {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background-color: transparent;
  color: var(--cr-text);
  box-shadow: none;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.el-pagination.is-background .btn-prev:hover,
.el-pagination.is-background .btn-next:hover,
.el-pagination.is-background .el-pager li:hover {
  border-color: rgba(255, 140, 0, 0.24);
  background-color: transparent;
  color: var(--cr-primary);
}

.el-pagination.is-background .el-pager li.is-active {
  border-color: rgba(255, 140, 0, 0.28);
  background-color: transparent;
  color: var(--cr-primary);
  box-shadow: none;
}

.el-pagination.is-background .el-pager li.is-active:hover,
.el-pagination.is-background .el-pager li.is-active:focus-visible {
  border-color: rgba(255, 140, 0, 0.28);
  background-color: transparent;
  color: var(--cr-primary);
}

.el-select {
  --el-color-primary: var(--cr-primary);
}

.el-select-dropdown__item.is-hovering,
.el-select-dropdown__item:hover {
  background-color: rgba(255, 140, 0, 0.08);
}

.el-select-dropdown__item.is-selected {
  color: var(--cr-primary);
}
</style>
