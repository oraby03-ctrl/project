package com.webapp.shared.dto;

import java.io.Serializable;

public record MatchStatus(boolean matched, String roomId, String symbol, String opponent, String message)
        implements Serializable {
}
