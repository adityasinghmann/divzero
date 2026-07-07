package com.divzero.service;

import com.divzero.dto.LeaderboardEntryResponse;
import java.util.List;

public interface LeaderboardReadService {

    List<LeaderboardEntryResponse> getLeaderboard();
}
