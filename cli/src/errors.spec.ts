import { describe, expect, it } from 'vitest'

import { FetchError, ResponseError } from './api'
import {
  AUTH_FAILED_MESSAGE,
  CliError,
  TLS_FAILED_MESSAGE,
  isTlsError,
  toCliError,
} from './errors'

const CONTEXT = { server: 'https://x.example' }

function responseError(status: number, body = ''): ResponseError {
  return new ResponseError(new Response(body, { status }), 'Response returned an error code')
}

describe('toCliError', () => {
  it('shouldPassCliErrorsThroughUnchanged', async () => {
    const original = new CliError('already mapped', 2)
    expect(await toCliError(original, CONTEXT)).toBe(original)
  })

  it('shouldMapUnauthorizedToTheAuthFailureMessage', async () => {
    const error = await toCliError(responseError(401), CONTEXT)
    expect(error.message).toBe(AUTH_FAILED_MESSAGE)
    expect(error.exitCode).toBe(1)
  })

  it('shouldUseTheContextMessagesForForbiddenAndNotFound', async () => {
    expect(
      (await toCliError(responseError(403), { ...CONTEXT, forbidden: 'no access' })).message,
    ).toBe('no access')
    expect(
      (await toCliError(responseError(404), { ...CONTEXT, notFound: 'gone' })).message,
    ).toBe('gone')
  })

  it('shouldIncludeStatusAndBodyForOtherHttpErrors', async () => {
    const error = await toCliError(responseError(500, 'boom'), CONTEXT)
    expect(error.message).toContain('HTTP 500')
    expect(error.message).toContain('boom')
  })

  it('shouldMapNetworkFailuresToTheUnreachableMessage', async () => {
    const cause = new TypeError('fetch failed')
    const error = await toCliError(new FetchError(cause, 'failed'), CONTEXT)
    expect(error.message).toContain('Cannot reach LinkWeave server at https://x.example')
  })

  it('shouldMapTlsFailuresToTheInsecureHint', async () => {
    // Mimics undici: TypeError('fetch failed') -> cause with a TLS error code.
    const tls = Object.assign(new Error('self-signed certificate'), {
      code: 'DEPTH_ZERO_SELF_SIGNED_CERT',
    })
    const fetchFailed = new TypeError('fetch failed')
    ;(fetchFailed as { cause?: unknown }).cause = tls

    const error = await toCliError(new FetchError(fetchFailed, 'failed'), CONTEXT)
    expect(error.message).toBe(TLS_FAILED_MESSAGE)
  })
})

describe('isTlsError', () => {
  it('shouldFindTlsCodesAnywhereInTheCauseChain', () => {
    const deep = { cause: { cause: { code: 'CERT_HAS_EXPIRED' } } }
    expect(isTlsError(deep)).toBe(true)
  })

  it('shouldNotMatchOrdinaryErrors', () => {
    expect(isTlsError(new Error('nope'))).toBe(false)
    expect(isTlsError(Object.assign(new Error('conn'), { code: 'ECONNREFUSED' }))).toBe(false)
    expect(isTlsError(undefined)).toBe(false)
  })
})
