<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/stores/notification'
import { AuthLayout, ButtonCl } from '@/components/ui'

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
  <AuthLayout :title="t('register.title')">
    <form @submit.prevent="handleRegister" class="space-y-4">
      <div class="space-y-2">
        <label for="firstName" class="text-sm font-medium">{{ t('register.firstName') }}</label>
        <input
          id="firstName"
          v-model="firstName"
          type="text"
          required
          class="flex h-9 w-full rounded-md border border-white/30 bg-white/20 px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-white/60 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/50"
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
          class="flex h-9 w-full rounded-md border border-white/30 bg-white/20 px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-white/60 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/50"
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
          class="flex h-9 w-full rounded-md border border-white/30 bg-white/20 px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-white/60 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/50"
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
          class="flex h-9 w-full rounded-md border border-white/30 bg-white/20 px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-white/60 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/50"
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
          class="flex h-9 w-full rounded-md border border-white/30 bg-white/20 px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-white/60 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/50"
          :class="{ 'border-red-500': passwordMismatch }"
          :placeholder="t('register.confirmPasswordPlaceholder')"
        />
        <p v-if="passwordMismatch" class="text-xs text-red-400">{{ t('register.passwordMismatch') }}</p>
      </div>

      <ButtonCl type="submit" class="w-full" :disabled="loading || passwordMismatch">
        {{ loading ? t('common.loading') : t('register.submit') }}
      </ButtonCl>
    </form>

    <template #footer>
      <p class="text-center text-sm">
        {{ t('register.hasAccount') }}
        <router-link :to="{ name: 'login' }" class="underline hover:text-white/80">{{ t('register.signIn') }}</router-link>
      </p>
    </template>
  </AuthLayout>
</template>
