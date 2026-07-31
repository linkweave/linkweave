// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { preventImplicitSubmit } from './implicitSubmit'

/**
 * Builds the shape of the bookmark form: the guard sits on the `<form>` exactly
 * as the template binds it, so each case dispatches a real bubbling keydown
 * from a child and asserts whether the submit default survived.
 */
function form() {
  const el = document.createElement('form')
  el.innerHTML = `
    <input id="url" type="url" />
    <input id="title" type="text" />
    <textarea id="description"></textarea>
    <input id="prop-text" type="text" />
    <input id="prop-number" type="number" />
    <button id="manage-rules" type="button">Manage rules</button>
    <span id="props-toggle" role="button">Properties</span>
  `
  el.addEventListener('keydown', (e) => {
    if ((e as KeyboardEvent).key === 'Enter') preventImplicitSubmit(e as KeyboardEvent)
  })
  document.body.appendChild(el)
  return el
}

/** @returns whether the browser's implicit submission was cancelled. */
function pressEnter(id: string, init?: KeyboardEventInit): boolean {
  const event = new KeyboardEvent('keydown', {
    key: 'Enter',
    bubbles: true,
    cancelable: true,
    ...init,
  })
  document.querySelector(`#${id}`)!.dispatchEvent(event)
  return event.defaultPrevented
}

describe('preventImplicitSubmit', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('blocks Enter in the url and title fields', () => {
    // ARRANGE
    form()

    // ACT / ASSERT
    expect(pressEnter('url')).toBe(true)
    expect(pressEnter('title')).toBe(true)
  })

  it('blocks Enter in the property inputs — the fields the per-input guard missed', () => {
    // ARRANGE
    form()

    // ACT / ASSERT
    expect(pressEnter('prop-text')).toBe(true)
    expect(pressEnter('prop-number')).toBe(true)
  })

  it('leaves Enter alone in a textarea so it still inserts a newline', () => {
    // ARRANGE
    form()

    // ACT / ASSERT
    expect(pressEnter('description')).toBe(false)
  })

  it('leaves Enter alone on buttons and role=button so they stay keyboard-activatable', () => {
    // ARRANGE
    form()

    // ACT / ASSERT
    expect(pressEnter('manage-rules')).toBe(false)
    expect(pressEnter('props-toggle')).toBe(false)
  })

  it('leaves Enter alone while an IME candidate is being composed', () => {
    // ARRANGE
    form()

    // ACT / ASSERT
    expect(pressEnter('title', { isComposing: true })).toBe(false)
  })

  it('only cancels the default — components that own Enter still see it', () => {
    // ARRANGE — TagCombobox picks the highlighted tag on Enter and must keep firing
    const el = form()
    const onInputEnter = vi.fn()
    document.querySelector('#title')!.addEventListener('keydown', onInputEnter)

    // ACT
    const prevented = pressEnter('title')

    // ASSERT
    expect(prevented).toBe(true)
    expect(onInputEnter).toHaveBeenCalledOnce()
    expect(el.isConnected).toBe(true)
  })
})
