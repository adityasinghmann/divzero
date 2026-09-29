import { useState, useEffect, useCallback, useMemo, type ReactNode } from 'react';
import { api, setUnauthorizedHandler } from '../lib/api';
import { AuthContext, type AuthUser } from './auth';

const TOKEN_KEY = 'dz_token';

// localStorage can throw (private mode, blocked site data); treat that as "no stored session".
function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function storeToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to do: the session just won't survive a reload.
  }
}

// ─── JWT decode (no library needed – payload is base64url) ────────────────────

function decodeToken(token: string): { id: string; name: string; username: string } | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    // The payload is UTF-8; atob alone yields Latin-1 and would garble non-ASCII names.
    const bytes = Uint8Array.from(atob(payload), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

interface MeResponse {
  id: string;
  name: string;
  username: string;
  role: 'ADMIN' | 'USER';
}

// The server is the source of truth for identity and role: /me validates the token and
// reports the role without exposing any other user's data.
async function resolveUser(rawToken: string): Promise<AuthUser | null> {
  const res = await api.get<MeResponse>('/me', rawToken);
  if (res.ok) {
    const { id, name, username, role } = res.data;
    return { id, name, username, isAdmin: role === 'ADMIN' };
  }
  // Rejected token (expired, bad signature, deleted user): drop the session.
  if (res.status === 401 || res.status === 403) return null;
  // API unreachable or failing: keep the session from the token's own claims, without admin
  // rights. Every request is still authorized server-side, so this grants nothing.
  const decoded = decodeToken(rawToken);
  if (!decoded) return null;
  return { id: decoded.id, name: decoded.name, username: decoded.username, isAdmin: false };
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  // Only a stored token needs checking; with none there is nothing to wait for.
  const [isLoading, setIsLoading] = useState(() => readStoredToken() !== null);

  const logout = useCallback(() => {
    storeToken(null);
    setToken(null);
    setUser(null);
  }, []);

  // Any 401 on an authenticated request ends the session; the route guards then send the
  // user to /login instead of every page showing "Authentication required".
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // Rehydrate from localStorage on mount
  useEffect(() => {
    const stored = readStoredToken();
    if (!stored) return;
    let cancelled = false;
    resolveUser(stored).then((u) => {
      if (cancelled) return;
      if (u) {
        setToken(stored);
        setUser(u);
      } else {
        storeToken(null);
      }
      setIsLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (rawToken: string) => {
    const u = await resolveUser(rawToken);
    if (!u) throw new Error('Invalid token received from server');
    storeToken(rawToken);
    setToken(rawToken);
    setUser(u);
  }, []);

  const value = useMemo(
    () => ({ user, token, isLoading, login, logout }),
    [user, token, isLoading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
