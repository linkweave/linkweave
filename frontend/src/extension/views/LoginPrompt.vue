<script setup lang="ts">
import { useExtensionStore } from '../stores/extension'
import ButtonCl from '@/components/ui/ButtonCl.vue'

const store = useExtensionStore()

function openWebApp() {
  chrome.tabs.create({ url: store.webAppUrl })
  window.close()
}
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-4 p-8 text-center">
    <img src="@/assets/ChainlinkLogoTrResc.png" alt="Chainlink" class="w-12 h-12 opacity-80" />
    <div>
      <p class="font-medium text-sm">Sign in to Chainlink</p>
      <p class="text-xs text-muted-foreground mt-1">
        Open the web app to sign in with Google or your email.
      </p>
    </div>
    <ButtonCl @click="openWebApp">Open Chainlink</ButtonCl>
  </div>
</template>
