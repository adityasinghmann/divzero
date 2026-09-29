package com.divzero.repository;

import com.divzero.entity.SubmissionEntity;
import com.divzero.entity.SubmissionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, String> {

    List<SubmissionEntity> findByProblemIdAndUserIdOrderByTimeDesc(String problemId, String userId);

    boolean existsByUserIdAndProblemIdAndStatus(String userId, String problemId, SubmissionStatus status);

    /**
     * Ranking is by distinct problems solved, so re-solving a problem cannot inflate the score.
     * The tiebreaker sums the single best (minimum) accepted run time per problem for the same
     * reason: repeated accepted attempts must neither inflate nor deflate it.
     */
    @Query(value = """
        WITH "bestPerProblem" AS (
            SELECT
                s."userId" AS "userId",
                s."problemId" AS "problemId",
                MIN(s."executionTimeMs") AS "bestExecutionTimeMs"
            FROM "Submission" s
            WHERE s."status" = 'ACCEPTED'
            GROUP BY s."userId", s."problemId"
        ),
        "solved" AS (
            SELECT
                b."userId" AS "userId",
                COUNT(*) AS "problemsSolved",
                COALESCE(SUM(b."bestExecutionTimeMs"), 0) AS "totalExecutionTimeMs"
            FROM "bestPerProblem" b
            GROUP BY b."userId"
        ),
        "attempts" AS (
            SELECT
                s."userId" AS "userId",
                COUNT(*) AS "totalSubmissions"
            FROM "Submission" s
            GROUP BY s."userId"
        )
        SELECT
            u."id" AS "userId",
            u."name" AS "name",
            u."username" AS "username",
            COALESCE(sv."problemsSolved", 0) AS "problemsSolved",
            COALESCE(a."totalSubmissions", 0) AS "totalSubmissions",
            COALESCE(sv."totalExecutionTimeMs", 0) AS "totalExecutionTimeMs"
        FROM "User" u
        LEFT JOIN "solved" sv ON sv."userId" = u."id"
        LEFT JOIN "attempts" a ON a."userId" = u."id"
        ORDER BY
            "problemsSolved" DESC,
            "totalExecutionTimeMs" ASC,
            "totalSubmissions" ASC,
            u."username" ASC
        """, nativeQuery = true)
    List<LeaderboardRow> findLeaderboard();
}
