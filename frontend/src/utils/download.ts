/**
 * Saves a `fetch` Response body as a browser download, resolving the filename
 * from the Content-Disposition header and falling back to `fallbackFilename`.
 * Shared by the full- and partial-export flows, which both stream HTML the
 * generated API client cannot handle as a blob.
 */
export async function downloadFromResponse(response: Response, fallbackFilename: string): Promise<void> {
  const contentDisposition = response.headers.get('Content-Disposition')
  const filename = extractFilenameFromContentDispositionHeader(contentDisposition) ?? fallbackFilename
  const blob = await response.blob()
  downloadBlobDirectly(blob, filename)
}

export function downloadBlobDirectly(blob: Blob, filename?: string): void {
  if (!filename) {
    console.warn('filename is not set, the browser will open the file in a new tab')
  }
  const downloadUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = downloadUrl
  if (filename) {
    anchor.download = filename
  }
  anchor.target = '_blank'
  anchor.click()
  URL.revokeObjectURL(downloadUrl)
}

export function extractFilenameFromContentDispositionHeader(contentDisposition: string | null | undefined): string | undefined {
  if (!contentDisposition) {
    return undefined
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;\s]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1])
    } catch (error) {
      console.warn('Failed to decode UTF-8 filename', error)
    }
  }
  const standardMatch = contentDisposition.match(/filename="?([^";\s]+)"?/i)
  if (standardMatch?.[1]) {
    try {
      return decodeURIComponent(standardMatch[1].replace(/['"]/g, ''))
    } catch (error) {
      console.warn('Failed to decode standard filename', error)
    }
  }
  return undefined
}
