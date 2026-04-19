<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { useNotificationStore } from '@/stores/notification'
import { AuthLayout, ButtonCl, FormFieldCl } from '@/components/ui'
import { registrationSchema } from '@/schemas/auth'
import { AuthResourceApi, ResponseError } from '@/api/generated'
import { config } from '@/api'

const authApi = new AuthResourceApi(config)

const { t } = useI18n()
const router = useRouter()
const notification = useNotificationStore()

const { defineField, handleSubmit, errors, isSubmitting } = useForm({
  validationSchema: toTypedSchema(registrationSchema),
  initialValues: {
    vorname: '',
    nachname: '',
    email: '',
    password: '',
    confirmPassword: '',
  },
})

const [vorname, vornameAttrs] = defineField('vorname')
const [nachname, nachnameAttrs] = defineField('nachname')
const [email, emailAttrs] = defineField('email')
const [password, passwordAttrs] = defineField('password')
const [confirmPassword, confirmPasswordAttrs] = defineField('confirmPassword')

const onSubmit = handleSubmit(async (values) => {
  try {
    await authApi.apiAuthRegisterPost({
      registrationRequestJson: {
        email: values.email,
        password: values.password,
        vorname: values.vorname,
        nachname: values.nachname,
      },
    })
    notification.success(t('register.success'))
    router.push({ name: 'login' })
  } catch (e) {
    if (e instanceof ResponseError && e.response.status === 409) {
      notification.error(t('register.alreadyExists'))
    } else {
      notification.error(t('register.error'))
    }
  }
})
</script>

<template>
  <AuthLayout :title="t('register.title')">
    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('register.firstName')" for-id="firstName" :error="errors.vorname">
        <input
          id="firstName"
          v-model="vorname"
          v-bind="vornameAttrs"
          type="text"
          class="flex h-9 w-full rounded-md bg-white/15 px-3 py-1 text-sm text-white transition-colors placeholder:text-white/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/30"
          style="border: 1px solid rgba(143, 149, 161, 0.25)"
          :placeholder="t('register.firstNamePlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('register.lastName')" for-id="lastName" :error="errors.nachname">
        <input
          id="lastName"
          v-model="nachname"
          v-bind="nachnameAttrs"
          type="text"
          class="flex h-9 w-full rounded-md bg-white/15 px-3 py-1 text-sm text-white transition-colors placeholder:text-white/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/30"
          style="border: 1px solid rgba(143, 149, 161, 0.25)"
          :placeholder="t('register.lastNamePlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('register.email')" for-id="email" :error="errors.email">
        <input
          id="email"
          v-model="email"
          v-bind="emailAttrs"
          type="email"
          autocomplete="email"
          class="flex h-9 w-full rounded-md bg-white/15 px-3 py-1 text-sm text-white transition-colors placeholder:text-white/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/30"
          style="border: 1px solid rgba(143, 149, 161, 0.25)"
          :placeholder="t('register.emailPlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('register.password')" for-id="password" :error="errors.password">
        <input
          id="password"
          v-model="password"
          v-bind="passwordAttrs"
          type="password"
          autocomplete="new-password"
          class="flex h-9 w-full rounded-md bg-white/15 px-3 py-1 text-sm text-white transition-colors placeholder:text-white/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/30"
          style="border: 1px solid rgba(143, 149, 161, 0.25)"
          :placeholder="t('register.passwordPlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('register.confirmPassword')" for-id="confirmPassword" :error="errors.confirmPassword">
        <input
          id="confirmPassword"
          v-model="confirmPassword"
          v-bind="confirmPasswordAttrs"
          type="password"
          autocomplete="new-password"
          class="flex h-9 w-full rounded-md bg-white/15 px-3 py-1 text-sm text-white transition-colors placeholder:text-white/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-white/30"
          style="border: 1px solid rgba(143, 149, 161, 0.25)"
          :placeholder="t('register.confirmPasswordPlaceholder')"
        />
      </FormFieldCl>

      <ButtonCl type="submit" class="w-full" :disabled="isSubmitting">
        {{ isSubmitting ? t('common.loading') : t('register.submit') }}
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
