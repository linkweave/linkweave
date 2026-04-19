<script setup lang="ts">
defineProps<{
  label: string
  forId: string
  error?: string
  required?: boolean
}>()
</script>

<template>
  <div class="space-y-2" :class="{ 'form-field-error': error }">
    <label :for="forId" class="text-sm font-medium">
      {{ label }}
      <span v-if="required" class="text-destructive"> *</span>
    </label>
    <slot />
    <p v-if="error" class="text-sm text-destructive" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.form-field-error :deep(input),
.form-field-error :deep(textarea),
.form-field-error :deep(select) {
  border-color: var(--color-destructive) !important;
}
</style>
