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
