<script setup lang="ts">
import type {CollectionMemberJson} from '@/api/generated'
import {ResponseError} from '@/api/generated'
import {toTypedSchema} from '@vee-validate/zod'
import {useForm} from 'vee-validate'
import {ButtonCl, DialogCl, FormFieldCl} from '@/components/ui'
import {useCollectionStore} from '@/stores/collection'
import {useNotificationStore} from '@/stores/notification'
import {collectionShareSchema} from '@/schemas/collection'
import {useFormDialog} from '@/composables/useFormDialog'
import {Crown, Loader2, User, UserPlus, X} from 'lucide-vue-next'
import {ref, toRef} from 'vue'
import {useI18n} from 'vue-i18n'

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

const members = ref<CollectionMemberJson[]>([])
const loadingMembers = ref(false)
const revokingUserId = ref<string | null>(null)

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(collectionShareSchema),
  initialValues: {
    email: '',
  },
})

const [inviteEmail, inviteEmailAttrs] = defineField('email')

useFormDialog(toRef(props, 'open'), async () => {
  resetForm({ values: { email: '' } })
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
    const newMember = await collectionStore.shareWithUser(props.collectionId, values.email)
    if (newMember) {
      members.value = [...members.value, newMember]
    }
    resetForm({ values: { email: '' } })
    notification.success(t('collectionManage.shareSuccess'))
  } catch (err: unknown) {
    if (err instanceof ResponseError) {
      try {
        const body = await err.response.json()
        const key = body?.violations?.[0]?.key ?? body?.clientKey
        if (key === 'ShareAlreadyHasAccess') {
          notification.warning(t('collectionManage.shareAlreadyMember'))
        } else if (key === 'ShareUserNotFound') {
          notification.warning(t('collectionManage.shareUserNotFound'))
        } else if (key === 'ShareCannotShareWithSelf') {
          notification.warning(t('collectionManage.shareSelfError'))
        } else {
          notification.handleApiError(err, t('collectionManage.shareInviteError'))
        }
      } catch {
        notification.error(t('collectionManage.shareInviteError'))
      }
    } else {
      notification.error(t('collectionManage.shareInviteError'))
    }
  }
})

async function handleRevoke(member: CollectionMemberJson) {
  if (!member.userId) return
  revokingUserId.value = member.userId
  try {
    await collectionStore.revokeAccess(props.collectionId, member.userId)
    members.value = members.value.filter((m) => m.userId !== member.userId)
  } catch {
    notification.error(t('collectionManage.shareInviteError'))
  } finally {
    revokingUserId.value = null
  }
}

function ownerMember() {
  return members.value.find((m) => m.role === 'OWNER')
}

function nonOwnerMembers() {
  return members.value.filter((m) => m.role !== 'OWNER')
}
</script>

<template>
  <DialogCl :open="props.open" @update:open="emit('update:open', $event)">
    <template #title>
      <div class="flex items-center gap-2">
        <UserPlus class="h-4 w-4 text-muted-foreground" />
        {{ t('collectionManage.shareTitle') }}
        <span class="text-muted-foreground font-normal text-base truncate max-w-[180px]">— {{ collectionName }}</span>
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
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary/15">
              <Crown class="h-3.5 w-3.5 text-primary" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium leading-none truncate">{{ ownerMember()!.displayName }}</p>
              <p class="text-xs text-muted-foreground truncate mt-0.5">{{ ownerMember()!.email }}</p>
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
            <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-muted">
              <User class="h-3.5 w-3.5 text-muted-foreground" />
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium leading-none truncate">{{ member.displayName }}</p>
              <p class="text-xs text-muted-foreground truncate mt-0.5">{{ member.email }}</p>
            </div>
            <span class="text-[10px] text-muted-foreground px-1.5 py-0.5 rounded border border-border shrink-0 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
              {{ t('collectionManage.shareMemberBadge') }}
            </span>
            <ButtonCl
              variant="ghost"
              size="icon"
              class="h-7 w-7 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity shrink-0 text-destructive hover:text-destructive hover:bg-destructive/10"
              :disabled="revokingUserId === member.userId"
              :title="t('collectionManage.shareRevokeBtn')"
              :data-testid="`share-revoke-btn-${member.userId}`"
              @click="handleRevoke(member)"
            >
              <Loader2 v-if="revokingUserId === member.userId" class="h-3.5 w-3.5 animate-spin" />
              <X v-else class="h-3.5 w-3.5" />
            </ButtonCl>
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

      <form class="flex gap-2 items-start" @submit.prevent="handleInvite">
        <div class="flex-1">
          <FormFieldCl :label="t('collectionManage.shareInvite')" for-id="share-email-input" :error="errors.email">
            <input
              id="share-email-input"
              v-model="inviteEmail"
              v-bind="inviteEmailAttrs"
              type="email"
              autocomplete="off"
              data-testid="share-email-input"
              :placeholder="t('collectionManage.shareEmailPlaceholder')"
              class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            />
          </FormFieldCl>
        </div>
          <ButtonCl
            type="submit"
            data-testid="share-invite-btn"
            class="mt-[1.625rem]"
            :disabled="isSubmitting || !inviteEmail?.trim()"
          >
            <Loader2 v-if="isSubmitting" class="h-4 w-4 animate-spin" />
            <UserPlus v-else class="h-4 w-4" />
            {{ t('collectionManage.shareInviteBtn') }}
          </ButtonCl>
        </form>
    </div>
  </DialogCl>
</template>
