import { config } from './config';

export const API_BASE = config.apiBase;

export type ApiResponse<T = unknown> =
  | { ok: true; data: T }
  | { ok: false; message: string };

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string | null,
): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = token;
  }

  try {
    const res = await fetch(`${API_BASE}${path}`, { ...options, headers });
    const json = await res.json();

    if (!res.ok) {
      return { ok: false, message: json?.message ?? `HTTP ${res.status}` };
    }
    return { ok: true, data: json as T };
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : 'Network error' };
  }
}

export const api = {
  get: <T>(path: string, token?: string | null) =>
    request<T>(path, { method: 'GET' }, token),

  post: <T>(path: string, body: unknown, token?: string | null) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }, token),
};
