package com.divzero.service.impl;

import com.divzero.service.LeaderboardReadService;
import com.divzero.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private final LeaderboardReadService leaderboardReadService;

    @Override
    public ResponseEntity<Object> getLeaderboard() {
        return ResponseEntity.ok(leaderboardReadService.getLeaderboard());
    }
}
