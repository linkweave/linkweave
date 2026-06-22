<script setup lang="ts">
import { useExtensionStore } from '../stores/extension'
import ButtonLw from '@/components/ui/ButtonLw.vue'

const store = useExtensionStore()

function openWebApp() {
  chrome.tabs.create({ url: store.webAppUrl })
  window.close()
}
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-4 p-8 text-center">
    <img src="@/assets/ChainlinkLogoTrResc.png" alt="LinkWeave" class="w-12 h-12 opacity-80" />
    <div>
      <p class="font-medium text-sm">Sign in to LinkWeave</p>
      <p class="text-xs text-muted-foreground mt-1">
        Open the web app to sign in with Google or your email.
      </p>
    </div>
    <ButtonLw @click="openWebApp">Open LinkWeave</ButtonLw>
  </div>
</template>
