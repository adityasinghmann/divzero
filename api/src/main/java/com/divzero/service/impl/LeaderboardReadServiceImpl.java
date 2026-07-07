package com.divzero.service.impl;

import com.divzero.config.CacheNames;
import com.divzero.dto.LeaderboardEntryResponse;
import com.divzero.repository.LeaderboardRow;
import com.divzero.repository.SubmissionRepository;
import com.divzero.service.LeaderboardReadService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardReadServiceImpl implements LeaderboardReadService {

    private final SubmissionRepository submissionRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.LEADERBOARD, key = "'global'")
    public List<LeaderboardEntryResponse> getLeaderboard() {
        log.info("Loading leaderboard from database");
        List<LeaderboardEntryResponse> leaderboard = new ArrayList<>();
        int rank = 1;
        for (LeaderboardRow row : submissionRepository.findLeaderboard()) {
            leaderboard.add(new LeaderboardEntryResponse(
                rank++,
                row.getUserId(),
                row.getName(),
                row.getUsername(),
                value(row.getAcceptedSubmissions()),
                value(row.getTotalSubmissions()),
                value(row.getTotalExecutionTimeMs())
            ));
        }
        return leaderboard;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
