package com.webapp.server.domain;

import com.webapp.shared.dto.GameType;

public record MatchRequest(String ticketId, String playerId, String playerName, GameType gameType) {
}
