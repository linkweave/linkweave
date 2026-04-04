<script setup lang="ts">
import { RouterView } from 'vue-router'
import 'vue-sonner/style.css'
import { Toaster } from 'vue-sonner'
</script>

<template>
  <Toaster position="bottom-right" :toastOptions="{ duration: 4000 }" />
  <RouterView />
</template>
