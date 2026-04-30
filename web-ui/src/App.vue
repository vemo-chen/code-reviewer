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
</style>
