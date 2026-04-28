import { Configuration } from '@/api/generated'
import { createOfflineMiddleware } from '@/lib/offline-middleware'

const config = new Configuration({
  basePath: '',
  credentials: 'include',
  middleware: [createOfflineMiddleware()],
})

export { config }
