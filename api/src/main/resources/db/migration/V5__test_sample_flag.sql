-- Only sample tests are shown to non-admin users; every other test is hidden and its input and
-- expected output never leave the server.
ALTER TABLE "Test"
    ADD COLUMN "isSample" BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve what users could already see: the client used to display the first three tests of
-- each problem as samples. Ids are CUIDs with a base-36 timestamp prefix, so ordering by id
-- approximates creation order.
UPDATE "Test" t
SET "isSample" = TRUE
FROM (
    SELECT "id", ROW_NUMBER() OVER (PARTITION BY "problemId" ORDER BY "id") AS rn
    FROM "Test"
) ranked
WHERE ranked."id" = t."id" AND ranked.rn <= 3;
