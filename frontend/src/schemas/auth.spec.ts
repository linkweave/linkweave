import { describe, it, expect } from 'vitest'
import { loginSchema, registrationSchema } from './auth'
import { standaloneSchemaTranslator as t } from '@/test-utils/schema'

describe('loginSchema', () => {
  it('should accept valid login data', () => {
    expect(loginSchema(t).parse({ email: 'user@example.com', password: 'secret' })).toEqual({
      email: 'user@example.com',
      password: 'secret',
    })
  })

  it('should reject missing email', () => {
    expect(() => loginSchema(t).parse({ password: 'secret' })).toThrow()
  })

  it('should reject invalid email', () => {
    expect(() => loginSchema(t).parse({ email: 'not-email', password: 'secret' })).toThrow()
  })

  it('should reject missing password', () => {
    expect(() => loginSchema(t).parse({ email: 'user@example.com', password: '' })).toThrow()
  })
})

describe('registrationSchema', () => {
  const valid = {
    vorname: 'John',
    nachname: 'Doe',
    email: 'john@example.com',
    password: 'secret123',
    confirmPassword: 'secret123',
  }

  it('should accept valid registration data', () => {
    expect(registrationSchema(t).parse(valid)).toEqual(valid)
  })

  it('should reject mismatched passwords', () => {
    expect(() =>
      registrationSchema(t).parse({ ...valid, confirmPassword: 'different' }),
    ).toThrow()
  })

  it('should reject empty password', () => {
    const result = registrationSchema(t).safeParse({ ...valid, password: '', confirmPassword: '' })
    expect(result.success).toBe(false)
    if (!result.success) {
      const issue = result.error.issues.find(i => i.path[0] === 'password')
      expect(issue?.message).toBe('Password is required')
    }
  })

  it('should reject short password', () => {
    expect(() =>
      registrationSchema(t).parse({ ...valid, password: 'short', confirmPassword: 'short' }),
    ).toThrow()
  })

  it('should reject missing vorname', () => {
    expect(() => registrationSchema(t).parse({ ...valid, vorname: '' })).toThrow()
  })

  it('should reject missing nachname', () => {
    expect(() => registrationSchema(t).parse({ ...valid, nachname: '' })).toThrow()
  })

  it('should reject invalid email', () => {
    expect(() => registrationSchema(t).parse({ ...valid, email: 'bad' })).toThrow()
  })

  it('should trim vorname and nachname', () => {
    const result = registrationSchema(t).parse({
      ...valid,
      vorname: '  John  ',
      nachname: '  Doe  ',
    })
    expect(result.vorname).toBe('John')
    expect(result.nachname).toBe('Doe')
  })
})
