package com.webapp.client.application;

import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;
import com.webapp.shared.dto.MatchStatus;
import com.webapp.shared.dto.MatchTicket;
import com.webapp.shared.dto.MoveRequest;
import com.webapp.shared.dto.MoveResult;
import com.webapp.shared.dto.RegisterResponse;
import com.webapp.shared.remote.GameServerRemote;

public class ClientGameService {
    private final GameServerRemote remote;

    public ClientGameService(GameServerRemote remote) {
        this.remote = remote;
    }

    public RegisterResponse register(String username) {
        try {
            return remote.registerPlayer(username);
        } catch (Exception exception) {
            throw new RuntimeException("Registration failed", exception);
        }
    }

    public MatchTicket requestTicTacToe(String sessionId) {
        try {
            return remote.requestMatch(sessionId, GameType.TIC_TAC_TOE);
        } catch (Exception exception) {
            throw new RuntimeException("Match request failed", exception);
        }
    }

    public MatchStatus pollMatch(String sessionId, String ticketId) {
        try {
            return remote.pollMatch(sessionId, ticketId);
        } catch (Exception exception) {
            throw new RuntimeException("Polling match failed", exception);
        }
    }

    public MoveResult move(String sessionId, String roomId, int row, int col) {
        try {
            return remote.makeMove(new MoveRequest(sessionId, roomId, row, col));
        } catch (Exception exception) {
            throw new RuntimeException("Move failed", exception);
        }
    }

    public GameStateView state(String sessionId, String roomId) {
        try {
            return remote.getGameState(sessionId, roomId);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to fetch game state", exception);
        }
    }
}
