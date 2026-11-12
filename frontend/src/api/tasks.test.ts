import { describe, expect, it } from 'vitest'
import { validateTask } from './tasks'

// Unit tests of the task title rule as mirrored in the UI (requirements.md
// validation limits; TaskCreateRequest.title in openapi.yaml): trimmed,
// 3–150 characters counted as code points, whitespace-only rejected.
// test-plan.md TV-01 to TV-07.
const LENGTH_MESSAGE = 'Title must have between 3 and 150 characters.'

describe('validateTask title rule', () => {
  it('accepts a valid title', () => {
    expect(validateTask('Prepare release notes', '')).toEqual({})
  })

  it.each(['', '   ', '\t\n ', '\u00a0\u00a0\u00a0'])('requires a title that is not whitespace-only (%j)', (title) => {
    expect(validateTask(title, '').title).toBe('Title is required.')
  })

  it.each(['ab', ' ab ', '  ab\t', '😀😀'])('rejects a title below the minimum after trimming (%j)', (title) => {
    expect(validateTask(title, '').title).toBe(LENGTH_MESSAGE)
  })

  it.each(['abc', '  abc  ', '\tabc\n', '😀😀😀'])('accepts a title at the minimum after trimming (%j)', (title) => {
    expect(validateTask(title, '').title).toBeUndefined()
  })

  it.each(['a'.repeat(150), `  ${'a'.repeat(150)}  `, '😀'.repeat(150)])(
    'accepts a title at the maximum (%#)',
    (title) => {
      expect(validateTask(title, '').title).toBeUndefined()
    },
  )

  it.each(['a'.repeat(151), '😀'.repeat(151)])('rejects a title above the maximum (%#)', (title) => {
    expect(validateTask(title, '').title).toBe(LENGTH_MESSAGE)
  })
})
