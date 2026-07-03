<script setup lang="ts">
import type { CollectionMemberJson } from '@/api/generated'
import { CollectionRole, ResponseError } from '@/api/generated'
import { ButtonLw, DialogLw, FormFieldLw, InputLw, SelectLw } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { collectionShareSchema } from '@/schemas/collection'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { Crown, Loader2, Shield, User, UserPlus, X } from '@lucide/vue'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { computed, ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  open: boolean
  collectionId: string
  collectionName: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { t } = useI18n()
const notification = useNotificationStore()
const collectionStore = useCollectionStore()
const authStore = useAuthStore()

const members = ref<CollectionMemberJson[]>([])
const loadingMembers = ref(false)
const revokingUserId = ref<string | null>(null)
const changingRoleUserId = ref<string | null>(null)

const { defineField, handleSubmit, errors, resetForm, isSubmitting, setFieldError } = useForm({
  validationSchema: toTypedSchema(collectionShareSchema(t)),
  initialValues: {
    email: '',
    role: CollectionRole.Member,
  },
})

// Don't validate the email on blur: the field is focused on open, so moving
// focus out of it (e.g. to change an existing member's role) would otherwise
// flag the still-empty email as invalid. It's still validated on submit.
const [inviteEmail, inviteEmailAttrs] = defineField('email', { validateOnBlur: false })
const [inviteRole, inviteRoleAttrs] = defineField('role')

// The viewer's own role on this collection drives what management UI is shown.
const isViewerOwner = computed(() => collectionStore.isCollectionOwner(props.collectionId))

useFormDialog(toRef(props, 'open'), async () => {
  resetForm({ values: { email: '', role: CollectionRole.Member } })
  await loadMembers()
})

async function loadMembers() {
  loadingMembers.value = true
  try {
    members.value = await collectionStore.fetchMembers(props.collectionId)
  } catch {
    notification.error(t('collectionManage.shareLoadError'))
  } finally {
    loadingMembers.value = false
  }
}

const handleInvite = handleSubmit(async (values) => {
  try {
    const newMember = await collectionStore.shareWithUser(
      props.collectionId,
      values.email,
      values.role,
    )
    if (newMember) {
      members.value = [...members.value, newMember]
    }
    resetForm({ values: { email: '', role: CollectionRole.Member } })
    notification.success(t('collectionManage.shareSuccess'))
  } catch (err: unknown) {
    // A few invite-specific violations map to an inline field error; everything
    // else is delegated to the shared handler. clone() so the body stays
    // readable for handleApiError's own extraction in the fallback.
    let key: string | undefined
    if (err instanceof ResponseError) {
      try {
        const body = (await err.response.clone().json()) as { violations?: { key?: string }[] }
        key = body?.violations?.[0]?.key
      } catch {
        // non-JSON body (network blip / empty) — falls through to handleApiError
      }
    }
    if (key === 'ShareUserNotFound') {
      setFieldError('email', t('collectionManage.shareUserNotFound'))
    } else if (key === 'ShareAlreadyHasAccess') {
      setFieldError('email', t('collectionManage.shareAlreadyMember'))
    } else if (key === 'ShareCannotShareWithSelf') {
      setFieldError('email', t('collectionManage.shareSelfError'))
    } else {
      await notification.handleApiError(err, t('collectionManage.shareInviteError'))
    }
  }
})

async function handleRevoke(member: CollectionMemberJson) {
  if (!member.userId) return
  revokingUserId.value = member.userId
  try {
    await collectionStore.revokeAccess(props.collectionId, member.userId)
    members.value = members.value.filter((m) => m.userId !== member.userId)
  } catch (err: unknown) {
    await notification.handleApiError(err, t('collectionManage.shareRevokeError'))
  } finally {
    revokingUserId.value = null
  }
}

async function handleChangeRole(member: CollectionMemberJson, newRole: CollectionRole) {
  if (!member.userId || member.role === newRole) return
  changingRoleUserId.value = member.userId
  try {
    const updated = await collectionStore.changeMemberRole(
      props.collectionId,
      member.userId,
      newRole,
    )
    if (updated) {
      members.value = members.value.map((m) => (m.userId === member.userId ? updated : m))
      // If the viewer changed their own role (e.g. admin stepping down), sync the
      // cached role so permission-gated controls update immediately.
      if (member.userId === authStore.user?.id) {
        collectionStore.setCollectionRole(props.collectionId, updated.role)
      }
    }
  } catch (err: unknown) {
    await notification.handleApiError(err, t('collectionManage.shareRoleChangeError'))
  } finally {
    changingRoleUserId.value = null
  }
}

function ownerMember() {
  return members.value.find((m) => m.role === CollectionRole.Owner)
}

function nonOwnerMembers() {
  return members.value.filter((m) => m.role !== CollectionRole.Owner)
}

// Can the viewer demote/promote this particular member?
function canChangeRoleOf(member: CollectionMemberJson) {
  if (isViewerOwner.value) return true
  // An admin may only step themselves down to member.
  const isSelf = member.userId === authStore.user?.id
  return isSelf && member.role === CollectionRole.Admin
}

// Can the viewer remove this particular member?
function canRemove(member: CollectionMemberJson) {
  if (member.role === CollectionRole.Owner) return false
  if (isViewerOwner.value) return true
  // Admins can remove members but not other admins (or themselves while admin).
  return member.role === CollectionRole.Member
}
</script>

<template>
  <DialogLw :open="props.open" @update:open="emit('update:open', $event)">
    <template #title>
      <div class="flex items-center gap-2">
        <UserPlus class="h-4 w-4 text-muted-foreground" />
        {{ t('collectionManage.shareTitle') }}
        <span class="text-muted-foreground font-normal text-base truncate max-w-[180px]"
          >— {{ collectionName }}</span
        >
      </div>
    </template>
    <template #description>
      {{ t('collectionManage.shareDescription') }}
    </template>

    <div class="space-y-5">
      <div class="space-y-2">
        <p class="text-xs font-medium uppercase tracking-wider text-muted-foreground">
          {{ t('collectionManage.shareMembers') }}
        </p>

        <div v-if="loadingMembers" class="flex items-center justify-center py-4">
          <Loader2 class="h-5 w-5 animate-spin text-muted-foreground" />
        </div>

        <div v-else class="space-y-1">
          <div
            v-if="ownerMember()"
            class="flex items-center gap-3 rounded-md px-3 py-2 bg-muted/40"
          >
            <div
              class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/15"
            >
              <Crown class="h-3.5 w-3.5 text-primary" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium leading-none truncate">
                {{ ownerMember()!.displayName }}
              </p>
              <p class="text-xs text-muted-foreground truncate mt-0.5">
                {{ ownerMember()!.email }}
              </p>
            </div>
            <span class="text-[10px] bg-primary/20 text-primary px-1.5 py-0.5 rounded shrink-0">
              {{ t('collectionManage.shareOwnerBadge') }}
            </span>
          </div>

          <div
            v-for="member in nonOwnerMembers()"
            :key="member.userId"
            class="flex items-center gap-3 rounded-md px-3 py-2 hover:bg-accent/50 transition-colors group"
          >
            <div
              class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full"
              :class="member.role === CollectionRole.Admin ? 'bg-blue-500/15' : 'bg-muted'"
            >
              <Shield
                v-if="member.role === CollectionRole.Admin"
                class="h-3.5 w-3.5 text-blue-500"
              />
              <User v-else class="h-3.5 w-3.5 text-muted-foreground" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium leading-none truncate">{{ member.displayName }}</p>
              <p class="text-xs text-muted-foreground truncate mt-0.5">{{ member.email }}</p>
            </div>

            <!-- Role control: owner can switch anyone; admin can step self down -->
            <div v-if="canChangeRoleOf(member)" class="shrink-0">
              <Loader2
                v-if="changingRoleUserId === member.userId"
                class="h-3.5 w-3.5 animate-spin text-muted-foreground"
              />
              <SelectLw
                v-else
                :model-value="member.role"
                class="h-7 w-28 text-xs"
                :data-testid="`share-role-select-${member.userId}`"
                @update:model-value="(v) => handleChangeRole(member, v as CollectionRole)"
              >
                <option :value="CollectionRole.Member">
                  {{ t('collectionManage.shareMemberBadge') }}
                </option>
                <option :value="CollectionRole.Admin" :disabled="!isViewerOwner">
                  {{ t('collectionManage.shareAdminBadge') }}
                </option>
              </SelectLw>
            </div>
            <span
              v-else
              class="text-[10px] text-muted-foreground px-1.5 py-0.5 rounded border border-border shrink-0"
            >
              {{
                member.role === CollectionRole.Admin
                  ? t('collectionManage.shareAdminBadge')
                  : t('collectionManage.shareMemberBadge')
              }}
            </span>

            <ButtonLw
              v-if="canRemove(member)"
              variant="ghost"
              size="icon"
              class="h-8 w-8 transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 shrink-0 text-destructive hover:text-destructive hover:bg-destructive/10"
              :disabled="revokingUserId === member.userId"
              :title="t('collectionManage.shareRevokeBtn')"
              :data-testid="`share-revoke-btn-${member.userId}`"
              @click="handleRevoke(member)"
            >
              <Loader2 v-if="revokingUserId === member.userId" class="h-3.5 w-3.5 animate-spin" />
              <X v-else class="h-3.5 w-3.5" />
            </ButtonLw>
          </div>

          <p
            v-if="!loadingMembers && nonOwnerMembers().length === 0"
            class="text-xs text-muted-foreground px-3 py-2"
          >
            {{ t('collectionManage.shareNoMembers') }}
          </p>
        </div>
      </div>

      <div class="border-t border-border" />

      <form class="flex flex-col sm:flex-row gap-2 sm:items-start" @submit.prevent="handleInvite">
        <div class="flex-1">
          <FormFieldLw
            :label="t('collectionManage.shareInvite')"
            for-id="share-email-input"
            :error="errors.email"
          >
            <InputLw
              id="share-email-input"
              v-model="inviteEmail"
              v-bind="inviteEmailAttrs"
              type="email"
              autocomplete="off"
              data-testid="share-email-input"
              :placeholder="t('collectionManage.shareEmailPlaceholder')"
            />
          </FormFieldLw>
        </div>
        <div class="w-full sm:w-36">
          <FormFieldLw :label="t('collectionManage.shareInviteRole')" for-id="share-role-select">
            <SelectLw
              id="share-role-select"
              v-model="inviteRole"
              v-bind="inviteRoleAttrs"
              data-testid="share-invite-role-select"
            >
              <option :value="CollectionRole.Member">
                {{ t('collectionManage.shareInviteRoleMember') }}
              </option>
              <option :value="CollectionRole.Admin" :disabled="!isViewerOwner">
                {{ t('collectionManage.shareInviteRoleAdmin') }}
              </option>
            </SelectLw>
          </FormFieldLw>
        </div>
        <div class="w-full sm:w-auto flex flex-col">
          <!-- Invisible label spacer mirrors the fields' label row so the button
               lines up with the inputs (not the labels) on sm+ screens. -->
          <span
            aria-hidden="true"
            class="hidden sm:block text-sm font-medium leading-none select-none mb-2"
            >&nbsp;</span
          >
          <ButtonLw
            type="submit"
            data-testid="share-invite-btn"
            class="w-full sm:w-auto"
            :disabled="isSubmitting || !inviteEmail?.trim()"
          >
            <Loader2 v-if="isSubmitting" class="h-4 w-4 animate-spin" />
            <UserPlus v-else class="h-4 w-4" />
            {{ t('collectionManage.shareInviteBtn') }}
          </ButtonLw>
        </div>
      </form>
    </div>
  </DialogLw>
</template>
