/// <reference types="vite/client" />

import 'vue-router'
import type { Permission } from '@/api/generated'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresPermission?: Permission
  }
}
