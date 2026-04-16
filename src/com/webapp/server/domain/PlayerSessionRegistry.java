package com.webapp.server.domain;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSessionRegistry {
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    public String register(String playerId, String playerName) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new SessionInfo(sessionId, playerId, playerName));
        return sessionId;
    }

    public SessionInfo requireSession(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Invalid session");
        }
        return session;
    }

    public String requirePlayerId(String sessionId) {
        return requireSession(sessionId).playerId();
    }

    public String requirePlayerName(String sessionId) {
        return requireSession(sessionId).playerName();
    }
}
