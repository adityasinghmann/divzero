package com.divzero.service;

import com.divzero.dto.ProblemDetailsResponse;
import com.divzero.dto.ProblemResponse;
import java.util.List;

public interface ProblemReadService {

    List<ProblemResponse> getProblemList();

    ProblemDetailsResponse getProblemDetails(String problemId);
}
