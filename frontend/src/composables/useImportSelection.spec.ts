// @vitest-environment happy-dom
import type { ImportNodeJson } from '@/api/generated'
import { ImportNodeType } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useImportSelection } from './useImportSelection'

function bm(id: string, url: string, name = id, duplicate = false): ImportNodeJson {
  return { id, type: ImportNodeType.Bookmark, name, url, duplicate }
}

function folder(id: string, children: ImportNodeJson[], name = id): ImportNodeJson {
  return { id, type: ImportNodeType.Folder, name, duplicate: false, children }
}

// Work[ a(example.com), Sub[ b(example.org) ] ]  +  root c(root.com)
function sampleTree(): ImportNodeJson[] {
  return [
    folder('f1', [bm('a', 'https://example.com'), folder('f2', [bm('b', 'https://example.org')])]),
    bm('c', 'https://root.com'),
  ]
}

// Same tree, but bookmark 'a' is flagged as a duplicate by the server.
function treeWithDuplicateA(): ImportNodeJson[] {
  return [
    folder('f1', [
      bm('a', 'https://example.com', 'a', true),
      folder('f2', [bm('b', 'https://example.org')]),
    ]),
    bm('c', 'https://root.com'),
  ]
}

describe('useImportSelection', () => {
  it('pre-selects everything when there are no duplicates', () => {
    const m = useImportSelection(ref(sampleTree()))
    expect(m.selectedCount.value).toBe(3)
    expect(m.totalBookmarks.value).toBe(3)
  })

  it('pre-deselects server-flagged duplicates', () => {
    const m = useImportSelection(ref(treeWithDuplicateA()))
    expect(m.isSelected('a')).toBe(false)
    expect(m.isSelected('b')).toBe(true)
    expect(m.duplicateCount.value).toBe(1)
    expect(m.selectedCount.value).toBe(2)
  })

  it('derives tri-state folder state', () => {
    const tree = ref(sampleTree())
    const m = useImportSelection(tree)
    expect(m.folderState(tree.value[0]!)).toBe('all')
    m.toggleBookmark('a')
    expect(m.folderState(tree.value[0]!)).toBe('some')
    m.toggleBookmark('b')
    expect(m.folderState(tree.value[0]!)).toBe('none')
  })

  it('toggles a whole subtree', () => {
    const tree = ref(sampleTree())
    const m = useImportSelection(tree)
    m.toggleFolder(tree.value[0]!) // all selected -> deselect subtree
    expect(m.isSelected('a')).toBe(false)
    expect(m.isSelected('b')).toBe(false)
    expect(m.isSelected('c')).toBe(true)
    m.toggleFolder(tree.value[0]!) // none selected -> select subtree
    expect(m.isSelected('a')).toBe(true)
    expect(m.isSelected('b')).toBe(true)
  })

  it('toggling skip re-includes duplicates', () => {
    const m = useImportSelection(ref(treeWithDuplicateA()))
    expect(m.isSelected('a')).toBe(false)
    m.setSkipDuplicates(false)
    expect(m.isSelected('a')).toBe(true)
    m.setSkipDuplicates(true)
    expect(m.isSelected('a')).toBe(false)
  })

  it('prunes the selected tree, dropping empty folders', () => {
    const tree = ref(sampleTree())
    const m = useImportSelection(tree)
    m.clear()
    m.toggleBookmark('b') // only the deeply-nested bookmark
    const pruned = m.selectedTree()
    // f1 -> f2 -> b retained; root bookmark c dropped
    expect(pruned).toHaveLength(1)
    expect(pruned[0]!.id).toBe('f1')
    expect(pruned[0]!.children).toHaveLength(1)
    expect(pruned[0]!.children![0]!.id).toBe('f2')
    expect(pruned[0]!.children![0]!.children![0]!.id).toBe('b')
  })
})
