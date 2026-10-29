import type { ButtonHTMLAttributes } from 'react';
export const buttonClasses = 'inline-flex min-h-11 items-center justify-center gap-2 rounded-2xl border border-transparent bg-brand px-5 py-3 text-center text-sm font-bold text-white transition-colors hover:bg-brand-dark focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ink disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none';
export default function Button({ className = '', variant = 'primary', type = 'button', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' }) {
  return <button type={type} className={`${buttonClasses} ${variant === 'secondary' ? '!border-line !bg-white !text-ink hover:!bg-brand-soft' : variant === 'danger' ? '!bg-red-800 !text-white hover:!bg-red-900' : ''} ${className}`} {...props} />;
}
