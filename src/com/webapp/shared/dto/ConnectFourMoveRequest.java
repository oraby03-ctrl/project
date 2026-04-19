package com.webapp.shared.dto;

public record ConnectFourMoveRequest(String sessionId, String roomId, int col) {
}
