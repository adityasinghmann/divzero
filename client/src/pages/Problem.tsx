import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import Navbar from '../components/Navbar';
import { api } from '../lib/api';
import { useAuth } from '../context/AuthContext';

interface Problem {
  id: string;
  title: string;
  description: string;
}

interface Test {
  id: string;
  input: string;
  correctOutput: string;
}

interface Submission {
  id: string;
  verdict: string;
  time: string;
}

type VerdictState =
  | { status: 'idle' }
  | { status: 'running' }
  | { status: 'accepted' }
  | { status: 'wrong_answer'; testId: string; input: string; expected: string; got: string }
  | { status: 'compile_error'; details: string }
  | { status: 'runtime_error'; testId: string }
  | { status: 'error'; message: string };

const DEFAULT_CODE = `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);
    
    // your solution here
    
    return 0;
}
`;

export default function Problem() {
  const { id } = useParams<{ id: string }>();
  const { token, user } = useAuth();
  const navigate = useNavigate();

  const [problem, setProblem] = useState<Problem | null>(null);
  const [tests, setTests] = useState<Test[]>([]);
  const [loadingProblem, setLoadingProblem] = useState(true);
  const [verdict, setVerdict] = useState<VerdictState>({ status: 'idle' });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [submissions, setSubmissions] = useState<Submission[]>([]);

  const codeRef = useRef<string>(DEFAULT_CODE);

  const fetchSubmissions = useCallback(async () => {
    if (!id || !user) return;
    const res = await api.get<Submission[]>(`/view-submissions?problemId=${id}&userId=${user.id}`, token);
    if (res.ok) setSubmissions(res.data);
  }, [id, user, token]);

  const closeDialog = useCallback(() => {
    if (verdict.status === 'running') return;
    setDialogOpen(false);
  }, [verdict.status]);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') closeDialog(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [closeDialog]);

  // Fetch problem + tests in parallel
  useEffect(() => {
    if (!id) return;
    Promise.all([
      api.get<Problem[]>('/view-problems', token),
      api.get<Test[]>(`/view-tests?problemId=${id}`, token),
    ]).then(([pRes, tRes]) => {
      if (pRes.ok) {
        const found = pRes.data.find((p) => p.id === id);
        if (!found) navigate('/problems', { replace: true });
        else setProblem(found);
      }
      if (tRes.ok) setTests(tRes.data);
      setLoadingProblem(false);
    });
    fetchSubmissions();
  }, [id, token, navigate, fetchSubmissions]);

  const handleSubmit = async () => {
    if (!id || !user) return;
    setVerdict({ status: 'running' });

    const res = await api.post<{
      message: string;
      status?: string;
      errorDetails?: string;
      userOutput?: string;
      correctOutput?: string;
      input?: string;
    }>('/submit-code', { code: codeRef.current, problemId: id, userId: user.id }, token);

    if (!res.ok) {
      setVerdict({ status: 'error', message: res.message });
      return;
    }

    const { message, errorDetails, userOutput, correctOutput, input } = res.data;

    if (message === 'Accepted') {
      setVerdict({ status: 'accepted' });
    } else if (message.startsWith('Failed on test')) {
      const testId = message.match(/test (\S+)/)?.[1] ?? '?';
      setVerdict({
        status: 'wrong_answer',
        testId,
        input: input ?? '',
        expected: correctOutput ?? '',
        got: userOutput ?? '',
      });
    } else if (message === 'Compilation Error') {
      setVerdict({ status: 'compile_error', details: errorDetails ?? '' });
    } else if (message.startsWith('Runtime Error')) {
      const testId = message.match(/test (\S+)/)?.[1] ?? '?';
      setVerdict({ status: 'runtime_error', testId });
    } else {
      setVerdict({ status: 'error', message });
    }

    // Refresh submissions list after judging completes
    fetchSubmissions();
  };

  const handleSubmitClick = () => {
    setDialogOpen(true);
    handleSubmit();
  };

  // Sample tests: show at most the first 3
  const sampleTests = tests.slice(0, 3);

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <Navbar />

      {loadingProblem ? (
        <div className="flex-1 flex items-center justify-center">
          <p className="font-mono text-xs text-white/30 animate-pulse">LOADING...</p>
        </div>
      ) : (
        <div className="flex-1 flex flex-col lg:flex-row overflow-hidden" style={{ height: 'calc(100vh - 3rem)' }}>

          {/* ── LEFT: Problem panel ── */}
          <div className="lg:w-[42%] border-r border-white/20 overflow-y-auto flex flex-col">
            {/* Title */}
            <div className="border-b border-white/20 px-5 py-4">
              <p className="font-mono text-xs text-white/30 tracking-widest mb-1">PROBLEM</p>
              <h2 className="font-mono text-base font-bold">{problem?.title}</h2>
            </div>

            {/* Description */}
            <div className="px-5 py-4 border-b border-white/20">
              <p className="font-mono text-xs text-white/40 tracking-widest mb-3">DESCRIPTION</p>
              <p className="text-sm text-white/70 leading-relaxed whitespace-pre-wrap">
                {problem?.description}
              </p>
            </div>

            {/* Sample test cases */}
            {sampleTests.length > 0 && (
              <div className="px-5 py-4">
                <p className="font-mono text-xs text-white/40 tracking-widest mb-4">SAMPLE TESTS</p>
                <div className="flex flex-col gap-4">
                  {sampleTests.map((t, i) => (
                    <div key={t.id} className="border border-white/15">
                      <div className="font-mono text-xs text-white/30 px-3 py-1.5 border-b border-white/15 bg-white/5">
                        SAMPLE {i + 1}
                      </div>
                      <div className="grid grid-cols-2 divide-x divide-white/15">
                        <div className="p-3">
                          <p className="font-mono text-xs text-white/30 mb-1">INPUT</p>
                          <pre className="font-mono text-xs text-white/80 whitespace-pre-wrap break-all">
                            {t.input}
                          </pre>
                        </div>
                        <div className="p-3">
                          <p className="font-mono text-xs text-white/30 mb-1">OUTPUT</p>
                          <pre className="font-mono text-xs text-white/80 whitespace-pre-wrap break-all">
                            {t.correctOutput}
                          </pre>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Admin-only view of all test cases */}
            {user?.isAdmin && tests.length > 0 && (
              <div className="px-5 py-4 border-t border-white/20">
                <p className="font-mono text-xs text-white/40 tracking-widest mb-4">
                  TEST SUITE (ADMIN ONLY — {tests.length} CASES)
                </p>
                <div className="flex flex-col gap-4">
                  {tests.map((t, i) => (
                    <details key={t.id} className="border border-white/15 group">
                      <summary className="font-mono text-xs text-white/30 px-3 py-2 bg-white/5 cursor-pointer hover:bg-white/10 transition-colors flex items-center justify-between">
                        <span>TEST CASE #{i + 1} ({t.id})</span>
                        <span className="text-[10px] text-white/20 group-open:hidden">▶ SHOW</span>
                        <span className="text-[10px] text-white/20 hidden group-open:inline">▼ HIDE</span>
                      </summary>
                      <div className="grid grid-cols-2 divide-x divide-white/15 border-t border-white/15">
                        <div className="p-3">
                          <p className="font-mono text-xs text-white/30 mb-1">INPUT</p>
                          <pre className="font-mono text-xs text-white/80 whitespace-pre-wrap break-all">
                            {t.input || '(empty)'}
                          </pre>
                        </div>
                        <div className="p-3">
                          <p className="font-mono text-xs text-white/30 mb-1">OUTPUT</p>
                          <pre className="font-mono text-xs text-white/80 whitespace-pre-wrap break-all">
                            {t.correctOutput || '(empty)'}
                          </pre>
                        </div>
                      </div>
                    </details>
                  ))}
                </div>
              </div>
            )}

            {/* Submissions history */}
            <div className="px-5 py-4 border-t border-white/20 mt-auto">
              <p className="font-mono text-xs text-white/40 tracking-widest mb-3">MY SUBMISSIONS</p>
              {submissions.length === 0 ? (
                <p className="font-mono text-xs text-white/20">No submissions yet.</p>
              ) : (
                <div className="flex flex-col gap-1">
                  {submissions.map((s) => (
                    <div key={s.id} className="flex items-center justify-between border border-white/10 px-3 py-2">
                      <span className={`font-mono text-xs font-bold ${verdictColor(s.verdict)}`}>
                        {s.verdict.toUpperCase()}
                      </span>
                      <span className="font-mono text-xs text-white/30">
                        {new Date(s.time).toLocaleString()}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* ── RIGHT: Editor + verdict ── */}
          <div className="flex-1 flex flex-col">
            {/* Editor toolbar */}
            <div className="border-b border-white/20 px-4 py-2 flex items-center justify-between shrink-0">
              <span className="font-mono text-xs text-white/30">C++ (g++)</span>
              <button
                onClick={handleSubmitClick}
                disabled={verdict.status === 'running'}
                className="font-mono text-xs bg-white text-black px-4 py-1.5 hover:bg-white/80 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              >
                {verdict.status === 'running' ? 'RUNNING...' : 'SUBMIT'}
              </button>
            </div>

            {/* Monaco editor */}
            <div className="flex-1 min-h-0">
              <Editor
                height="100%"
                defaultLanguage="cpp"
                defaultValue={DEFAULT_CODE}
                theme="vs-dark"
                onChange={(val) => { codeRef.current = val ?? ''; }}
                options={{
                  fontSize: 13,
                  fontFamily: '"Cascadia Code", "Fira Code", monospace',
                  minimap: { enabled: false },
                  scrollBeyondLastLine: false,
                  renderLineHighlight: 'line',
                  lineNumbers: 'on',
                  tabSize: 4,
                  wordWrap: 'off',
                  padding: { top: 12, bottom: 12 },
                }}
              />
            </div>

          </div>
        </div>
      )}

      {/* Verdict dialog */}
      {dialogOpen && (
        <VerdictDialog verdict={verdict} onClose={closeDialog} />
      )}
    </div>
  );
}

// ─── Verdict dialog ───────────────────────────────────────────────────────────

function VerdictDialog({
  verdict,
  onClose,
}: {
  verdict: VerdictState;
  onClose: () => void;
}) {
  const isRunning = verdict.status === 'running';

  return (
    // Backdrop
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70"
      onClick={isRunning ? undefined : onClose}
    >
      {/* Modal */}
      <div
        className="bg-black border border-white/30 w-full max-w-lg mx-4"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-white/20 px-5 py-3">
          <span className="font-mono text-xs text-white/40 tracking-widest">SUBMISSION RESULT</span>
          {!isRunning && (
            <button
              onClick={onClose}
              className="font-mono text-xs text-white/30 hover:text-white transition-colors cursor-pointer"
            >
              [ESC] CLOSE
            </button>
          )}
        </div>

        {/* Body */}
        <div className="px-5 py-5">
          {verdict.status === 'running' && (
            <p className="font-mono text-sm text-white/50 animate-pulse">JUDGING...</p>
          )}

          {verdict.status === 'accepted' && (
            <p className="font-mono text-sm font-bold text-green-400">✓ ACCEPTED — All tests passed.</p>
          )}

          {verdict.status === 'wrong_answer' && (
            <div className="flex flex-col gap-4">
              <p className="font-mono text-sm font-bold text-red-400">
                ✗ WRONG ANSWER — test {verdict.testId}
              </p>
              <div className="grid grid-cols-3 gap-2 font-mono text-xs">
                <IOBlock label="INPUT" value={verdict.input} />
                <IOBlock label="EXPECTED" value={verdict.expected} />
                <IOBlock label="YOUR OUTPUT" value={verdict.got} accent />
              </div>
            </div>
          )}

          {verdict.status === 'compile_error' && (
            <div className="flex flex-col gap-3">
              <p className="font-mono text-sm font-bold text-yellow-400">✗ COMPILATION ERROR</p>
              <pre className="font-mono text-xs text-yellow-400/70 whitespace-pre-wrap max-h-48 overflow-y-auto border border-yellow-400/20 bg-yellow-400/5 px-3 py-2">
                {verdict.details}
              </pre>
            </div>
          )}

          {verdict.status === 'runtime_error' && (
            <p className="font-mono text-sm font-bold text-orange-400">
              ✗ RUNTIME ERROR — test {verdict.testId}
            </p>
          )}

          {verdict.status === 'error' && (
            <p className="font-mono text-sm font-bold text-red-400">ERROR: {verdict.message}</p>
          )}
        </div>
      </div>
    </div>
  );
}

function IOBlock({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="border border-white/15">
      <div className="px-2 py-1 border-b border-white/15 bg-white/5 text-white/30">{label}</div>
      <pre
        className={`px-2 py-1.5 whitespace-pre-wrap break-all max-h-20 overflow-y-auto ${
          accent ? 'text-red-300' : 'text-white/60'
        }`}
      >
        {value || '(empty)'}
      </pre>
    </div>
  );
}

function verdictColor(verdict: string): string {
  switch (verdict) {
    case 'Accepted': return 'text-green-400';
    case 'Wrong Answer': return 'text-red-400';
    case 'Compilation Error': return 'text-yellow-400';
    case 'Time Limit Exceeded': return 'text-blue-400';
    case 'Runtime Error': return 'text-orange-400';
    default: return 'text-white/50';
  }
}

