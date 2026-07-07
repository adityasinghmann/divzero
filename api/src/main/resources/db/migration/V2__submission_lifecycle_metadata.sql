ALTER TABLE "Submission"
    ADD COLUMN "status" TEXT,
    ADD COLUMN "compileTimeMs" BIGINT,
    ADD COLUMN "executionTimeMs" BIGINT,
    ADD COLUMN "memoryUsageKb" BIGINT,
    ADD COLUMN "language" TEXT NOT NULL DEFAULT 'cpp',
    ADD COLUMN "judgeLogs" TEXT;

UPDATE "Submission"
SET "status" = CASE "verdict"
    WHEN 'Accepted' THEN 'ACCEPTED'
    WHEN 'Wrong Answer' THEN 'WRONG_ANSWER'
    WHEN 'Compilation Error' THEN 'COMPILATION_ERROR'
    WHEN 'Runtime Error' THEN 'RUNTIME_ERROR'
    WHEN 'Time Limit Exceeded' THEN 'TIME_LIMIT_EXCEEDED'
    WHEN 'Queued' THEN 'QUEUED'
    WHEN 'Running' THEN 'RUNNING'
    ELSE 'INTERNAL_ERROR'
END;

ALTER TABLE "Submission"
    ALTER COLUMN "status" SET NOT NULL,
    ALTER COLUMN "status" SET DEFAULT 'QUEUED';
