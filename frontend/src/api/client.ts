import { clearSession, getToken } from './session';
import type { FieldError } from './types';
import { t } from '../i18n/en';
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details: FieldError[] = []) { super(message); this.name = 'ApiError'; }
}
export interface RequestOptions { method?: 'GET' | 'POST' | 'PUT' | 'DELETE'; body?: unknown; auth?: 'participant' | 'admin'; adminKey?: string }
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' });
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  if (options.auth === 'participant') { const token = getToken(); if (token) headers.set('Authorization', `Bearer ${token}`); }
  if (options.auth === 'admin') headers.set('X-Admin-Key', options.adminKey ?? '');
  let response: Response;
  try { response = await fetch(`/api${path}`, { method: options.method ?? 'GET', headers, body: options.body === undefined ? undefined : JSON.stringify(options.body) }); }
  catch { throw new ApiError(0, 'NETWORK', t('error.network')); }
  if (response.status === 401 && options.auth === 'participant') clearSession();
  const raw = await response.text();
  let payload: unknown;
  try { payload = raw ? JSON.parse(raw) : undefined; }
  catch { throw new ApiError(response.status, 'INVALID_RESPONSE', t('error.response')); }
  if (!response.ok) {
    const envelope = payload as { error?: { code?: string; message?: string; details?: FieldError[] } } | undefined;
    throw new ApiError(response.status, envelope?.error?.code ?? 'HTTP_ERROR', envelope?.error?.message ?? t('error.generic'), envelope?.error?.details ?? []);
  }
  return payload as T;
}
