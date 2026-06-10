interface ParsedUrl {
  host: string
  hostLabels: string[]
  // Labels excluding the TLD (last label) when there are 2+ labels.
  // Avoids false positives for reserved/common TLDs like .test or .dev.
  nonTldLabels: string[]
}

interface AutoTagRule {
  tag: string
  test: (parsed: ParsedUrl) => boolean
}

function parseHost(rawUrl: string): ParsedUrl | null {
  try {
    const host = new URL(rawUrl).hostname.toLowerCase()
    if (!host) return null
    const hostLabels = host.split('.')
    const nonTldLabels =
      hostLabels.length > 1 ? hostLabels.slice(0, -1) : hostLabels
    return { host, hostLabels, nonTldLabels }
  } catch {
    return null
  }
}

function labelMatches(prefix: string): (parsed: ParsedUrl) => boolean {
  const dashed = `${prefix}-`
  return ({ nonTldLabels }) =>
    nonTldLabels.some((label) => label === prefix || label.startsWith(dashed))
}

const BUILT_IN_RULES: AutoTagRule[] = [
  {
    tag: 'local',
    test: ({ host, hostLabels }) =>
      host === 'localhost' ||
      host === '127.0.0.1' ||
      host === '0.0.0.0' ||
      hostLabels[0] === 'local' ||
      (hostLabels[0]?.startsWith('local-') ?? false),
  },
  { tag: 'dev', test: labelMatches('dev') },
  { tag: 'uat', test: labelMatches('uat') },
  {
    tag: 'staging',
    test: ({ nonTldLabels }) =>
      nonTldLabels.some(
        (label) =>
          label === 'staging' ||
          label === 'stg' ||
          label.startsWith('staging-') ||
          label.startsWith('stg-'),
      ),
  },
  { tag: 'test', test: labelMatches('test') },
  { tag: 'qa', test: labelMatches('qa') },
  { tag: 'prod', test: labelMatches('prod') },
]

export function suggestTagNames(
  rawUrl: string,
  rules: AutoTagRule[] = BUILT_IN_RULES,
): string[] {
  const parsed = parseHost(rawUrl)
  if (!parsed) return []
  const seen = new Set<string>()
  const out: string[] = []
  for (const rule of rules) {
    if (!seen.has(rule.tag) && rule.test(parsed)) {
      seen.add(rule.tag)
      out.push(rule.tag)
    }
  }
  return out
}
