<script setup lang="ts">
import { MainLayout } from '@/components/layout'
import { ButtonLw, DialogLw, DialogFooterLw, ResponsiveButton, SearchBar } from '@/components/ui'
import { fetchAllUsers, resetUserPassword, type AdminUser } from '@/api/admin'
import { useNotificationStore } from '@/stores/notification'
import { useAuthStore } from '@/stores/auth'
import { Permission } from '@/api/generated'
import { ArrowLeft, Check, Copy, KeyRound, RefreshCw } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()
const notification = useNotificationStore()
const auth = useAuthStore()

const users = ref<AdminUser[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)
const searchQuery = ref('')

const filteredUsers = computed<AdminUser[]>(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter((u) => {
    return (
      u.email.toLowerCase().includes(q) ||
      u.firstName.toLowerCase().includes(q) ||
      u.lastName.toLowerCase().includes(q) ||
      `${u.firstName} ${u.lastName}`.toLowerCase().includes(q)
    )
  })
})

// Confirm-reset dialog state
const confirmOpen = ref(false)
const targetUser = ref<AdminUser | null>(null)
const resetting = ref(false)

// Result dialog state
const resultOpen = ref(false)
const generatedPassword = ref('')
const copied = ref(false)

onMounted(loadUsers)

async function loadUsers() {
  loading.value = true
  loadError.value = null
  try {
    users.value = await fetchAllUsers()
  } catch (e) {
    loadError.value = (e as Error).message
    notification.error(t('admin.loadError'))
  } finally {
    loading.value = false
  }
}

function openConfirm(user: AdminUser) {
  targetUser.value = user
  confirmOpen.value = true
}

async function performReset() {
  if (!targetUser.value) return
  resetting.value = true
  try {
    const result = await resetUserPassword(targetUser.value.id)
    generatedPassword.value = result.newPassword
    copied.value = false
    confirmOpen.value = false
    resultOpen.value = true
    notification.success(t('admin.resetSuccess', { name: userName(targetUser.value) }))
  } catch {
    notification.error(t('admin.resetError'))
    confirmOpen.value = false
  } finally {
    resetting.value = false
  }
}

async function copyPassword() {
  try {
    await navigator.clipboard.writeText(generatedPassword.value)
    copied.value = true
  } catch {
    // Clipboard can be unavailable in non-secure contexts — the user can
    // still select-and-copy from the read-only input.
  }
}

function roleBadgeClass(user: AdminUser): string {
  if (user.roles.includes(Permission.SystemAdmin)) return 'bg-red-500/15 text-red-600'
  if (user.roles.includes(Permission.Support)) return 'bg-amber-500/15 text-amber-600'
  return 'bg-blue-500/15 text-blue-600'
}

function roleLabel(user: AdminUser): string {
  if (user.roles.includes(Permission.SystemAdmin)) return t('admin.roleAdmin')
  if (user.roles.includes(Permission.Support)) return t('admin.roleSupport')
  return t('admin.roleUser')
}

function authProviderLabel(provider: AdminUser['authProvider']): string {
  if (provider === 'FORM') return t('admin.providerForm')
  if (provider === 'OIDC') return t('admin.providerOidc')
  return '—'
}

function userName(user: AdminUser): string {
  return `${user.firstName} ${user.lastName}`
}

