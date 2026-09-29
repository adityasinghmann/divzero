import { useEffect, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/auth';
import { api } from '../lib/api';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isOnline, setIsOnline] = useState<boolean | null>(null);

  useEffect(() => {
    const checkHealth = async () => {
      try {
        const res = await api.get<{ status: string }>('/health');
        setIsOnline(res.ok && res.data.status === 'healthy');
      } catch {
        setIsOnline(false);
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, 10000);
    return () => clearInterval(interval);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="border-b border-white/20 bg-black">
      <nav className="max-w-7xl mx-auto px-4 h-12 flex items-center justify-between">
        {/* Brand & Health */}
        <div className="flex items-center gap-4">
          <Link
            to="/"
            className="font-mono text-base font-bold tracking-tight text-white hover:text-white/70 transition-colors"
          >
            DIV<span className="text-white/40">/</span>ZERO
          </Link>
          <div className="flex items-center gap-1.5 font-mono text-[9px] tracking-widest text-white/40 select-none border border-white/10 px-2 py-0.5 rounded bg-white/5">
            <span
              className={`h-1.5 w-1.5 rounded-full transition-all duration-300 ${
                isOnline === null
                  ? 'bg-yellow-500 animate-pulse'
                  : isOnline
                  ? 'bg-green-500 shadow-[0_0_8px_#22c55e]'
                  : 'bg-red-500 shadow-[0_0_8px_#ef4444] animate-pulse'
              }`}
            />
            {isOnline === null ? 'SYNCING' : isOnline ? 'API ONLINE' : 'API OFFLINE'}
          </div>
        </div>

        {/* Links */}
        <div className="flex items-center gap-6 text-sm font-mono">
          {user ? (
            <>
              <NavLink
                to="/problems"
                className={({ isActive }) =>
                  isActive
                    ? 'text-white border-b border-white pb-0.5'
                    : 'text-white/50 hover:text-white transition-colors'
                }
              >
                problems
              </NavLink>

              <NavLink
                to="/leaderboard"
                className={({ isActive }) =>
                  isActive
                    ? 'text-white border-b border-white pb-0.5'
                    : 'text-white/50 hover:text-white transition-colors'
                }
              >
                leaderboard
              </NavLink>

              {user.isAdmin && (
                <NavLink
                  to="/admin"
                  className={({ isActive }) =>
                    isActive
                      ? 'text-white border-b border-white pb-0.5'
                      : 'text-white/50 hover:text-white transition-colors'
                  }
                >
                  admin
                </NavLink>
              )}

              <span className="text-white/30">|</span>

              <span className="text-white/40">{user.username}</span>

              <button
                onClick={handleLogout}
                className="text-white/50 hover:text-white transition-colors cursor-pointer"
              >
                logout
              </button>
            </>
          ) : (
            <>
              <NavLink
                to="/login"
                className={({ isActive }) =>
                  isActive
                    ? 'text-white border-b border-white pb-0.5'
                    : 'text-white/50 hover:text-white transition-colors'
                }
              >
                login
              </NavLink>
              <NavLink
                to="/signup"
                className={({ isActive }) =>
                  isActive
                    ? 'text-white border-b border-white pb-0.5'
                    : 'text-white/50 hover:text-white transition-colors'
                }
              >
                signup
              </NavLink>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
