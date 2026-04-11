<script setup lang="ts">
import logoUrl from '@/assets/ChainlinkLogoTrResc.png'

defineProps<{
  title: string
}>()
</script>

<template>
  <div
    class="min-h-screen flex items-center justify-center"
    style="
      background: #2a7b9b;
      background: linear-gradient(
        90deg,
        rgba(42, 123, 155, 1) 0%,
        rgba(46, 130, 153, 1) 27%,
        rgba(87, 199, 133, 1) 73%,
        rgba(237, 221, 83, 1) 100%
      );
    "
  >
    <div class="w-full max-w-sm space-y-8">
      <div class="text-center">
        <img :src="logoUrl" alt="Chainlink" class="mx-auto h-32 w-32 mb-4" />
        <h1 class="text-2xl font-bold tracking-tight">{{ title }}</h1>
      </div>

      <div class="rounded-2xl border border-white/20 bg-white/10 backdrop-blur-xl p-6 shadow-lg">
        <slot />
      </div>

      <slot name="footer" />
    </div>
  </div>
</template>
