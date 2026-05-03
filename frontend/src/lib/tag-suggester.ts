  import { suggestTagNames } from './builtin-tag-rules'

// ---------- Custom (user-defined) rules ----------

export interface CustomRuleInput {
  pattern: string
  /** Comma-separated tag names, e.g. "pr,github". */
  tagNames: string
  enabled: boolean
}

export interface CompiledCustomRule {
  regex: RegExp
  tagNames: string[]
}

/**
 * Compile user-defined rules into runtime form. Disabled or invalid rules are dropped.
 * The full URL string (not just the host) is matched against each regex.
 */
export function compileCustomRules(rules: CustomRuleInput[]): CompiledCustomRule[] {
  const compiled: CompiledCustomRule[] = []
  for (const rule of rules) {
    if (!rule.enabled) continue
    const tagNames = rule.tagNames
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter((s) => s.length > 0)
    if (tagNames.length === 0) continue
    let regex: RegExp
    try {
      regex = new RegExp(rule.pattern)
    } catch {
      // server should reject these, but be defensive on the client too
      console.warn('[auto-tag] dropped rule with invalid regex:', rule.pattern)
      continue
    }
    compiled.push({ regex, tagNames })
  }
  return compiled
}

/**
 * Combined suggestion pipeline: built-in host-based rules first (in order),
 * then user-defined rules in declaration order, deduplicated by tag name.
 */
export function suggestAllTagNames(
  rawUrl: string,
  customRules: CompiledCustomRule[] = [],
): string[] {
  const seen = new Set<string>()
  const out: string[] = []
  for (const name of suggestTagNames(rawUrl)) {
    if (!seen.has(name)) {
      seen.add(name)
      out.push(name)
    }
  }
  if (customRules.length > 0 && rawUrl.length > 0) {
    for (const rule of customRules) {
      if (!rule.regex.test(rawUrl)) continue
      for (const name of rule.tagNames) {
        if (!seen.has(name)) {
          seen.add(name)
          out.push(name)
        }
      }
    }
  }
  return out
}
