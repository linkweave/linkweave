import { instanceOfAppFailureErrorJson, FailureType } from '@/api/generated'
import { ResponseError, FetchError } from '@/api/generated/runtime'

export function isNetworkError(error: unknown): boolean {
  return error instanceof FetchError || error instanceof TypeError
}

export function isResponseError(error: unknown): error is ResponseError {
  return error instanceof ResponseError
}

export interface ExtractedError {
  message: string
  isAppError: boolean
}

export async function extractResponseError(response: Response): Promise<ExtractedError | null> {
  try {
    const body = await response.clone().json()
    if (body.type === FailureType.Validation && Array.isArray(body.violations)) {
      return {
        message: body.violations.map((v: { message: string }) => v.message).join(', '),
        isAppError: true,
      }
    }
    if (instanceOfAppFailureErrorJson(body) && body.summary) {
      return {
        message: body.summary,
        isAppError: true,
      }
    }
  } catch {
    // response body is not valid JSON
  }
  return null
}

export async function extractResponseErrorSummary(response: Response, fallback: string): Promise<string> {
  const extracted = await extractResponseError(response)
  return extracted?.message ?? fallback
}

export async function extractErrorSummary(error: unknown, fallback: string): Promise<string> {
  if (isNetworkError(error)) {
    return fallback
  }
  if (isResponseError(error)) {
    return extractResponseErrorSummary(error.response, fallback)
  }
  // Unknown error shape — log it so it shows up in devtools instead of being
  // silently swallowed behind a generic toast.
  console.error(error)
  return fallback
}
