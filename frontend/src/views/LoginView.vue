<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const error = ref(false)
const loading = ref(false)

async function handleLogin() {
  error.value = false
  loading.value = true

  try {
    const formData = new URLSearchParams()
    formData.append('j_username', email.value)
    formData.append('j_password', password.value)

    const response = await fetch('/api/j_security_check', {
      method: 'POST',
      body: formData,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      }
    })

    if (response.status === 200) {
      await auth.fetchCurrentUser()
      router.push('/')
    } else {
      error.value = true
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-background">
    <div class="w-full max-w-sm space-y-8">
      <div class="text-center">
        <h1 class="text-2xl font-bold tracking-tight">{{ t('app.title') }}</h1>
      </div>

      <form @submit.prevent="handleLogin" class="space-y-4">
        <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
          {{ t('login.error') }}
        </div>

        <div class="space-y-2">
          <label for="email" class="text-sm font-medium">{{ t('login.email') }}</label>
          <input
            id="email"
            v-model="email"
            type="email"
            required
            autocomplete="email"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>

        <div class="space-y-2">
          <label for="password" class="text-sm font-medium">{{ t('login.password') }}</label>
          <input
            id="password"
            v-model="password"
            type="password"
            required
            autocomplete="current-password"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
        </div>

        <Button type="submit" class="w-full" :disabled="loading">
          {{ loading ? t('common.loading') : t('login.submit') }}
        </Button>
      </form>
    </div>
  </div>
</template>
