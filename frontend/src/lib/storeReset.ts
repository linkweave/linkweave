/**
 * Lightweight reset registry. Lets the auth store clear all other stores on
 * logout without importing each of them (which would create circular
 * dependencies and let auth reach into other stores' internals). Each store
 * registers its own `reset` from its setup; auth broadcasts via
 * `resetAllStores()`.
 */
const handlers = new Set<() => void>()

/** Registers a reset callback. Call once from a store's setup. */
export function registerStoreReset(handler: () => void): void {
  handlers.add(handler)
}

/** Invokes every registered reset. Called on logout. */
export function resetAllStores(): void {
  for (const handler of handlers) handler()
}
