package com.webapp.shared.dto;

import java.io.Serializable;

public record GameStateView(
        String roomId,
        String[][] board,
        String currentTurn,
        String winner,
        boolean draw,
        boolean finished,
        String playerX,
        String playerO,
        String playerXName,
        String playerOName,
        String statusMessage
) implements Serializable {
}
