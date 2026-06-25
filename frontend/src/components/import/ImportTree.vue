<script setup lang="ts">
import type { ImportNodeJson } from '@/api/generated'
import ImportTreeNode from '@/components/import/ImportTreeNode.vue'
import type { useImportSelection } from '@/composables/useImportSelection'

defineProps<{
  nodes: ImportNodeJson[]
  model: ReturnType<typeof useImportSelection>
}>()
</script>

<template>
  <div class="select-none">
    <ImportTreeNode v-for="node in nodes" :key="node.id" :node="node" :model="model" />
  </div>
</template>
