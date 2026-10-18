import type { Rule } from 'antd/es/form'

/** The same rules the backend enforces on registration and on resetting a password. */
export const passwordRules: Rule[] = [
  { required: true, message: 'Password is required' },
  { min: 12, message: 'Password must be at least 12 characters' },
  { pattern: /[a-z]/, message: 'Password must include a lowercase letter' },
  { pattern: /[A-Z]/, message: 'Password must include an uppercase letter' },
  { pattern: /\d/, message: 'Password must include a number' },
  { pattern: /[^A-Za-z0-9]/, message: 'Password must include a special character' },
  { pattern: /^\S+$/, message: 'Password cannot contain spaces' },
]
