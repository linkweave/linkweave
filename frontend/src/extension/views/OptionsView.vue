<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { loadExtensionConfig, saveExtensionConfig, getDefaults } from '../api/client'
import type { ExtensionConfig } from '../api/client'
import ButtonLw from '@/components/ui/ButtonLw.vue'

const apiUrl = ref('')
const webAppUrl = ref('')
const saving = ref(false)
const saved = ref(false)
const error = ref<string | null>(null)

const defaults = getDefaults()

onMounted(async () => {
  const config = await loadExtensionConfig()
  apiUrl.value = config.apiUrl
  webAppUrl.value = config.webAppUrl
})

async function save() {
  saving.value = true
  saved.value = false
  error.value = null
  try {
    const config: ExtensionConfig = {
      apiUrl: apiUrl.value.replace(/\/+$/, ''),
      webAppUrl: webAppUrl.value.replace(/\/+$/, ''),
    }
    await saveExtensionConfig(config)
    saved.value = true
    setTimeout(() => { saved.value = false }, 3000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

function resetDefaults() {
  apiUrl.value = defaults.apiUrl
  webAppUrl.value = defaults.webAppUrl
  saved.value = false
}
</script>

<template>
  <div class="max-w-lg mx-auto p-8">
    <!-- Header -->
    <div class="flex items-center gap-3 mb-8">
      <img src="@/assets/ChainlinkLogoTrResc.png" alt="LinkWeave" class="w-8 h-8" />
      <div>
        <h1 class="text-lg font-semibold">LinkWeave Options</h1>
        <p class="text-xs text-muted-foreground">Configure your self-hosted instance</p>
      </div>
    </div>

    <form class="space-y-5" @submit.prevent="save">
      <!-- API URL -->
      <div class="space-y-1.5">
        <label class="block text-sm font-medium leading-none">API URL</label>
        <input
          v-model="apiUrl"
          type="url"
          required
          :placeholder="defaults.apiUrl"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
        <p class="text-xs text-muted-foreground">
          Base URL of the LinkWeave API (no trailing slash, no <code>/api</code> suffix).
        </p>
      </div>

      <!-- Web App URL -->
      <div class="space-y-1.5">
        <label class="block text-sm font-medium leading-none">Web App URL</label>
        <input
          v-model="webAppUrl"
          type="url"
          required
          :placeholder="defaults.webAppUrl"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
        <p class="text-xs text-muted-foreground">
          URL of the LinkWeave web app, used for the "Open LinkWeave" login link.
        </p>
      </div>

      <!-- Actions -->
      <div class="flex items-center gap-3 pt-2">
        <ButtonLw type="submit" :disabled="saving">
          {{ saving ? 'Saving…' : 'Save' }}
        </ButtonLw>
        <button
          type="button"
          class="text-sm text-muted-foreground hover:text-foreground transition-colors"
          @click="resetDefaults"
        >
          Reset to defaults
        </button>
      </div>

      <!-- Feedback -->
      <p v-if="saved" class="text-sm text-primary">
        Saved. Reopen the popup to use the new settings.
      </p>
      <p v-if="error" class="text-sm text-destructive">{{ error }}</p>
    </form>
  </div>
</template>
