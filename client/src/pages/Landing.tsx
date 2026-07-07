import { Link } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';

const STATS = [
  { label: 'NO TRICKS', desc: 'Submit C++ code directly against test cases.' },
  { label: 'INSTANT VERDICT', desc: 'Know within seconds if your solution is correct.' },
  { label: 'CLEAN INTERFACE', desc: 'No ads, no fluff. Just the problem and the editor.' },
];

export default function Landing() {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <Navbar />

      {/* Hero */}
      <main className="flex-1 max-w-7xl mx-auto px-4 py-20 w-full">
        <div className="border border-white/20 p-10 mb-12">
          <p className="font-mono text-xs text-white/40 tracking-widest mb-4">
            COMPETITIVE PROGRAMMING PLATFORM
          </p>
          <h1 className="font-mono text-5xl font-bold tracking-tight mb-4">
            DIV<span className="text-white/30">/</span>ZERO
          </h1>
          <p className="text-white/50 text-sm max-w-md mb-8 leading-relaxed">
            A no-frills arena for competitive programming. Read the problem,
            write the solution, submit.
          </p>

          <div className="flex gap-3">
            {user ? (
              <Link
                to="/problems"
                className="font-mono text-sm bg-white text-black px-5 py-2 hover:bg-white/80 transition-colors"
              >
                VIEW PROBLEMS →
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="font-mono text-sm bg-white text-black px-5 py-2 hover:bg-white/80 transition-colors"
                >
                  LOGIN →
                </Link>
                <Link
                  to="/signup"
                  className="font-mono text-sm border border-white/40 text-white px-5 py-2 hover:border-white transition-colors"
                >
                  SIGN UP
                </Link>
              </>
            )}
          </div>
        </div>

        {/* Feature grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-px bg-white/10">
          {STATS.map((s) => (
            <div key={s.label} className="bg-black p-6">
              <p className="font-mono text-xs font-bold tracking-widest text-white mb-2">
                {s.label}
              </p>
              <p className="text-white/40 text-xs leading-relaxed">{s.desc}</p>
            </div>
          ))}
        </div>
      </main>


    </div>
  );
}

