// Compact custom drag image (UC-102). The browser's default ghost is a
// translucent snapshot of the whole dragged element — a full-width row or an
// entire bookmark card — which hides the insertion line under the cursor and
// makes precise drops guesswork. A small name chip hanging below-right of the
// cursor tip keeps the line and its anchor dot visible.

const ICON_PATHS = {
  folder:
    'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z',
  bookmark: 'm19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2Z',
} as const

function iconSvg(kind: keyof typeof ICON_PATHS): string {
  return (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" '
    + 'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">'
    + `<path d="${ICON_PATHS[kind]}"/></svg>`
  )
}

export function setCompactDragImage(
  event: DragEvent,
  label: string,
  kind: keyof typeof ICON_PATHS,
) {
  if (!event.dataTransfer) return
  const chip = document.createElement('div')
  chip.className = 'dnd-drag-chip'
  chip.innerHTML = iconSvg(kind)
  const name = document.createElement('span')
  name.textContent = label // user content — textContent, never innerHTML
  chip.appendChild(name)
  document.body.appendChild(chip)
  // (0,0): the chip's top-left corner rides at the cursor hotspot, so the chip
  // hangs below-right of the pointer tip and never covers what it points at.
  event.dataTransfer.setDragImage(chip, 0, 0)
  // The browser snapshots the image when the dragstart task finishes; the
  // element itself (parked off-screen by its CSS) can go right after.
  setTimeout(() => chip.remove(), 0)
}
