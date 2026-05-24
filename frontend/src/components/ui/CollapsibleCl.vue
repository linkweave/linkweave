<script setup lang="ts">
defineProps<{ open: boolean }>()
</script>

<template>
  <div class="collapsible-wrap" :class="{ shut: !open }">
    <div class="collapsible-inner">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.collapsible-wrap {
  display: grid;
  grid-template-rows: 1fr;
  transition:
    grid-template-rows 0.22s ease,
    opacity 0.18s ease;
  opacity: 1;
}
.collapsible-wrap.shut {
  grid-template-rows: 0fr;
  opacity: 0;
}
.collapsible-inner {
  overflow: hidden;
}
</style>
