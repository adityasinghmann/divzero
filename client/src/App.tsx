import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';

import Landing from './pages/Landing';
import Login from './pages/Login';
import Signup from './pages/Signup';
import Problems from './pages/Problems';
import Problem from './pages/Problem';
import Admin from './pages/Admin';

// ─── Route Guards ─────────────────────────────────────────────────────────────

/**
 * Redirects unauthenticated users to /login.
 * Shows nothing while auth state is still loading from localStorage.
 */
function RequireAuth() {
  const { user, isLoading } = useAuth();
  if (isLoading) return null;
  return user ? <Outlet /> : <Navigate to="/login" replace />;
}

/**
 * Redirects non-admin users to /problems.
 * Admin status is resolved during the login/rehydration flow.
 */
function RequireAdmin() {
  const { user, isLoading } = useAuth();
  if (isLoading) return null;
  if (!user) return <Navigate to="/login" replace />;
  return user.isAdmin ? <Outlet /> : <Navigate to="/problems" replace />;
}

/**
 * Redirects already-authenticated users away from auth pages (login / signup).
 */
function RedirectIfAuthed() {
  const { user, isLoading } = useAuth();
  if (isLoading) return null;
  return user ? <Navigate to="/problems" replace /> : <Outlet />;
}

// ─── Router ───────────────────────────────────────────────────────────────────

function AppRoutes() {
  return (
    <Routes>
      {/* Public */}
      <Route path="/" element={<Landing />} />

      {/* Auth pages – redirect logged-in users away */}
      <Route element={<RedirectIfAuthed />}>
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />
      </Route>

      {/* Protected – any logged-in user */}
      <Route element={<RequireAuth />}>
        <Route path="/problems" element={<Problems />} />
        <Route path="/problem/:id" element={<Problem />} />
      </Route>

      {/* Protected – admin only */}
      <Route element={<RequireAdmin />}>
        <Route path="/admin" element={<Admin />} />
      </Route>

      {/* Fallback */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

// ─── Root ─────────────────────────────────────────────────────────────────────

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
