package com.webapp.server.domain;

import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;

public interface IRoom {
    String getRoomId();
    String getPlayerX();
    String getPlayerO();
    GameType getGameType();
    boolean isFinished();
    void touch(String playerId);
    void applyDisconnectForfeitIfNeeded(long timeoutMs);
    void forfeit(String quittingPlayerId);
    GameStateView snapshot();
    boolean markPersistedIfNeeded();
    int getMoveCount();
}
