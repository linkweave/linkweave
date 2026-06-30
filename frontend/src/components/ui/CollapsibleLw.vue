<script setup lang="ts">
// `fill` makes the collapsible a bounded, flex-filling column whose slotted
// content can scroll instead of overflowing its parent. Use it when the
// collapsible lives inside a height-constrained flex column (e.g. the sidebar
// Tags/Properties sections) and the slot is a scroll container. The slotted
// element is responsible for `flex-1 min-h-0 overflow-y-auto`.
defineProps<{ open: boolean; fill?: boolean }>()
</script>

<template>
  <div class="collapsible-wrap" :class="{ shut: !open, fill }" :inert="!open">
    <div class="collapsible-inner">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.collapsible-wrap {
  display: grid;
  grid-template-rows: 1fr;
  transition:
    grid-template-rows 0.22s ease,
    opacity 0.18s ease;
  opacity: 1;
}
.collapsible-wrap.shut {
  grid-template-rows: 0fr;
  opacity: 0;
}
.collapsible-inner {
  overflow: hidden;
}

/* Fill mode: bound the row to its flex track so the inner slot can scroll.
   `min-height: 0` on the grid item overrides its automatic min-content size,
   letting the 1fr row shrink below content height (the default behaviour
   otherwise pushes the list out of the section). */
.collapsible-wrap.fill {
  /* `auto` basis (not 0) so the wrap contributes its content height: the
     parent section hits its max-height and the wrap then shrinks to fit,
     activating the inner scroll. A 0 basis would collapse it to nothing. */
  flex: 1 1 auto;
  min-height: 0;
}
.collapsible-wrap.fill > .collapsible-inner {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
