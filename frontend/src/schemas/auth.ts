import { z } from 'zod'
import type { TFunction } from './types'

export const loginSchema = (t: TFunction) =>
  z.object({
    email: z.string().min(1, t('validation.required', { field: 'Email' })).email(t('validation.email')),
    password: z.string().min(1, t('validation.required', { field: 'Password' })),
  })

export type LoginInput = z.infer<ReturnType<typeof loginSchema>>

export const registrationSchema = (t: TFunction) =>
  z
    .object({
      vorname: z.string().min(1, t('validation.required', { field: 'First name' })).trim(),
      nachname: z.string().min(1, t('validation.required', { field: 'Last name' })).trim(),
      email: z.string().min(1, t('validation.required', { field: 'Email' })).email(t('validation.email')),
      password: z
        .string()
        .min(1, t('validation.required', { field: 'Password' }))
        .min(8, t('validation.minLength', { field: 'Password', min: 8 })),
      confirmPassword: z.string().min(1, t('validation.required', { field: 'Confirm password' })),
    })
    .refine((data) => data.password === data.confirmPassword, {
      message: t('validation.passwordMismatch'),
      path: ['confirmPassword'],
    })

export type RegistrationInput = z.infer<ReturnType<typeof registrationSchema>>
