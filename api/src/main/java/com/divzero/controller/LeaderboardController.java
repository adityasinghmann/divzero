package com.divzero.controller;

import com.divzero.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping({"/leaderboard", "/view-leaderboard"})
    public ResponseEntity<Object> getLeaderboard() {
        return leaderboardService.getLeaderboard();
    }
}
