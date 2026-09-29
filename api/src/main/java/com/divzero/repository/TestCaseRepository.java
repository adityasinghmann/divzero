package com.divzero.repository;

import com.divzero.entity.TestCaseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCaseEntity, String> {

    /** Ordered so judging and display run tests in a stable, creation-like order. */
    List<TestCaseEntity> findByProblemIdOrderByIdAsc(String problemId);

    boolean existsByProblemId(String problemId);
}
