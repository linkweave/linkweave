import { FetchError, ResponseError } from './api'

/** Exit code contract (UC-079 BR-017). */
export const EXIT_ERROR = 1
export const EXIT_USAGE = 2

/** An error whose message is shown to the user as `Error: <message>`. */
export class CliError extends Error {
  readonly exitCode: number

  constructor(message: string, exitCode: number = EXIT_ERROR) {
    super(message)
    this.name = 'CliError'
    this.exitCode = exitCode
  }
}

export const NOT_AUTHENTICATED_MESSAGE =
  "Not authenticated. Run 'linkweave login' to configure your API key."

export const AUTH_FAILED_MESSAGE =
  "Authentication failed. Your API key may have been revoked. Run 'linkweave login' to reconfigure."

export const TLS_FAILED_MESSAGE =
  'TLS certificate verification failed. Use --insecure flag for local development only.'

const TLS_ERROR_CODES = new Set([
  'DEPTH_ZERO_SELF_SIGNED_CERT',
  'SELF_SIGNED_CERT_IN_CHAIN',
  'UNABLE_TO_VERIFY_LEAF_SIGNATURE',
  'UNABLE_TO_GET_ISSUER_CERT',
  'UNABLE_TO_GET_ISSUER_CERT_LOCALLY',
  'CERT_HAS_EXPIRED',
  'ERR_TLS_CERT_ALTNAME_INVALID',
  'HOSTNAME_MISMATCH',
])

/** Walks the `cause` chain looking for a node TLS verification error code. */
export function isTlsError(error: unknown): boolean {
  let current: unknown = error
  for (let depth = 0; current !== undefined && current !== null && depth < 8; depth++) {
    if (typeof current === 'object') {
      const code = (current as { code?: unknown }).code
      if (typeof code === 'string' && TLS_ERROR_CODES.has(code)) return true
      current = (current as { cause?: unknown }).cause
    } else {
      break
    }
  }
  return false
}

export interface HttpErrorContext {
  /** Server base URL, used in the network-failure message. */
  server: string
  /** Message for HTTP 404 responses. */
  notFound?: string
  /** Message for HTTP 403 responses. */
  forbidden?: string
}

/**
 * Maps a failure from the generated client to a CliError with the messages
 * mandated by UC-079/UC-080 (A1-A7). CliErrors pass through unchanged.
 */
export async function toCliError(error: unknown, context: HttpErrorContext): Promise<CliError> {
  if (error instanceof CliError) return error

  if (error instanceof ResponseError) {
    const status = error.response.status
    switch (status) {
      case 401:
        return new CliError(AUTH_FAILED_MESSAGE)
      case 403:
        return new CliError(context.forbidden ?? 'Access denied.')
      case 404:
        return new CliError(context.notFound ?? 'Not found (HTTP 404).')
      default: {
        const body = await error.response.text().catch(() => '')
        const detail = body ? `: ${body.slice(0, 500)}` : ''
        return new CliError(`Server returned HTTP ${status}${detail}`)
      }
    }
  }

  if (error instanceof FetchError || error instanceof TypeError) {
    if (isTlsError(error)) return new CliError(TLS_FAILED_MESSAGE)
    return new CliError(
      `Cannot reach LinkWeave server at ${context.server}. Check your network connection and server URL.`,
    )
  }

  return new CliError(error instanceof Error ? error.message : String(error))
}
