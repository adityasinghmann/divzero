import { useEffect, useState, type FormEvent } from 'react';
import Navbar from '../components/Navbar';
import { api } from '../lib/api';
import { useAuth } from '../context/AuthContext';

interface Problem {
  id: string;
  title: string;
}

type FormStatus =
  | { type: 'idle' }
  | { type: 'loading' }
  | { type: 'success'; message: string }
  | { type: 'error'; message: string };

export default function Admin() {
  const { token, user } = useAuth();

  // ── Problem list (needed for test form dropdown) ──────────────────────────
  const [problems, setProblems] = useState<Problem[]>([]);

  useEffect(() => {
    api.get<Problem[]>('/view-problems', token).then((res) => {
      if (res.ok) setProblems(res.data);
    });
  }, [token]);

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <Navbar />

      <main className="flex-1 max-w-4xl mx-auto px-4 py-10 w-full flex flex-col gap-10">
        {/* Page header */}
        <div>
          <p className="font-mono text-xs text-white/30 tracking-widest mb-1">DASHBOARD</p>
          <h1 className="font-mono text-lg font-bold">ADMIN</h1>
          <p className="font-mono text-xs text-white/30 mt-1">{user?.username}</p>
        </div>

        {/* ── Form 1: Create Problem ── */}
        <CreateProblemForm
          token={token}
          onCreated={(p) => setProblems((prev) => [...prev, p])}
        />

        {/* ── Form 2: Create Test ── */}
        <CreateTestForm token={token} problems={problems} />

        {/* ── User List ── */}
        <UserList token={token} />
      </main>
    </div>
  );
}

// ─── Create Problem ───────────────────────────────────────────────────────────

function CreateProblemForm({
  token,
  onCreated,
}: {
  token: string | null;
  onCreated: (p: Problem) => void;
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<FormStatus>({ type: 'idle' });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setStatus({ type: 'loading' });

    const res = await api.post<{ status: string; response: Problem }>(
      '/create-problem',
      { title, description },
      token,
    );

    if (!res.ok) {
      setStatus({ type: 'error', message: res.message });
      return;
    }

    onCreated(res.data.response);
    setTitle('');
    setDescription('');
    setStatus({ type: 'success', message: `Problem "${res.data.response.title}" created.` });
  };

  return (
    <section>
      {/* Section header */}
      <div className="border border-white/20 border-b-0 px-5 pt-5 pb-3">
        <p className="font-mono text-xs text-white/30 tracking-widest mb-0.5">FORM 01</p>
        <h2 className="font-mono text-sm font-bold">CREATE PROBLEM</h2>
      </div>

      <form onSubmit={handleSubmit} className="border border-white/20 px-5 py-5 flex flex-col gap-5">
        <Field
          label="TITLE"
          id="prob-title"
          value={title}
          onChange={setTitle}
          required
        />

        <div className="flex flex-col gap-1.5">
          <label htmlFor="prob-desc" className="font-mono text-xs text-white/40 tracking-widest">
            DESCRIPTION
          </label>
          <textarea
            id="prob-desc"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            required
            rows={6}
            className="bg-black border border-white/20 focus:border-white/60 outline-none px-3 py-2 text-sm font-mono text-white resize-y transition-colors"
          />
        </div>

        <StatusBar status={status} />

        <button
          type="submit"
          disabled={status.type === 'loading'}
          className="font-mono text-sm bg-white text-black px-4 py-2 hover:bg-white/80 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer self-start"
        >
          {status.type === 'loading' ? 'CREATING...' : 'CREATE PROBLEM →'}
        </button>
      </form>
    </section>
  );
}

// ─── Create Test ──────────────────────────────────────────────────────────────

