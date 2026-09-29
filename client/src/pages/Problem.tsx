import { useEffect, useState, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import Navbar from '../components/Navbar';
import { api } from '../lib/api';
import { useAuth } from '../context/auth';

interface Problem {
  id: string;
  title: string;
  description: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
}

interface Test {
  id: string;
  input: string;
  correctOutput: string;
  sample: boolean;
}

// Non-admins receive sample tests only; admins receive every test.
interface ProblemDetails extends Problem {
  tests: Test[];
}

interface Submission {
  id: string;
  verdict: string;
  time: string;
}

type VerdictState =
  | { status: 'idle' }
  | { status: 'running' }
  | { status: 'queued'; current: string }
  | { status: 'polled'; verdict: string }
  | { status: 'accepted' }
  // io is null when the failing test is hidden: the API withholds its data.
  | { status: 'wrong_answer'; testId: string; io: { input: string; expected: string; got: string } | null }
  | { status: 'compile_error'; details: string }
  | { status: 'runtime_error'; testId: string; stderr?: string }
  | { status: 'time_limit'; testId: string }
  | { status: 'memory_limit'; testId: string }
  | { status: 'error'; message: string };

// The API answers 202 when judging outlives the short synchronous wait; from there the
// verdict is retrieved by polling, which is also the durable path if the API instance that
// held the request is replaced mid-judge.
const POLL_INTERVAL_MS = 1500;
const POLL_TIMEOUT_MS = 60000;
const TERMINAL_VERDICTS = new Set([
  'Accepted',
  'Wrong Answer',
  'Compilation Error',
  'Runtime Error',
  'Time Limit Exceeded',
  'Memory Limit Exceeded',
  'Internal Error',
]);

const submissionsPath = (problemId: string) =>
  `/view-submissions?problemId=${encodeURIComponent(problemId)}`;

const DEFAULT_CODE = `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios_base::sync_with_stdio(false);
    cin.tie(NULL);
    
    // your solution here
    
    return 0;
}
`;

/**
 * Keyed by problem id so that moving between problems remounts the page: every piece of state
 * (problem, tests, verdict, editor contents) starts fresh, and the previous page's polling loop
 * is stopped by its unmount instead of writing into the new problem's view.
 */
export default function Problem() {
  const { id } = useParams<{ id: string }>();
  return <ProblemView key={id} />;
}

function ProblemView() {
  const { id } = useParams<{ id: string }>();
  const { token, user } = useAuth();
  const navigate = useNavigate();

  const [problem, setProblem] = useState<Problem | null>(null);
  const [tests, setTests] = useState<Test[]>([]);
  const [loadingProblem, setLoadingProblem] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [verdict, setVerdict] = useState<VerdictState>({ status: 'idle' });
  const [dialogOpen, setDialogOpen] = useState(false);
  const [submissions, setSubmissions] = useState<Submission[]>([]);

  const codeRef = useRef<string>(DEFAULT_CODE);
  // Cleared on unmount so an in-flight polling loop stops instead of running for up to a minute.
  const mountedRef = useRef(true);
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const fetchSubmissions = useCallback(async () => {
    if (!id || !user || !mountedRef.current) return;
    const res = await api.get<Submission[]>(submissionsPath(id), token);
    if (res.ok && mountedRef.current) setSubmissions(res.data);
  }, [id, user, token]);

  const pollForVerdict = useCallback(
    async (submissionId: string) => {
      if (!id) return;
      const deadline = Date.now() + POLL_TIMEOUT_MS;
      setVerdict({ status: 'queued', current: 'Queued' });

      while (Date.now() < deadline) {
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
        if (!mountedRef.current) return;
        const res = await api.get<Submission[]>(submissionsPath(id), token);
        if (!mountedRef.current) return;
        if (!res.ok) continue;

        const found = res.data.find((s) => s.id === submissionId);
        if (!found) continue;

        setSubmissions(res.data);
        if (TERMINAL_VERDICTS.has(found.verdict)) {
          setVerdict(
            found.verdict === 'Accepted'
              ? { status: 'accepted' }
              : { status: 'polled', verdict: found.verdict },
          );
          return;
        }
        setVerdict({ status: 'queued', current: found.verdict });
      }

      setVerdict({
        status: 'error',
        message: 'Still judging. Check your submissions list in a moment.',
      });
    },
    [id, token],
  );

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

  // Fetch the problem together with the tests this caller may see, and the caller's history
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    api
      .get<ProblemDetails>(`/view-problem?problemId=${encodeURIComponent(id)}`, token)
      .then((res) => {
        if (cancelled) return;
        if (res.ok) {
          const { tests: problemTests, ...rest } = res.data;
          setProblem(rest);
          setTests(problemTests);
        } else if (res.status === 404) {
          navigate('/problems', { replace: true });
        } else {
          setLoadError(res.message);
        }
        setLoadingProblem(false);
      });
    api.get<Submission[]>(submissionsPath(id), token).then((res) => {
      if (!cancelled && res.ok) setSubmissions(res.data);
    });
    return () => {
      cancelled = true;
    };
  }, [id, token, navigate]);

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
      stderr?: string;
      submissionId?: string;
    }>('/submit-code', { code: codeRef.current, problemId: id }, token);
    if (!mountedRef.current) return;

    if (!res.ok) {
      setVerdict({ status: 'error', message: res.message });
      return;
    }

    // 202 = queued, still judging. Fall back to polling for the terminal verdict.
    if (res.status === 202 && res.data?.submissionId) {
      await pollForVerdict(res.data.submissionId);
      // pollForVerdict also returns early on unmount; nothing further should be fetched then.
      fetchSubmissions();
      return;
    }

    const { errorDetails, userOutput, correctOutput, input, stderr } = res.data ?? {};
    const message = res.data?.message ?? '';
    const testId = message.match(/test (\S+)/)?.[1] ?? '?';

    if (message === 'Accepted') {
      setVerdict({ status: 'accepted' });
    } else if (message.startsWith('Failed on test')) {
      setVerdict({
        status: 'wrong_answer',
        testId,
        // The API only includes these fields when the failing test is a sample.
        io:
          input === undefined
            ? null
            : { input, expected: correctOutput ?? '', got: userOutput ?? '' },
      });
    } else if (message === 'Compilation Error') {
      setVerdict({ status: 'compile_error', details: errorDetails ?? '' });
    } else if (message.startsWith('Runtime Error')) {
      setVerdict({ status: 'runtime_error', testId, stderr });
    } else if (message.startsWith('Time Limit Exceeded')) {
      setVerdict({ status: 'time_limit', testId });
    } else if (message.startsWith('Memory Limit Exceeded')) {
      setVerdict({ status: 'memory_limit', testId });
    } else {
      setVerdict({ status: 'error', message: message || 'Unexpected response from the judge' });
    }

    // Refresh submissions list after judging completes
    fetchSubmissions();
  };

  const handleSubmitClick = () => {
    setDialogOpen(true);
    handleSubmit();
  };

  // Only tests the admin marked as samples are shown as examples
  const sampleTests = tests.filter((t) => t.sample);

  return (
    <div className="min-h-screen bg-black text-white flex flex-col">
      <Navbar />

      {loadingProblem ? (
        <div className="flex-1 flex items-center justify-center">
          <p className="font-mono text-xs text-white/30 animate-pulse">LOADING...</p>
        </div>
      ) : loadError ? (
        <div className="flex-1 flex items-center justify-center px-4">
          <p className="font-mono text-xs text-red-400 border border-red-400/30 bg-red-400/5 px-3 py-2">
            ERROR: {loadError}
          </p>
        </div>
      ) : (
        <div className="flex-1 flex flex-col lg:flex-row overflow-hidden" style={{ height: 'calc(100vh - 3rem)' }}>

          {/* ── LEFT: Problem panel ── */}
          <div className="lg:w-[42%] border-r border-white/20 overflow-y-auto flex flex-col">
            {/* Title */}
            <div className="border-b border-white/20 px-5 py-4">
              <p className="font-mono text-xs text-white/30 tracking-widest mb-1">PROBLEM</p>
              <h2 className="font-mono text-base font-bold">{problem?.title}</h2>
              {problem && (
                <div className="flex flex-wrap items-center gap-2 mt-3">
                  <span
                    className={`font-mono text-[10px] font-bold px-1.5 py-0.5 border ${difficultyStyle(
                      problem.difficulty,
                    )}`}
                  >
                    {problem.difficulty}
                  </span>
                  <span className="font-mono text-[10px] text-white/40 border border-white/15 px-1.5 py-0.5">
                    {problem.timeLimitMs} MS
                  </span>
                  <span className="font-mono text-[10px] text-white/40 border border-white/15 px-1.5 py-0.5">
                    {problem.memoryLimitMb} MB
                  </span>
                </div>
              )}
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
                        <span>
                          TEST CASE #{i + 1} ({t.id}) · {t.sample ? 'SAMPLE' : 'HIDDEN'}
                        </span>
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
                disabled={verdict.status === 'running' || verdict.status === 'queued'}
                className="font-mono text-xs bg-white text-black px-4 py-1.5 hover:bg-white/80 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
              >
                {verdict.status === 'running' || verdict.status === 'queued'
                  ? 'RUNNING...'
                  : 'SUBMIT'}
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

          {verdict.status === 'queued' && (
            <div className="flex flex-col gap-2">
              <p className="font-mono text-sm text-white/50 animate-pulse">
                {verdict.current.toUpperCase()}...
              </p>
              <p className="font-mono text-xs text-white/30">
                Judging is taking longer than the request wait — polling for the result.
              </p>
            </div>
          )}

          {verdict.status === 'accepted' && (
            <p className="font-mono text-sm font-bold text-green-400">✓ ACCEPTED — All tests passed.</p>
          )}

          {verdict.status === 'polled' && (
            <p className={`font-mono text-sm font-bold ${verdictColor(verdict.verdict)}`}>
              ✗ {verdict.verdict.toUpperCase()}
            </p>
          )}

          {verdict.status === 'wrong_answer' && (
            <div className="flex flex-col gap-4">
              <p className="font-mono text-sm font-bold text-red-400">
                ✗ WRONG ANSWER — test {verdict.testId}
              </p>
              {verdict.io ? (
                <div className="grid grid-cols-3 gap-2 font-mono text-xs">
                  <IOBlock label="INPUT" value={verdict.io.input} />
                  <IOBlock label="EXPECTED" value={verdict.io.expected} />
                  <IOBlock label="YOUR OUTPUT" value={verdict.io.got} accent />
                </div>
              ) : (
                <p className="font-mono text-xs text-white/40">
                  This is a hidden test, so its input and expected output are not shown.
                </p>
              )}
            </div>
          )}

          {verdict.status === 'memory_limit' && (
            <p className="font-mono text-sm font-bold text-purple-400">
              ✗ MEMORY LIMIT EXCEEDED — test {verdict.testId}
            </p>
          )}

          {verdict.status === 'time_limit' && (
            <p className="font-mono text-sm font-bold text-blue-400">
              ✗ TIME LIMIT EXCEEDED — test {verdict.testId}
            </p>
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
            <div className="flex flex-col gap-3">
              <p className="font-mono text-sm font-bold text-orange-400">
                ✗ RUNTIME ERROR — test {verdict.testId}
              </p>
              {verdict.stderr && (
                <pre className="font-mono text-xs text-orange-400/70 whitespace-pre-wrap max-h-48 overflow-y-auto border border-orange-400/20 bg-orange-400/5 px-3 py-2">
                  {verdict.stderr}
                </pre>
              )}
            </div>
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

function difficultyStyle(difficulty: 'EASY' | 'MEDIUM' | 'HARD'): string {
  switch (difficulty) {
    case 'EASY':
      return 'text-green-400 border-green-400/30 bg-green-400/5';
    case 'HARD':
      return 'text-red-400 border-red-400/30 bg-red-400/5';
    default:
      return 'text-yellow-400 border-yellow-400/30 bg-yellow-400/5';
  }
}

function verdictColor(verdict: string): string {
  switch (verdict) {
    case 'Accepted': return 'text-green-400';
    case 'Wrong Answer': return 'text-red-400';
    case 'Compilation Error': return 'text-yellow-400';
    case 'Time Limit Exceeded': return 'text-blue-400';
    case 'Memory Limit Exceeded': return 'text-purple-400';
    case 'Runtime Error': return 'text-orange-400';
    default: return 'text-white/50';
  }
}

