package com.webapp.shared.dto;

import java.io.Serializable;

public record CheckersMoveRequest(
        String sessionId,
        String roomId,
        int fromRow,
        int fromCol,
        int toRow,
        int toCol) implements Serializable {
}
