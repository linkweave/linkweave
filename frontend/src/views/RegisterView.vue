<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'
import { ButtonCl } from '@/components/ui'
import logoUrl from '@/assets/ChainlinkLogoTrResc.png'

const { t } = useI18n()
const router = useRouter()
const notification = useNotificationStore()

const firstName = ref('')
const lastName = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const loading = ref(false)

const passwordMismatch = computed(() =>
  confirmPassword.value.length > 0 && password.value !== confirmPassword.value
)

async function handleRegister() {
  if (passwordMismatch.value) return

  loading.value = true

  try {
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: email.value,
        password: password.value,
        vorname: firstName.value,
        nachname: lastName.value,
      }),
    })

    if (response.ok) {
      notification.success(t('register.success'))
      router.push({ name: 'login' })
    } else if (response.status === 409) {
      notification.error(t('register.alreadyExists'))
    } else {
      notification.error(t('register.error'))
    }
  } catch {
    notification.error(t('register.error'))
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
    <div class="w-full max-w-sm space-y-6">
      <div class="text-center">
        <img :src="logoUrl" alt="Chainlink" class="mx-auto h-24 w-24 mb-3" />
        <h1 class="text-xl font-bold tracking-tight">{{ t('register.title') }}</h1>
      </div>

      <form @submit.prevent="handleRegister" class="space-y-4">
        <div class="space-y-2">
          <label for="firstName" class="text-sm font-medium">{{ t('register.firstName') }}</label>
          <input
            id="firstName"
            v-model="firstName"
            type="text"
            required
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :placeholder="t('register.firstNamePlaceholder')"
          />
        </div>

        <div class="space-y-2">
          <label for="lastName" class="text-sm font-medium">{{ t('register.lastName') }}</label>
          <input
            id="lastName"
            v-model="lastName"
            type="text"
            required
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :placeholder="t('register.lastNamePlaceholder')"
          />
        </div>

        <div class="space-y-2">
          <label for="email" class="text-sm font-medium">{{ t('register.email') }}</label>
          <input
            id="email"
            v-model="email"
            type="email"
            required
            autocomplete="email"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :placeholder="t('register.emailPlaceholder')"
          />
        </div>

        <div class="space-y-2">
          <label for="password" class="text-sm font-medium">{{ t('register.password') }}</label>
          <input
            id="password"
            v-model="password"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :placeholder="t('register.passwordPlaceholder')"
          />
        </div>

        <div class="space-y-2">
          <label for="confirmPassword" class="text-sm font-medium">{{ t('register.confirmPassword') }}</label>
          <input
            id="confirmPassword"
            v-model="confirmPassword"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            :class="{ 'border-red-500': passwordMismatch }"
            :placeholder="t('register.confirmPasswordPlaceholder')"
          />
          <p v-if="passwordMismatch" class="text-xs text-red-400">{{ t('register.passwordMismatch') }}</p>
        </div>

        <ButtonCl type="submit" class="w-full" :disabled="loading || passwordMismatch">
          {{ loading ? t('common.loading') : t('register.submit') }}
        </ButtonCl>
      </form>

      <p class="text-center text-sm">
        {{ t('register.hasAccount') }}
        <router-link :to="{ name: 'login' }" class="underline hover:text-white/80">{{ t('register.signIn') }}</router-link>
      </p>
    </div>
  </div>
</template>
