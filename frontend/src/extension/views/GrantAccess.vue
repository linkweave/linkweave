<script setup lang="ts">
import { useExtensionStore } from '../stores/extension'
import ButtonLw from '@/components/ui/ButtonLw.vue'

const store = useExtensionStore()

function tryExtractHost(url: string): string {
  try { return new URL(url).host || url } catch { return url }
}

function openOptions(): void {
  chrome.runtime.openOptionsPage()
}
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-4 p-8 text-center">
    <img src="@/assets/LinkWeaveLogoTrResc.png" alt="LinkWeave" class="w-12 h-12 opacity-80" />
    <div>
      <p class="font-medium text-sm">Connect to your instance</p>
      <p class="text-xs text-muted-foreground mt-1">
        Allow the extension to reach your LinkWeave API at
        <span class="font-medium text-foreground">{{ tryExtractHost(store.apiUrl) }}</span>.
      </p>
    </div>
    <ButtonLw :disabled="store.granting" @click="store.grantPermission()">Grant access</ButtonLw>
    <button
      class="text-xs text-muted-foreground hover:text-foreground underline-offset-2 hover:underline"
      @click="openOptions"
    >
      Open options
    </button>
    <p v-if="store.error" class="text-xs text-destructive text-center">{{ store.error }}</p>
  </div>
</template>
