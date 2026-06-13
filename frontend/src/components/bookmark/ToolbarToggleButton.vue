<script setup lang="ts">
// Shared ghost-button for the list-toolbar toggles (Select, Previews):
// bordered, 12px/600, primary-tinted when active. Consumers pass icon +
// label via the slot and aria-label/title/handlers via fallthrough attrs.
defineProps<{ active: boolean }>()
</script>

<template>
  <button
    type="button"
    class="toolbar-toggle"
    :class="{ 'is-on': active }"
    :aria-pressed="active"
  >
    <slot />
  </button>
</template>

<style scoped>
.toolbar-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 8px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-muted-foreground);
  transition: color 0.15s, background-color 0.15s, border-color 0.15s;
}

.toolbar-toggle:hover {
  color: var(--color-foreground);
}

.toolbar-toggle.is-on {
  border-color: color-mix(in oklab, var(--color-primary) 90%, black);
  background: color-mix(in oklab, var(--color-primary) 13%, transparent);
  color: color-mix(in oklab, var(--color-primary) 65%, white);
}
</style>
