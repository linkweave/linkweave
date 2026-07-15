// Single import point for the OpenAPI client generated from the running
// Quarkus server (`pnpm run generate-api` in frontend/). The CLI reuses the
// frontend's checked-in client so both stay on the exact same contract; tsup
// bundles it into dist/main.js, so the published package has no reference to
// the monorepo layout.
export {
  AuthResourceApi,
  BookmarkResourceApi,
  CollectionResourceApi,
  FolderResourceApi,
  TagResourceApi,
  Configuration,
  ResponseError,
  FetchError,
  BookmarkJsonToJSON,
  CollectionSummaryJsonToJSON,
} from '../../frontend/src/api/generated/index'

export type {
  BookmarkJson,
  BookmarkSaveJson,
  CollectionSummaryJson,
  FolderJson,
  TagJson,
  UserInfoJson,
} from '../../frontend/src/api/generated/index'
