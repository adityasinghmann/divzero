import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { api } from '../lib/api';

// ─── Types ────────────────────────────────────────────────────────────────────

export interface AuthUser {
  id: string;
  name: string;
  username: string;
  isAdmin: boolean;
}

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  login: (token: string) => Promise<void>;
  logout: () => void;
}

// ─── Context ──────────────────────────────────────────────────────────────────

const AuthContext = createContext<AuthContextValue | null>(null);

// ─── JWT decode (no library needed – payload is base64url) ────────────────────

function decodeToken(token: string): { id: string; name: string; username: string } | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

// Probe whether the stored token grants admin access by hitting the
// admin-only /view-users endpoint.
async function probeAdmin(token: string): Promise<boolean> {
  const res = await api.get('/view-users', token);
  return res.ok;
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const buildUser = useCallback(async (rawToken: string): Promise<AuthUser | null> => {
    const decoded = decodeToken(rawToken);
    if (!decoded) return null;
    const isAdmin = await probeAdmin(rawToken);
    return { ...decoded, isAdmin };
  }, []);

  // Rehydrate from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem('dz_token');
    if (!stored) {
      setIsLoading(false);
      return;
    }
    buildUser(stored).then((u) => {
      if (u) {
        setToken(stored);
        setUser(u);
      } else {
        localStorage.removeItem('dz_token');
      }
      setIsLoading(false);
    });
  }, [buildUser]);

  const login = useCallback(
    async (rawToken: string) => {
      const u = await buildUser(rawToken);
      if (!u) throw new Error('Invalid token received from server');
      localStorage.setItem('dz_token', rawToken);
      setToken(rawToken);
      setUser(u);
    },
    [buildUser],
  );

  const logout = useCallback(() => {
    localStorage.removeItem('dz_token');
    setToken(null);
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, token, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
