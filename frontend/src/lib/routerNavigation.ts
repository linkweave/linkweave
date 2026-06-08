import type { RouteLocationRaw, Router } from 'vue-router'

/**
 * Indirection that lets non-component code (Pinia stores, and components that
 * would otherwise import `@/router`) trigger navigation without importing the
 * router module — which pulls the view + store graph back in and creates
 * circular dependencies. The router instance is registered once from the
 * composition root (`main.ts`) after it is created.
 */
let router: Router | null = null

/** Registers the application router. Call once from `main.ts`. */
export function registerRouter(instance: Router): void {
  router = instance
}

/** Navigates to a route. No-op until the router has been registered. */
export function navigate(to: RouteLocationRaw): void {
  void router?.push(to)
}
