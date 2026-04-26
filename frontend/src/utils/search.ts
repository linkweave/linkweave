export function parseSearchQuery(raw: string): string[] {
  const terms: string[] = []
  let current = ''
  let inQuotes = false

  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i]

    if (ch === "'") {
      if (inQuotes) {
        inQuotes = false
      } else {
        inQuotes = true
      }
      continue
    }

    if (ch === ' ' && !inQuotes) {
      if (current.length >= 2) {
        terms.push(current)
      }
      current = ''
      continue
    }

    current += ch
  }

  if (current.length >= 2) {
    terms.push(current)
  }

  return terms
}

export function bookmarkMatchesTerms(
  bookmark: {
    data: {
      title?: string | null
      url?: string | null
      description?: string | null
      tagIds?: Set<string> | null
    }
  },
  terms: string[],
  tagNamesById: Map<string, string>,
): boolean {
  for (const term of terms) {
    const t = term.toLowerCase()
    let matched = false

    if (bookmark.data.title?.toLowerCase().includes(t)) matched = true
    if (!matched && bookmark.data.url?.toLowerCase().includes(t)) matched = true
    if (!matched && bookmark.data.description?.toLowerCase().includes(t)) matched = true

    if (!matched && bookmark.data.tagIds) {
      for (const tagId of bookmark.data.tagIds) {
        const tagName = tagNamesById.get(tagId)
        if (tagName?.includes(t)) {
          matched = true
          break
        }
      }
    }

    if (!matched) return false
  }

  return true
}
