import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { api } from '../lib/api';
import Navbar from '../components/Navbar';

interface SignupResponse {
  status: string;
  token: string;
}

export default function Signup() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const res = await api.post<SignupResponse>('/signup', { name, username, password });

    if (!res.ok) {
      setError(res.message);
      setLoading(false);
      return;
    }

    try {
      await login(res.data.token);
      navigate('/problems', { replace: true });
    } catch {
      setError('Received an invalid token from server.');
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <Navbar />

      <main className="flex-1 flex items-center justify-center px-4">
        <div className="w-full max-w-sm">
          {/* Header */}
          <div className="border border-white/20 border-b-0 px-6 pt-6 pb-4">
            <p className="font-mono text-xs text-white/40 tracking-widest mb-1">AUTHENTICATION</p>
            <h2 className="font-mono text-xl font-bold">CREATE ACCOUNT</h2>
          </div>

          {/* Form */}
          <form
            onSubmit={handleSubmit}
            className="border border-white/20 px-6 py-6 flex flex-col gap-5"
          >
            <Field
              label="DISPLAY NAME"
              id="name"
              type="text"
              value={name}
              onChange={setName}
              autoComplete="name"
              required
            />

            <Field
              label="USERNAME"
              id="username"
              type="text"
              value={username}
              onChange={setUsername}
              autoComplete="username"
              required
            />

            <Field
              label="PASSWORD"
              id="password"
              type="password"
              value={password}
              onChange={setPassword}
              autoComplete="new-password"
              required
            />

            {error && (
              <p className="font-mono text-xs text-red-400 border border-red-400/30 bg-red-400/5 px-3 py-2">
                ERROR: {error}
              </p>
            )}

            <button
              type="submit"
              disabled={loading}
              className="font-mono text-sm bg-white text-black px-4 py-2 hover:bg-white/80 transition-colors disabled:opacity-40 cursor-pointer disabled:cursor-not-allowed"
            >
              {loading ? 'CREATING ACCOUNT...' : 'SIGN UP →'}
            </button>
          </form>

          {/* Footer link */}
          <div className="border border-white/20 border-t-0 px-6 py-3">
            <p className="font-mono text-xs text-white/30">
              Already have an account?{' '}
              <Link to="/login" className="text-white/60 hover:text-white transition-colors underline">
                Log in
              </Link>
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}

// ─── Shared field component ────────────────────────────────────────────────────

function Field({
  label,
  id,
  type,
  value,
  onChange,
  autoComplete,
  required,
}: {
  label: string;
  id: string;
  type: string;
  value: string;
  onChange: (v: string) => void;
  autoComplete?: string;
  required?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="font-mono text-xs text-white/40 tracking-widest">
        {label}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required={required}
        className="bg-black border border-white/20 focus:border-white/60 outline-none px-3 py-2 text-sm font-mono text-white placeholder-white/20 transition-colors"
      />
    </div>
  );
}

