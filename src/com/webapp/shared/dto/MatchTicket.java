package com.webapp.shared.dto;

import java.io.Serializable;

public record MatchTicket(String ticketId, String message) implements Serializable {
}
