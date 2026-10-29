import type { HTMLAttributes } from 'react';
export default function Card({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`min-w-0 rounded-3xl border border-line bg-white p-5 shadow-sm sm:p-6 ${className}`} {...props} />;
}
