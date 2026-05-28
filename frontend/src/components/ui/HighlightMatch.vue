<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ text: string; filter: string }>()

const parts = computed(() => {
  if (!props.filter) return [{ text: props.text, match: false }]
  const idx = props.text.toLowerCase().indexOf(props.filter.toLowerCase())
  if (idx === -1) return [{ text: props.text, match: false }]
  return [
    { text: props.text.slice(0, idx), match: false },
    { text: props.text.slice(idx, idx + props.filter.length), match: true },
    { text: props.text.slice(idx + props.filter.length), match: false },
  ]
})
</script>

<template>
  <span>
    <template v-for="(p, i) in parts" :key="i">
      <mark v-if="p.match" class="rounded-[2px] bg-primary/20 px-px text-inherit">{{ p.text }}</mark>
      <template v-else>{{ p.text }}</template>
    </template>
  </span>
</template>
