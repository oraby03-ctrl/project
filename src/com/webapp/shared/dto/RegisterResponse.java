package com.webapp.shared.dto;

import java.io.Serializable;

public record RegisterResponse(String sessionId, String username) implements Serializable {
}
