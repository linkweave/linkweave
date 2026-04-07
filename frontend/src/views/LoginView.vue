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

      <ButtonCl class="w-full gap-2" @click="signInWithGoogle">
        <span class="inline-flex items-center justify-center w-5 h-5 rounded-sm bg-white shrink-0">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" fill="none" viewBox="0 0 28 28"><path fill="#E94335" d="M3.242 8.679a11.5 11.5 0 0 1 3.114-3.943c1.791-1.48 3.846-2.37 6.15-2.644 2.707-.32 5.25.192 7.603 1.6.584.35 1.13.747 1.652 1.179.13.105.115.167.005.273a502 502 0 0 0-3.195 3.2c-.125.124-.192.115-.326.014-3.104-2.39-7.63-1.744-9.944 1.418a7 7 0 0 0-.968 1.806c-.02.057-.057.11-.086.167-.618-.469-1.24-.939-1.854-1.413q-1.08-.824-2.15-1.657"/><path fill="#34A853" d="M7.25 16.243c.206.455.378.93.637 1.356 1.087 1.782 2.64 2.927 4.685 3.334 1.844.369 3.616.12 5.269-.805q.087-.043.168-.086c.028.029.052.062.081.086 1.236.958 2.477 1.916 3.712 2.874-.593.59-1.269 1.064-1.988 1.476-2.083 1.188-4.34 1.667-6.715 1.485-2.96-.23-5.514-1.4-7.622-3.507-.924-.924-1.686-1.969-2.237-3.161.513-.393 1.025-.781 1.538-1.174.824-.628 1.648-1.25 2.472-1.878"/><path fill="#4285F3" d="M21.806 23.002c-1.235-.958-2.476-1.916-3.712-2.874a1 1 0 0 1-.081-.086c.426-.33.862-.652 1.211-1.073a5.6 5.6 0 0 0 1.174-2.376c.024-.11.005-.149-.105-.144-.058.005-.11 0-.168 0-1.954 0-3.914-.005-5.868.005-.216 0-.263-.058-.259-.264q.015-2.084 0-4.167c0-.177.048-.225.226-.225q5.373.008 10.754 0c.153 0 .215.038.253.201.293 1.317.274 2.64.096 3.971a13 13 0 0 1-.8 3.09 11 11 0 0 1-2.596 3.856c-.043.034-.086.058-.125.086"/><path fill="#FABB06" d="M7.246 16.244 4.775 18.12q-.769.583-1.538 1.174c-.398-.738-.656-1.519-.867-2.323-.402-1.557-.465-3.138-.244-4.724a11 11 0 0 1 1.111-3.563c.719.55 1.432 1.106 2.15 1.657q.928.71 1.855 1.413c-.106.513-.254 1.016-.302 1.543a7.4 7.4 0 0 0 .278 2.812c.019.038.024.086.028.134"/></svg>
        </span>
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
