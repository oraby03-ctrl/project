package com.webapp.shared.dto;

import java.io.Serializable;

public record MoveResult(boolean accepted, String message) implements Serializable {
}
