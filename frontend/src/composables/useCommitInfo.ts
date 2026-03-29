export async function useCommitInfo() {
  try {
    const response = await fetch('/commit.json')
    const data = await response.json()
    return data.commit
  } catch (error) {
    console.error('Failed to fetch commit info:', error)
    return 'unknown'
  }
}
