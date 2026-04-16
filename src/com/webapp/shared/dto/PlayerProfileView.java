package com.webapp.shared.dto;

import java.io.Serializable;

public record PlayerProfileView(
        String playerId,
        String playerName,
        int wins,
        int losses,
        int draws,
        int score
) implements Serializable {
}
