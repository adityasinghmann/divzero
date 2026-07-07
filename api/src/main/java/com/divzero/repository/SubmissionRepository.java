package com.divzero.repository;

import com.divzero.entity.SubmissionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, String> {

    List<SubmissionEntity> findByProblemIdAndUserIdOrderByTimeDesc(String problemId, String userId);

    @Query(value = """
        SELECT
            u."id" AS "userId",
            u."name" AS "name",
            u."username" AS "username",
            COUNT(s."id") FILTER (WHERE s."status" = 'ACCEPTED') AS "acceptedSubmissions",
            COUNT(s."id") AS "totalSubmissions",
            COALESCE(SUM(s."executionTimeMs") FILTER (WHERE s."status" = 'ACCEPTED'), 0) AS "totalExecutionTimeMs"
        FROM "User" u
        LEFT JOIN "Submission" s ON s."userId" = u."id"
        GROUP BY u."id", u."name", u."username"
        ORDER BY
            "acceptedSubmissions" DESC,
            "totalExecutionTimeMs" ASC,
            "totalSubmissions" ASC,
            u."username" ASC
        """, nativeQuery = true)
    List<LeaderboardRow> findLeaderboard();
}
