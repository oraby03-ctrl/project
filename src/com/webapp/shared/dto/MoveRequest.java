package com.webapp.shared.dto;

import java.io.Serializable;

public record MoveRequest(String sessionId, String roomId, int row, int col) implements Serializable {
}
