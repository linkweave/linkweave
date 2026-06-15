import { vi } from 'vitest'
import { FailureType } from '@/api/generated'
import { FetchError, ResponseError } from '@/api/generated/runtime'
import {
  extractErrorSummary,
  extractResponseError,
  extractResponseErrorSummary,
  isNetworkError,
  isResponseError,
} from './error-utils'

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), { status: 400 })
}

describe('isNetworkError', () => {
  it('is true for FetchError and TypeError', () => {
    expect(isNetworkError(new FetchError(new Error('boom')))).toBe(true)
    expect(isNetworkError(new TypeError('Failed to fetch'))).toBe(true)
  })

  it('is false for ResponseError, plain errors, and non-errors', () => {
    expect(isNetworkError(new ResponseError(new Response(null)))).toBe(false)
    expect(isNetworkError(new Error('nope'))).toBe(false)
    expect(isNetworkError('a string')).toBe(false)
    expect(isNetworkError(null)).toBe(false)
  })
})

describe('isResponseError', () => {
  it('narrows ResponseError and rejects everything else', () => {
    expect(isResponseError(new ResponseError(new Response(null)))).toBe(true)
    expect(isResponseError(new FetchError(new Error('boom')))).toBe(false)
    expect(isResponseError(new Error('nope'))).toBe(false)
    expect(isResponseError(undefined)).toBe(false)
  })
})

describe('extractResponseError', () => {
  it('joins validation violation messages', async () => {
    const res = jsonResponse({
      type: FailureType.Validation,
      violations: [{ message: 'too short' }, { message: 'is required' }],
    })
    expect(await extractResponseError(res)).toEqual({ message: 'too short, is required', isAppError: true })
  })

  it('returns an empty message for an empty violations array', async () => {
    const res = jsonResponse({ type: FailureType.Validation, violations: [] })
    expect(await extractResponseError(res)).toEqual({ message: '', isAppError: true })
  })

  it('falls back to the summary when a validation type has no violations array', async () => {
    const res = jsonResponse({ type: FailureType.Validation, id: 'e1', summary: 'Invalid input', violations: 'nope' })
    expect(await extractResponseError(res)).toEqual({ message: 'Invalid input', isAppError: true })
  })

  it('uses the summary of a non-validation app failure', async () => {
    const res = jsonResponse({ type: 'BUSINESS', id: 'e1', summary: 'Boom happened' })
    expect(await extractResponseError(res)).toEqual({ message: 'Boom happened', isAppError: true })
  })

  it('returns null when an app failure has a blank summary', async () => {
    const res = jsonResponse({ type: 'BUSINESS', id: 'e1', summary: '' })
    expect(await extractResponseError(res)).toBeNull()
  })

  it('returns null for JSON that is not an app failure', async () => {
    expect(await extractResponseError(jsonResponse({ foo: 'bar' }))).toBeNull()
  })

  it('returns null when the body is not valid JSON', async () => {
    expect(await extractResponseError(new Response('<html>not json</html>'))).toBeNull()
  })
})

describe('extractResponseErrorSummary', () => {
  it('returns the extracted message when present', async () => {
    const res = jsonResponse({ type: 'BUSINESS', id: 'e1', summary: 'Server says no' })
    expect(await extractResponseErrorSummary(res, 'fallback')).toBe('Server says no')
  })

  it('returns the fallback when nothing could be extracted', async () => {
    expect(await extractResponseErrorSummary(new Response('not json'), 'fallback')).toBe('fallback')
  })
})

describe('extractErrorSummary', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns the fallback for network errors without inspecting them', async () => {
    expect(await extractErrorSummary(new FetchError(new Error('boom')), 'fallback')).toBe('fallback')
    expect(await extractErrorSummary(new TypeError('Failed to fetch'), 'fallback')).toBe('fallback')
  })

  it('extracts the summary from a ResponseError body', async () => {
    const error = new ResponseError(jsonResponse({ type: 'BUSINESS', id: 'e1', summary: 'Server says no' }))
    expect(await extractErrorSummary(error, 'fallback')).toBe('Server says no')
  })

  it('returns the fallback when a ResponseError body is not an app failure', async () => {
    const error = new ResponseError(new Response('not json'))
    expect(await extractErrorSummary(error, 'fallback')).toBe('fallback')
  })

  it('logs unknown error shapes and returns the fallback', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const weird = new Error('weird')

    expect(await extractErrorSummary(weird, 'fallback')).toBe('fallback')
    expect(await extractErrorSummary('just a string', 'fallback')).toBe('fallback')
    expect(consoleError).toHaveBeenCalledWith(weird)
    expect(consoleError).toHaveBeenCalledWith('just a string')
  })
})
