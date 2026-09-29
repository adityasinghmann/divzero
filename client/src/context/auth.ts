import { createContext, useContext } from 'react';

export interface AuthUser {
  id: string;
  name: string;
  username: string;
  isAdmin: boolean;
}

export interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  isLoading: boolean;
  login: (token: string) => Promise<void>;
  logout: () => void;
}

// Kept apart from the AuthProvider component so that file exports only components, which
// React Fast Refresh requires to hot-reload it.
export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
