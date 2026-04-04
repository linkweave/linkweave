export async function useCommitInfo() {
  try {
    const response = await fetch('/commit.json')
    if (!response.ok) {return 'unknown'}
    const data = await response.json()
    return data.commit
  } catch (error) {
    console.warn('Failed to fetch commit info:', error)
    return 'unknown'
  }
}
