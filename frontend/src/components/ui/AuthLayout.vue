<script setup lang="ts">
import logoUrl from '@/assets/LinkWeaveLogoTrResc.png'

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
        45deg,
        rgba(42, 123, 155, 1) 0%,
        rgba(46, 130, 153, 1) 27%,
        rgba(87, 199, 133, 1) 73%,
        rgba(237, 221, 83, 1) 100%
      );
    "
  >
    <div class="w-full max-w-sm space-y-8">
      <div class="text-center">
        <img :src="logoUrl" alt="LinkWeave" class="mx-auto h-32 w-32 mb-4" />
        <h1 class="text-2xl font-bold tracking-tight">{{ title }}</h1>
      </div>

      <div
        class="rounded-2xl p-6"
        style="
          background-color: rgba(30, 30, 35, 0.45);
          backdrop-filter: blur(20px);
          -webkit-backdrop-filter: blur(20px);
          border: 1px solid rgba(255, 255, 255, 0.15);
          box-shadow: 0 20px 40px -10px rgba(0, 0, 0, 0.5), inset 0 1px 1px 0 rgba(255, 255, 255, 0.08);
        "
      >
        <slot />
      </div>

      <slot name="footer" />
    </div>
  </div>
</template>
