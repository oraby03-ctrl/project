package com.webapp.server.domain;

import com.webapp.shared.dto.GameType;

public record MatchRequest(String ticketId, String username, GameType gameType) {
}
