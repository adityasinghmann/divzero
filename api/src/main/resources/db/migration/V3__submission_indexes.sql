-- Supports per-problem submission history lookups (view-submissions) and the
-- ACCEPTED-filtered aggregation behind the leaderboard query.
CREATE INDEX IF NOT EXISTS "idx_submission_problem_user_time" ON "Submission" ("problemId", "userId", "time" DESC);
CREATE INDEX IF NOT EXISTS "idx_submission_status_user_problem" ON "Submission" ("status", "userId", "problemId");
