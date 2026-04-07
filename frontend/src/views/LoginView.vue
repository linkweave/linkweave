<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { ButtonCl } from '@/components/ui'
import logoUrl from '@/assets/ChainlinkLogoTrResc.png'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const collection = useCollectionStore()
const notification = useNotificationStore()

const email = ref('')
const password = ref('')
const loading = ref(false)

function signInWithGoogle() {
  window.location.href = '/api/auth/oidc-login'
}

async function handleLogin() {
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
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    })

    if (response.status === 200) {
      const authenticated = await auth.fetchCurrentUser()
      if (authenticated && auth.user?.defaultCollectionId) {
        collection.setCurrentCollectionId(auth.user.defaultCollectionId)
      }
      router.push('/')
    } else {
      notification.error(t('login.error'))
    }
  } catch {
    notification.error(t('login.error'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div
    class="min-h-screen flex items-center justify-center bg-background"
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
        <h1 class="text-2xl font-bold tracking-tight">{{ t('app.title') }}</h1>
      </div>

      <ButtonCl class="w-full" @click="signInWithGoogle">
        {{ t('login.google') }}
      </ButtonCl>

      <form @submit.prevent="handleLogin" class="space-y-4">
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

        <ButtonCl type="submit" class="w-full" :disabled="loading">
          {{ loading ? t('common.loading') : t('login.submit') }}
        </ButtonCl>
      </form>
    </div>
  </div>
</template>
