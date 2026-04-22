<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useExtensionStore } from '../stores/extension'
import { FolderSelectCl } from '@/components/ui'
import TagSelect from '../components/TagSelect.vue'
import ButtonCl from '@/components/ui/ButtonCl.vue'

const props = defineProps<{
  initialUrl: string
  initialTitle: string
}>()

const emit = defineEmits<{ saved: []; browse: [] }>()

const store = useExtensionStore()

const url = ref(props.initialUrl)
const title = ref(props.initialTitle)
const description = ref('')
const folderId = ref<string | undefined>(undefined)
const selectedTagIds = ref<Set<string>>(new Set())
const domainOnly = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const saved = ref(false)
const savedTitle = ref('')

// When props change (e.g. context menu URL arrives after mount)
watch(() => props.initialUrl, (v) => { url.value = v })
watch(() => props.initialTitle, (v) => { title.value = v })

const effectiveUrl = computed(() => {
  if (!domainOnly.value) return url.value
  try {
    const { origin } = new URL(url.value)
    return origin
  } catch {
    return url.value
  }
})

const collectionId = computed(() => store.currentCollectionId ?? '')

function toggleTag(tagId: string) {
  const next = new Set(selectedTagIds.value)
  if (next.has(tagId)) next.delete(tagId)
  else next.add(tagId)
  selectedTagIds.value = next
}

async function save() {
  if (!collectionId.value || !title.value.trim() || !effectiveUrl.value.trim()) return
  saving.value = true
  error.value = null
  try {
    await store.createBookmark({
      collectionId: collectionId.value,
      folderId: folderId.value ?? undefined,
      title: title.value.trim(),
      url: effectiveUrl.value.trim(),
      description: description.value.trim() || undefined,
      tagIds: selectedTagIds.value.size > 0 ? selectedTagIds.value : undefined,
    })
    savedTitle.value = title.value.trim()
    saved.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save bookmark'
  } finally {
    saving.value = false
  }
}

function saveAnother() {
  saved.value = false
  url.value = props.initialUrl
  title.value = props.initialTitle
  description.value = ''
  folderId.value = undefined
  selectedTagIds.value = new Set()
  domainOnly.value = false
  error.value = null
}
</script>

<template>
  <!-- Success state -->
  <div v-if="saved" class="flex flex-col items-center justify-center gap-3 p-8 text-center">
    <div class="text-2xl">✓</div>
    <p class="font-medium text-sm">Saved!</p>
    <p class="text-xs text-muted-foreground truncate max-w-full px-4">{{ savedTitle }}</p>
    <div class="flex gap-2 mt-1">
      <ButtonCl size="sm" variant="outline" @click="saveAnother">Save another</ButtonCl>
      <ButtonCl size="sm" @click="emit('saved')">Browse</ButtonCl>
    </div>
  </div>

  <!-- Already saved banner -->
  <div
    v-if="!saved && store.alreadySavedBookmark"
    class="mx-4 mt-4 flex items-center gap-2 rounded-md border border-primary/30 bg-primary/10 px-3 py-2 text-xs"
  >
    <span class="text-primary shrink-0">✓</span>
    <span class="text-foreground/80 flex-1 truncate">
      Already saved as <em class="not-italic font-medium">{{ store.alreadySavedBookmark.data.title }}</em>
    </span>
    <button
      type="button"
      class="text-primary hover:text-primary/80 font-medium shrink-0"
      @click="emit('browse')"
    >
      View
    </button>
  </div>

  <!-- Form -->
  <form v-if="!saved" class="p-4 space-y-3" @submit.prevent="save">
    <!-- URL -->
    <div class="space-y-1.5">
      <div class="flex items-center justify-between">
        <label class="text-xs font-medium">URL</label>
        <div class="flex gap-0.5">
          <button
            type="button"
            class="text-[10px] px-1.5 py-0.5 rounded transition-colors"
            :class="!domainOnly ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
            @click="domainOnly = false"
          >
            Full URL
          </button>
          <button
            type="button"
            class="text-[10px] px-1.5 py-0.5 rounded transition-colors"
            :class="domainOnly ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'"
            @click="domainOnly = true"
          >
            Domain only
          </button>
        </div>
      </div>
      <input
        v-model="url"
        type="url"
        required
        placeholder="https://..."
        class="flex h-8 w-full rounded-md border border-input bg-transparent px-3 py-1 text-xs shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      />
      <p v-if="domainOnly" class="text-[10px] text-muted-foreground">
        Will save: {{ effectiveUrl }}
      </p>
    </div>

    <!-- Title -->
    <div class="space-y-1.5">
      <label class="text-xs font-medium">Title <span class="text-destructive">*</span></label>
      <input
        v-model="title"
        type="text"
        required
        placeholder="Page title"
        class="flex h-8 w-full rounded-md border border-input bg-transparent px-3 py-1 text-xs shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      />
    </div>

    <!-- Description -->
    <div class="space-y-1.5">
      <label class="text-xs font-medium">Description</label>
      <textarea
        v-model="description"
        rows="2"
        placeholder="Optional note…"
        class="flex w-full rounded-md border border-input bg-transparent px-3 py-1 text-xs shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
      />
    </div>

    <!-- Folder -->
    <div class="space-y-1.5">
      <label class="text-xs font-medium">Folder</label>
      <FolderSelectCl v-model="folderId" :folders="store.folders" />
    </div>

    <!-- Tags -->
    <div v-if="store.tags.length > 0" class="space-y-1.5">
      <label class="text-xs font-medium">Tags</label>
      <TagSelect
        :tags="store.tags"
        :selected="selectedTagIds"
        placeholder="Add tags…"
        @toggle="toggleTag"
        @clear="selectedTagIds = new Set()"
      />
    </div>

    <!-- Error -->
    <p v-if="error" class="text-xs text-destructive">{{ error }}</p>

    <!-- Submit -->
    <ButtonCl type="submit" class="w-full" :disabled="saving || !title.trim()">
      {{ saving ? 'Saving…' : 'Save Bookmark' }}
    </ButtonCl>
  </form>
</template>
