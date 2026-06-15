// @vitest-environment happy-dom
import { vi } from 'vitest'
import { downloadBlobDirectly, extractFilenameFromContentDispositionHeader } from './download'

describe('downloadBlobDirectly', () => {
  let anchor: { href: string; download: string; target: string; click: ReturnType<typeof vi.fn> }
  let createObjectURL: ReturnType<typeof vi.fn>
  let revokeObjectURL: ReturnType<typeof vi.fn>

  beforeEach(() => {
    anchor = { href: '', download: '', target: '', click: vi.fn() }
    vi.spyOn(document, 'createElement').mockReturnValue(anchor as unknown as HTMLAnchorElement)
    createObjectURL = vi.fn(() => 'blob:mock-url')
    revokeObjectURL = vi.fn()
    URL.createObjectURL = createObjectURL as unknown as typeof URL.createObjectURL
    URL.revokeObjectURL = revokeObjectURL as unknown as typeof URL.revokeObjectURL
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('points the anchor at the object URL, downloads, and revokes', () => {
    const blob = new Blob(['hi'], { type: 'text/plain' })

    downloadBlobDirectly(blob, 'report.csv')

    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(anchor.href).toBe('blob:mock-url')
    expect(anchor.download).toBe('report.csv')
    expect(anchor.target).toBe('_blank')
    expect(anchor.click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
    expect(console.warn).not.toHaveBeenCalled()
  })

  it('warns and leaves the download attribute unset when no filename is given', () => {
    downloadBlobDirectly(new Blob(['hi']))

    expect(console.warn).toHaveBeenCalledOnce()
    expect(anchor.download).toBe('') // never assigned
    expect(anchor.click).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url')
  })
})

describe('extractFilenameFromContentDispositionHeader', () => {
  it('returns undefined for null, undefined, or empty input', () => {
    expect(extractFilenameFromContentDispositionHeader(null)).toBeUndefined()
    expect(extractFilenameFromContentDispositionHeader(undefined)).toBeUndefined()
    expect(extractFilenameFromContentDispositionHeader('')).toBeUndefined()
  })

  it('decodes an RFC 5987 UTF-8 filename*', () => {
    expect(
      extractFilenameFromContentDispositionHeader("attachment; filename*=UTF-8''%E2%82%AC%20report.pdf"),
    ).toBe('€ report.pdf')
  })

  it('reads a standard quoted filename', () => {
    expect(extractFilenameFromContentDispositionHeader('attachment; filename="report.pdf"')).toBe('report.pdf')
  })

  it('reads a standard unquoted filename', () => {
    expect(extractFilenameFromContentDispositionHeader('attachment; filename=report.pdf')).toBe('report.pdf')
  })

  it('prefers the UTF-8 filename* over the standard filename', () => {
    expect(
      extractFilenameFromContentDispositionHeader("attachment; filename=\"plain.pdf\"; filename*=UTF-8''fancy.pdf"),
    ).toBe('fancy.pdf')
  })

  it('returns undefined when no filename token is present', () => {
    expect(extractFilenameFromContentDispositionHeader('attachment')).toBeUndefined()
    expect(extractFilenameFromContentDispositionHeader('inline')).toBeUndefined()
  })

  it('warns and returns undefined when the standard filename cannot be decoded', () => {
    expect(extractFilenameFromContentDispositionHeader('attachment; filename="%E0%A4%A.pdf"')).toBeUndefined()
    expect(console.warn).toHaveBeenCalledWith('Failed to decode standard filename', expect.anything())
  })

  it('warns when the UTF-8 filename* cannot be decoded', () => {
    extractFilenameFromContentDispositionHeader("attachment; filename*=UTF-8''%E0%A4%A")
    expect(console.warn).toHaveBeenCalledWith('Failed to decode UTF-8 filename', expect.anything())
  })

  beforeEach(() => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })
})
