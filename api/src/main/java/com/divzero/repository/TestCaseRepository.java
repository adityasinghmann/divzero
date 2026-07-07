package com.divzero.repository;

import com.divzero.entity.TestCaseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseRepository extends JpaRepository<TestCaseEntity, String> {

    List<TestCaseEntity> findByProblemId(String problemId);
}
