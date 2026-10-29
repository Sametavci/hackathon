import type { HTMLAttributes } from 'react';
export default function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`min-w-0 rounded-2xl border border-line bg-white p-4 sm:p-5 ${className}`} {...props} />;
}
