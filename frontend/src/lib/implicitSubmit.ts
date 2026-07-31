/**
 * Suppresses a form's *implicit submission* — the browser behaviour where Enter
 * in any single-line field submits the form as if the submit button had been
 * pressed.
 *
 * The bookmark forms are long (url, title, description, folder, tags, suggested
 * tags, and one input per custom property definition), so implicit submission
 * routinely fired mid-form: a half-typed title with no tags yet got saved, the
 * dialog closed, and because the new row is filed by sort order rather than
 * prepended it landed below the fold — which read as "nothing happened" and got
 * re-entered, producing duplicates. Submitting is the submit button's job only.
 *
 * Bind on the `<form>` rather than per input, so fields added later are covered
 * by default:
 *
 * ```html
 * <form @keydown.enter="preventImplicitSubmit" @submit.prevent="onSubmit">
 * ```
 *
 * Enter is left alone where it has a real meaning of its own:
 * - `<textarea>` — inserts a newline.
 * - buttons, links and `role="button"` — keyboard activation. Note this covers
 *   only controls *inside* the form; a submit button wired up from outside via
 *   `form="…"` never bubbles its keydown here, so it keeps working regardless.
 * - IME composition — Enter commits the candidate rather than reaching the form.
 *
 * Components that give Enter their own behaviour (TagCombobox picking the
 * highlighted tag, for instance) handle it on the way up and are unaffected:
 * this only cancels the browser default, it does not stop propagation.
 */
export function preventImplicitSubmit(event: KeyboardEvent): void {
  if (event.isComposing) return

  const target = event.target as HTMLElement | null
  if (!target) return
  if (target.tagName === 'TEXTAREA') return
  if (target.closest('button, a, [role="button"]')) return

  event.preventDefault()
}
