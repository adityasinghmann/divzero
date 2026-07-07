package com.divzero.service;

import org.springframework.http.ResponseEntity;

public interface LeaderboardService {

    ResponseEntity<Object> getLeaderboard();
}