function CreateTestForm({
  token,
  problems,
}: {
  token: string | null;
  problems: Problem[];
}) {
  const [problemId, setProblemId] = useState('');
  const [input, setInput] = useState('');
  const [correctOutput, setCorrectOutput] = useState('');
  const [status, setStatus] = useState<FormStatus>({ type: 'idle' });

  // Auto-select first problem when list loads/changes
  useEffect(() => {
    if (problems.length > 0 && !problemId) {
      setProblemId(problems[0].id);
    }
  }, [problems, problemId]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!problemId) return;
    setStatus({ type: 'loading' });

    const res = await api.post('/create-test', { problemId, input, correctOutput }, token);

    if (!res.ok) {
      setStatus({ type: 'error', message: res.message });
      return;
    }

    setInput('');
    setCorrectOutput('');
    setStatus({ type: 'success', message: 'Test case added successfully.' });
  };

  return (
    <section>
      {/* Section header */}
      <div className="border border-white/20 border-b-0 px-5 pt-5 pb-3">
        <p className="font-mono text-xs text-white/30 tracking-widest mb-0.5">FORM 02</p>
        <h2 className="font-mono text-sm font-bold">CREATE TEST CASE</h2>
      </div>

      <form onSubmit={handleSubmit} className="border border-white/20 px-5 py-5 flex flex-col gap-5">
        {/* Problem selector */}
        <div className="flex flex-col gap-1.5">
          <label htmlFor="test-problem" className="font-mono text-xs text-white/40 tracking-widest">
            PROBLEM
          </label>
          {problems.length === 0 ? (
            <p className="font-mono text-xs text-white/30 italic">No problems yet — create one first.</p>
          ) : (
            <select
              id="test-problem"
              value={problemId}
              onChange={(e) => setProblemId(e.target.value)}
              required
              className="bg-black border border-white/20 focus:border-white/60 outline-none px-3 py-2 text-sm font-mono text-white transition-colors cursor-pointer"
            >
              {problems.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Input / Output side by side */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="test-input" className="font-mono text-xs text-white/40 tracking-widest">
              INPUT
            </label>
            <textarea
              id="test-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              required
              rows={8}
              placeholder="stdin fed to the program"
              className="bg-black border border-white/20 focus:border-white/60 outline-none px-3 py-2 text-sm font-mono text-white placeholder-white/15 resize-y transition-colors"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="test-output" className="font-mono text-xs text-white/40 tracking-widest">
              EXPECTED OUTPUT
            </label>
            <textarea
              id="test-output"
              value={correctOutput}
              onChange={(e) => setCorrectOutput(e.target.value)}
              required
              rows={8}
              placeholder="expected stdout"
              className="bg-black border border-white/20 focus:border-white/60 outline-none px-3 py-2 text-sm font-mono text-white placeholder-white/15 resize-y transition-colors"
            />
          </div>
        </div>

        <StatusBar status={status} />

        <button
          type="submit"
          disabled={status.type === 'loading' || problems.length === 0}
          className="font-mono text-sm bg-white text-black px-4 py-2 hover:bg-white/80 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer self-start"
        >
          {status.type === 'loading' ? 'SAVING...' : 'ADD TEST CASE →'}
        </button>
      </form>
    </section>
  );
}

// ─── Shared components ────────────────────────────────────────────────────────

function Field({
  label,
  id,
  value,
  onChange,
  required,
}: {
  label: string;
  id: string;
  value: string;
  onChange: (v: string) => void;
  required?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="font-mono text-xs text-white/40 tracking-widest">
        {label}
      </label>
      <input
        id={id}
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        className="bg-black border border-white/20 focus:border-white/60 outline-none px-3 py-2 text-sm font-mono text-white transition-colors"
      />
    </div>
  );
}

function StatusBar({ status }: { status: FormStatus }) {
  if (status.type === 'idle' || status.type === 'loading') return null;

  return status.type === 'success' ? (
    <p className="font-mono text-xs text-green-400 border border-green-400/30 bg-green-400/5 px-3 py-2">
      ✓ {status.message}
    </p>
  ) : (
    <p className="font-mono text-xs text-red-400 border border-red-400/30 bg-red-400/5 px-3 py-2">
      ERROR: {status.message}
    </p>
  );
}

interface RegisteredUser {
  id: string;
  name: string;
  username: string;
  role: string;
}

function UserList({ token }: { token: string | null }) {
  const [users, setUsers] = useState<RegisteredUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<RegisteredUser[]>('/view-users', token).then((res) => {
      if (res.ok) {
        setUsers(res.data);
      } else {
        setError(res.message);
      }
      setLoading(false);
    });
  }, [token]);

  return (
    <section className="mb-10">
      <div className="border border-white/20 border-b-0 px-5 pt-5 pb-3">
        <p className="font-mono text-xs text-white/30 tracking-widest mb-0.5">DASHBOARD 03</p>
        <h2 className="font-mono text-sm font-bold">REGISTERED USERS</h2>
      </div>

      <div className="border border-white/20 px-5 py-5 flex flex-col gap-4">
        {loading && (
          <p className="font-mono text-xs text-white/30 animate-pulse">LOADING USERS...</p>
        )}

        {error && (
          <p className="font-mono text-xs text-red-400 border border-red-400/30 bg-red-400/5 px-3 py-2">
            ERROR: {error}
          </p>
        )}

        {!loading && !error && (
          <div className="overflow-x-auto border border-white/10">
            <table className="w-full text-left border-collapse font-mono text-xs">
              <thead>
                <tr className="bg-white/5 border-b border-white/10">
                  <th className="px-4 py-2 text-white/40 font-bold border-r border-white/10">ID</th>
                  <th className="px-4 py-2 text-white/40 font-bold border-r border-white/10">NAME</th>
                  <th className="px-4 py-2 text-white/40 font-bold border-r border-white/10">USERNAME</th>
                  <th className="px-4 py-2 text-white/40 font-bold">ROLE</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-4 py-4 text-center text-white/20">
                      NO USERS REGISTERED
                    </td>
                  </tr>
                ) : (
                  users.map((u) => (
                    <tr key={u.id} className="border-b border-white/5 last:border-b-0 hover:bg-white/5 transition-colors">
                      <td className="px-4 py-2 border-r border-white/10 text-white/50">{u.id}</td>
                      <td className="px-4 py-2 border-r border-white/10 text-white/80">{u.name}</td>
                      <td className="px-4 py-2 border-r border-white/10 text-white/80">{u.username}</td>
                      <td className="px-4 py-2">
                        <span className={`px-1.5 py-0.5 text-[10px] font-bold ${u.role === 'ADMIN' ? 'bg-red-500/10 text-red-400 border border-red-500/20' : 'bg-green-500/10 text-green-400 border border-green-500/20'}`}>
                          {u.role}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

