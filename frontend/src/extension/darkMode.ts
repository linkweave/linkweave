/**
 * Toggles the `dark` class on the document root to match the OS color scheme,
 * and keeps it in sync as the preference changes. Used by the extension's
 * popup and options entry points, which boot outside the SPA's theme setup.
 */
export function syncDarkModeWithSystem(): void {
  const apply = (dark: boolean) => document.documentElement.classList.toggle('dark', dark)
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  apply(mq.matches)
  mq.addEventListener('change', (e) => apply(e.matches))
}
