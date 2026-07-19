import { onBeforeUnmount, onMounted, ref, watch, type Ref } from 'vue'
import { isDraggingBookmark, isDraggingFolder } from '@/composables/useDragState'

const EDGE_PX = 64
const MAX_SPEED = 14
const MIN_SPEED = 3

/**
 * Scrolls a container while a drag hovers within EDGE_PX of its top or bottom
 * edge (UC-102). Speed ramps with proximity. The returned `scrollEdge` drives
 * the `.as-up` / `.as-down` glow classes on the container.
 *
 * Listeners are registered in the capture phase: the drop targets inside the
 * tree stopPropagation() on their drag events, so bubbling-phase listeners on
 * the container would only ever fire over bare padding.
 *
 * By default the listener host is also the scrolled element. When one host
 * contains several independent scroll areas (the grouped layout's cards,
 * UC-103), `resolveTarget` picks the area under the pointer instead.
 */
export function useDndAutoScroll(
  container: Ref<HTMLElement | null>,
  resolveTarget?: (event: DragEvent) => HTMLElement | null,
) {
  const scrollEdge = ref<'up' | 'down' | null>(null)
  let activeEl: HTMLElement | null = null
  let speed = 0
  let raf = 0

  function canScroll(el: HTMLElement, edge: 'up' | 'down'): boolean {
    return edge === 'up'
      ? el.scrollTop > 0
      : el.scrollTop + el.clientHeight < el.scrollHeight - 1
  }

  function step() {
    const el = activeEl
    const edge = scrollEdge.value
    if (!el || edge === null || !canScroll(el, edge)) {
      stop()
      return
    }
    el.scrollTop += edge === 'up' ? -speed : speed
    raf = requestAnimationFrame(step)
  }

  function stop() {
    scrollEdge.value = null
    activeEl = null
    if (raf) cancelAnimationFrame(raf)
    raf = 0
  }

  function onDragOver(event: DragEvent) {
    if (!isDraggingFolder.value && !isDraggingBookmark.value) return
    const el = resolveTarget ? resolveTarget(event) : container.value
    if (!el) {
      stop()
      return
    }
    activeEl = el
    const rect = el.getBoundingClientRect()
    const fromTop = event.clientY - rect.top
    const fromBottom = rect.bottom - event.clientY
    const dist = Math.min(fromTop, fromBottom)
    const edge = fromTop < fromBottom ? 'up' : 'down'
    if (dist > EDGE_PX || !canScroll(el, edge)) {
      stop()
      return
    }
    speed = Math.max(MIN_SPEED, Math.round(((EDGE_PX - dist) / EDGE_PX) * MAX_SPEED))
    scrollEdge.value = edge
    if (!raf) raf = requestAnimationFrame(step)
  }

  onMounted(() => {
    container.value?.addEventListener('dragover', onDragOver, { capture: true })
    container.value?.addEventListener('drop', stop, { capture: true })
  })
  onBeforeUnmount(() => {
    container.value?.removeEventListener('dragover', onDragOver, { capture: true })
    container.value?.removeEventListener('drop', stop, { capture: true })
    stop()
  })
  watch([isDraggingFolder, isDraggingBookmark], ([folder, bookmark]) => {
    if (!folder && !bookmark) stop()
  })

  return { scrollEdge }
}
