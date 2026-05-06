export interface BuildInfo {
  commit: string
  version: string
}

export async function useCommitInfo(): Promise<BuildInfo> {
  try {
    const response = await fetch('/commit.json')
    if (!response.ok) return { commit: 'unknown', version: 'unknown' }
    const data = await response.json()
    return {
      commit: data.commit ?? 'unknown',
      version: data.version ?? 'unknown',
    }
  } catch {
    return { commit: 'unknown', version: 'unknown' }
  }
}
