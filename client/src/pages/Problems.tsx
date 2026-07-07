import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { api } from '../lib/api';
import { useAuth } from '../context/AuthContext';

interface Problem {
  id: string;
  title: string;
  description: string;
}

export default function Problems() {
  const { token } = useAuth();
  const [problems, setProblems] = useState<Problem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<Problem[]>('/view-problems', token).then((res) => {
      if (res.ok) setProblems(res.data);
      else setError(res.message);
      setLoading(false);
    });
  }, [token]);

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-7xl mx-auto px-4 py-10 w-full">
        {/* Page header */}
        <div className="flex items-baseline justify-between mb-6">
          <h1 className="font-mono text-lg font-bold tracking-tight">PROBLEM SET</h1>
          <span className="font-mono text-xs text-white/30">
            {!loading && `${problems.length} problem${problems.length !== 1 ? 's' : ''}`}
          </span>
        </div>

        {/* State: loading */}
        {loading && (
          <p className="font-mono text-xs text-white/30 animate-pulse">LOADING...</p>
        )}

        {/* State: error */}
        {error && (
          <p className="font-mono text-xs text-red-400 border border-red-400/30 bg-red-400/5 px-3 py-2">
            ERROR: {error}
          </p>
        )}

        {/* Table */}
        {!loading && !error && (
          <div className="border border-white/20">
            {/* Header row */}
            <div className="grid grid-cols-[3rem_1fr] border-b border-white/20 bg-white/5">
              <div className="font-mono text-xs text-white/30 px-4 py-2 border-r border-white/20">#</div>
              <div className="font-mono text-xs text-white/30 px-4 py-2">TITLE</div>
            </div>

            {problems.length === 0 ? (
              <div className="px-4 py-8 text-center font-mono text-xs text-white/20">
                NO PROBLEMS AVAILABLE
              </div>
            ) : (
              problems.map((p, i) => (
                <div
                  key={p.id}
                  className="grid grid-cols-[3rem_1fr] border-b border-white/10 last:border-b-0 hover:bg-white/5 transition-colors"
                >
                  <div className="font-mono text-xs text-white/30 px-4 py-3 border-r border-white/10 flex items-center">
                    {i + 1}
                  </div>
                  <div className="px-4 py-3 flex items-center">
                    <Link
                      to={`/problem/${p.id}`}
                      className="font-mono text-sm text-white hover:text-white/60 transition-colors"
                    >
                      {p.title}
                    </Link>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </main>
    </div>
  );
}