function initials(user: AdminUser): string {
  return (user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase() || '?'
}

function goBack() {
  router.go(-1)
}
</script>

<template>
  <MainLayout hide-sidebar>
    <template #header-leading>
      <ButtonLw
        variant="ghost"
        size="icon"
        data-testid="admin-back-btn"
        :aria-label="t('common.back')"
        @click="goBack"
      >
        <ArrowLeft class="h-4 w-4" />
      </ButtonLw>
    </template>

    <template #header-title>
      <span class="text-base font-semibold text-foreground truncate">
        {{ t('admin.title') }}
      </span>
    </template>

    <template #header-actions>
      <ResponsiveButton
        :label="t('admin.refresh')"
        data-testid="admin-refresh-btn"
        :disabled="loading"
        @click="loadUsers"
      >
        <RefreshCw />
      </ResponsiveButton>
    </template>

    <div class="max-w-3xl mx-auto space-y-4">
      <p class="text-sm text-muted-foreground">{{ t('admin.intro') }}</p>

      <SearchBar
        v-model="searchQuery"
        :placeholder="t('admin.searchPlaceholder')"
      />

      <div
        v-if="loading"
        data-testid="admin-users-loading"
        class="text-center py-8 text-muted-foreground"
      >
        {{ t('common.loading') }}
      </div>

      <div
        v-else-if="loadError"
        data-testid="admin-users-error"
        class="text-center py-8 text-destructive"
      >
        {{ loadError }}
      </div>

      <div
        v-else-if="filteredUsers.length === 0"
        class="text-center py-8 text-muted-foreground"
      >
        {{ searchQuery ? t('admin.noResults') : t('admin.noUsers') }}
      </div>

      <div v-else class="space-y-2">
        <div
          v-for="user in filteredUsers"
          :key="user.id"
          :data-testid="`admin-user-row-${user.id}`"
          class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-3 p-3 rounded-lg border border-border bg-card hover:bg-accent/50 transition-colors"
        >
          <div class="flex items-center gap-3 flex-1 min-w-0">
            <span
              class="hidden sm:inline-flex h-9 w-9 shrink-0 rounded-full bg-secondary text-secondary-foreground items-center justify-center text-xs font-semibold select-none"
            >
              {{ initials(user) }}
            </span>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium text-foreground truncate">{{ userName(user) }}</span>
                <span
                  class="text-[9px] px-1.5 py-0.5 rounded shrink-0"
                  :class="roleBadgeClass(user)"
                >
                  {{ roleLabel(user) }}
                </span>
                <span
                  v-if="!user.active"
                  class="text-[9px] bg-muted text-muted-foreground px-1.5 py-0.5 rounded shrink-0"
                >
                  {{ t('admin.inactive') }}
                </span>
              </div>
              <div class="text-xs text-muted-foreground mt-0.5 truncate">{{ user.email }}</div>
              <div class="text-[10px] text-muted-foreground/70 mt-0.5">
                {{ t('admin.provider') }}: {{ authProviderLabel(user.authProvider) }}
              </div>
            </div>
          </div>

          <div class="flex items-center justify-end gap-1 shrink-0">
            <ButtonLw
              variant="ghost"
              size="sm"
              :data-testid="`admin-reset-password-btn-${user.id}`"
              :title="t('admin.resetAction')"
              @click="openConfirm(user)"
            >
              <KeyRound class="h-4 w-4" />
              <span class="hidden sm:inline">{{ t('admin.resetAction') }}</span>
            </ButtonLw>
          </div>
        </div>
      </div>

      <p
        v-if="auth.user && !auth.user.permissions.has(Permission.Support)"
        class="text-xs text-muted-foreground"
      >
        {{ t('admin.notAuthorizedHint') }}
      </p>
    </div>

    <!-- Confirm reset dialog -->
    <DialogLw :open="confirmOpen" @update:open="confirmOpen = $event">
      <template #title>{{ t('admin.confirmTitle') }}</template>
      <div class="space-y-2 text-sm text-muted-foreground">
        <p>{{ t('admin.confirmBody', { name: targetUser ? userName(targetUser) : '' }) }}</p>
        <p v-if="targetUser" class="font-mono text-foreground">{{ targetUser.email }}</p>
        <p class="text-xs">{{ t('admin.confirmWarning') }}</p>
      </div>
      <template #footer>
        <DialogFooterLw>
          <ButtonLw type="button" variant="outline" @click="confirmOpen = false">
            {{ t('common.cancel') }}
          </ButtonLw>
          <ButtonLw
            type="button"
            variant="destructive"
            :disabled="resetting"
            data-testid="admin-confirm-reset-btn"
            @click="performReset"
          >
            {{ resetting ? t('common.loading') : t('admin.confirmSubmit') }}
          </ButtonLw>
        </DialogFooterLw>
      </template>
    </DialogLw>

    <!-- Result dialog with the generated password -->
    <DialogLw :open="resultOpen" @update:open="resultOpen = $event">
      <template #title>{{ t('admin.resultTitle') }}</template>
      <div class="space-y-3 text-sm">
        <p class="text-muted-foreground">
          {{ t('admin.resultBody') }}
          <span v-if="targetUser" class="font-medium text-foreground">{{ userName(targetUser) }}</span>
        </p>
        <div class="flex items-stretch gap-2">
          <input
            :value="generatedPassword"
            type="text"
            readonly
            data-testid="admin-generated-password"
            class="flex-1 rounded-md border border-input bg-background px-3 py-2 font-mono text-sm focus:outline-none focus:ring-1 focus:ring-ring"
            @focus="($event.target as HTMLInputElement).select()"
          />
          <ButtonLw
            type="button"
            variant="outline"
            data-testid="admin-copy-password-btn"
            :aria-label="t('admin.copy')"
            @click="copyPassword"
          >
            <Check v-if="copied" class="h-4 w-4" />
            <Copy v-else class="h-4 w-4" />
            <span class="hidden sm:inline">{{ copied ? t('admin.copied') : t('admin.copy') }}</span>
          </ButtonLw>
        </div>
        <p class="text-xs text-amber-600">{{ t('admin.resultWarning') }}</p>
      </div>
      <template #footer>
        <DialogFooterLw>
          <ButtonLw
            type="button"
            data-testid="admin-result-done-btn"
            @click="resultOpen = false"
          >
            {{ t('common.done') }}
          </ButtonLw>
        </DialogFooterLw>
      </template>
    </DialogLw>
  </MainLayout>
</template>
