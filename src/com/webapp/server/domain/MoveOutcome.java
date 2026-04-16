package com.webapp.server.domain;

public record MoveOutcome(boolean accepted, String message, int moveNumber, boolean finished) {
}
