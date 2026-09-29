import { config } from './config';

export const API_BASE = config.apiBase;

export type ApiResponse<T = unknown> =
  | { ok: true; data: T; status: number }
  | { ok: false; message: string; status: number };

let unauthorizedHandler: (() => void) | null = null;

/**
 * Registered by AuthProvider. A 401 on a request that carried a token means the session has
 * expired or been revoked, so the app should log out rather than show an error on every page.
 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

function errorMessage(body: unknown, status: number): string {
  if (body && typeof body === 'object') {
    const { message, error, errors } = body as { message?: unknown; error?: unknown; errors?: unknown };
    const base = typeof message === 'string' ? message : typeof error === 'string' ? error : null;
    // Validation failures carry per-field reasons; show them instead of a bare "Validation failed".
    if (errors && typeof errors === 'object') {
      const details = Object.entries(errors as Record<string, unknown>)
        .map(([field, reason]) => `${field} ${String(reason)}`)
        .join('; ');
      if (details) return base ? `${base}: ${details}` : details;
    }
    if (base) return base;
  }
  return `HTTP ${status}`;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string | null,
): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = { ...(options.headers as Record<string, string>) };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers['Authorization'] = token;
  }

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch (err) {
    return { ok: false, message: err instanceof Error ? err.message : 'Network error', status: 0 };
  }

  // Parsed defensively: an empty body or a proxy's HTML error page must not turn a real HTTP
  // status into a JSON parse error.
  const text = await res.text().catch(() => '');
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }

  if (!res.ok) {
    if (res.status === 401 && token) {
      unauthorizedHandler?.();
    }
    return { ok: false, message: errorMessage(body, res.status), status: res.status };
  }
  // status is surfaced so callers can distinguish 200 (verdict inline) from 202 (still judging).
  return { ok: true, data: body as T, status: res.status };
}

export const api = {
  get: <T>(path: string, token?: string | null) =>
    request<T>(path, { method: 'GET' }, token),

  post: <T>(path: string, body: unknown, token?: string | null) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }, token),
};
