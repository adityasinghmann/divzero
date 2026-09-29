import { useEffect, useState } from 'react';
import Navbar from '../components/Navbar';
import { api } from '../lib/api';
import { useAuth } from '../context/auth';

interface LeaderboardEntry {
  rank: number;
  userId: string;
  name: string;
  username: string;
  problemsSolved: number;
  totalSubmissions: number;
  totalExecutionTimeMs: number;
}

export default function Leaderboard() {
  const { token } = useAuth();
  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<LeaderboardEntry[]>('/leaderboard', token).then((res) => {
      if (res.ok) setEntries(res.data);
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
          <h1 className="font-mono text-lg font-bold tracking-tight">LEADERBOARD</h1>
          <span className="font-mono text-xs text-white/30">
            {!loading && `${entries.length} competitor${entries.length !== 1 ? 's' : ''}`}
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
          <div className="border border-white/20 overflow-x-auto">
            {/* Header row */}
            <div className="grid grid-cols-[3rem_1fr_1fr_7rem_7rem_8rem] border-b border-white/20 bg-white/5 min-w-[52rem]">
              <div className="font-mono text-xs text-white/30 px-4 py-2 border-r border-white/20">#</div>
              <div className="font-mono text-xs text-white/30 px-4 py-2 border-r border-white/20">NAME</div>
              <div className="font-mono text-xs text-white/30 px-4 py-2 border-r border-white/20">USERNAME</div>
              <div className="font-mono text-xs text-white/30 px-4 py-2 border-r border-white/20 text-right">SOLVED</div>
              <div className="font-mono text-xs text-white/30 px-4 py-2 border-r border-white/20 text-right">SUBMITS</div>
              <div className="font-mono text-xs text-white/30 px-4 py-2 text-right">TIME</div>
            </div>

            {entries.length === 0 ? (
              <div className="px-4 py-8 text-center font-mono text-xs text-white/20">
                NO RANKED COMPETITORS YET
              </div>
            ) : (
              entries.map((e) => (
                <div
                  key={e.userId}
                  className="grid grid-cols-[3rem_1fr_1fr_7rem_7rem_8rem] border-b border-white/10 last:border-b-0 hover:bg-white/5 transition-colors min-w-[52rem]"
                >
                  <div className="font-mono text-xs text-white/30 px-4 py-3 border-r border-white/10 flex items-center">
                    {e.rank}
                  </div>
                  <div className="font-mono text-sm text-white px-4 py-3 border-r border-white/10 flex items-center truncate">
                    {e.name}
                  </div>
                  <div className="font-mono text-sm text-white/60 px-4 py-3 border-r border-white/10 flex items-center truncate">
                    {e.username}
                  </div>
                  <div className="font-mono text-sm font-bold px-4 py-3 border-r border-white/10 flex items-center justify-end">
                    {e.problemsSolved}
                  </div>
                  <div className="font-mono text-sm text-white/50 px-4 py-3 border-r border-white/10 flex items-center justify-end">
                    {e.totalSubmissions}
                  </div>
                  <div className="font-mono text-sm text-white/50 px-4 py-3 flex items-center justify-end">
                    {formatDuration(e.totalExecutionTimeMs)}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {!loading && !error && entries.length > 0 && (
          <p className="font-mono text-[10px] text-white/25 mt-3">
            RANKED BY DISTINCT PROBLEMS SOLVED, THEN BY BEST ACCEPTED RUN TIME PER PROBLEM.
          </p>
        )}
      </main>
    </div>
  );
}

function formatDuration(ms: number): string {
  if (ms <= 0) return '—';
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}
