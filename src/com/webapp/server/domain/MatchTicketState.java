package com.webapp.server.domain;

public class MatchTicketState {
    private final String ticketId;
    private volatile boolean matched;
    private volatile String roomId;
    private volatile String symbol;
    private volatile String opponent;

    public MatchTicketState(String ticketId) {
        this.ticketId = ticketId;
        this.matched = false;
    }

    public String getTicketId() {
        return ticketId;
    }

    public boolean isMatched() {
        return matched;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getOpponent() {
        return opponent;
    }

    public void setMatchedData(String roomId, String symbol, String opponent) {
        this.roomId = roomId;
        this.symbol = symbol;
        this.opponent = opponent;
        this.matched = true;
    }
}
