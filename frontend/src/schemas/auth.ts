import { z } from 'zod'
import { v } from './validation-messages'

export const loginSchema = z.object({
  email: z.string().min(1, v.required('Email')).email(v.email()),
  password: z.string().min(1, v.required('Password')),
})

export type LoginInput = z.infer<typeof loginSchema>

export const registrationSchema = z
  .object({
    vorname: z.string().min(1, v.required('First name')).trim(),
    nachname: z.string().min(1, v.required('Last name')).trim(),
    email: z.string().min(1, v.required('Email')).email(v.email()),
    password: z.string().min(1, v.required('Password')).min(8, v.minLength('Password', 8)),
    confirmPassword: z.string().min(1, v.required('Confirm password')),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: v.passwordMismatch(),
    path: ['confirmPassword'],
  })

export type RegistrationInput = z.infer<typeof registrationSchema>
