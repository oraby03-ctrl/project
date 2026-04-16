package com.webapp.server.application;

import com.webapp.server.domain.GameRoom;
import com.webapp.server.domain.MatchTicketState;
import com.webapp.server.domain.MoveOutcome;
import com.webapp.server.domain.PlayerSessionRegistry;
import com.webapp.server.domain.SessionInfo;
import com.webapp.server.infrastructure.PersistenceService;
import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;
import com.webapp.shared.dto.MatchStatus;
import com.webapp.shared.dto.MatchTicket;
import com.webapp.shared.dto.MoveRequest;
import com.webapp.shared.dto.MoveResult;
import com.webapp.shared.dto.PlayerProfileView;
import com.webapp.shared.dto.RegisterResponse;

import java.util.Arrays;
import java.util.List;

public class GamePlatformService {
    private static final long HEARTBEAT_TIMEOUT_MS = 12000;

    private final PlayerSessionRegistry sessions;
    private final MatchmakingService matchmaking;
    private final PersistenceService persistenceService;

    public GamePlatformService(PlayerSessionRegistry sessions,
                               MatchmakingService matchmaking,
                               PersistenceService persistenceService) {
        this.sessions = sessions;
        this.matchmaking = matchmaking;
        this.persistenceService = persistenceService;
    }

    public RegisterResponse registerPlayer(String playerName) {
        validatePlayerName(playerName);
        String generatedPlayerId = "legacy-" + playerName.trim().toLowerCase().replace(" ", "-");
        return registerPlayer(generatedPlayerId, playerName);
    }

    public RegisterResponse registerPlayer(String playerId, String playerName) {
        validatePlayerId(playerId);
        validatePlayerName(playerName);
        String cleanId = playerId.trim();
        String cleanName = playerName.trim();

        persistenceService.ensurePlayer(cleanId, cleanName);
        String sessionId = sessions.register(cleanId, cleanName);
        return new RegisterResponse(sessionId, cleanName);
    }

    public PlayerProfileView getProfile(String sessionId) {
        String playerId = sessions.requirePlayerId(sessionId);
        return persistenceService.getProfile(playerId);
    }

    public MatchTicket requestMatch(String sessionId, GameType gameType) {
        String playerId = sessions.requirePlayerId(sessionId);
        if (gameType != GameType.TIC_TAC_TOE) {
            return new MatchTicket("", "Only TIC_TAC_TOE is currently implemented");
        }
        String ticketId = matchmaking.enqueue(playerId, gameType);
        return new MatchTicket(ticketId, "Queued for matchmaking");
    }

    public MatchStatus pollMatch(String sessionId, String ticketId) {
        sessions.requireSession(sessionId);
        MatchTicketState ticket = matchmaking.getTicketState(ticketId);
        if (ticket == null) {
            return new MatchStatus(false, null, null, null, "Ticket not found");
        }
        if (!ticket.isMatched()) {
            return new MatchStatus(false, null, null, null, "Waiting for another player...");
        }
        return new MatchStatus(true, ticket.getRoomId(), ticket.getSymbol(), ticket.getOpponent(), "Match found");
    }

    public MoveResult makeMove(MoveRequest moveRequest) {
        String playerId = sessions.requirePlayerId(moveRequest.sessionId());
        GameRoom room = matchmaking.requireRoomByPlayer(playerId, moveRequest.roomId());
        room.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);

        MoveOutcome outcome = room.makeMove(playerId, moveRequest.row(), moveRequest.col());
        if (!outcome.accepted()) {
            return new MoveResult(false, outcome.message());
        }

        String symbol = room.symbolFor(playerId);
        persistenceService.logMove(room.getRoomId(), outcome.moveNumber(), playerId, symbol,
                moveRequest.row(), moveRequest.col());

        persistIfFinished(room);
        return new MoveResult(true, outcome.message());
    }

    public GameStateView getGameState(String sessionId, String roomId) {
        String playerId = sessions.requirePlayerId(sessionId);
        GameRoom room = matchmaking.requireRoomByPlayer(playerId, roomId);
        room.touch(playerId);
        room.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);
        persistIfFinished(room);
        return room.snapshot();
    }

    public void heartbeat(String sessionId, String roomId) {
        String playerId = sessions.requirePlayerId(sessionId);
        GameRoom room = matchmaking.requireRoomByPlayer(playerId, roomId);
        room.touch(playerId);
        room.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);
        persistIfFinished(room);
    }

    public void leaveGame(String sessionId, String roomId) {
        String playerId = sessions.requirePlayerId(sessionId);
        GameRoom room = matchmaking.requireRoomByPlayer(playerId, roomId);
        room.forfeit(playerId);
        persistIfFinished(room);
    }

    public SessionInfo getSessionInfo(String sessionId) {
        return sessions.requireSession(sessionId);
    }

    public List<GameType> listSupportedGames() {
        return Arrays.asList(GameType.values());
    }

    private void persistIfFinished(GameRoom room) {
        if (room.markPersistedIfNeeded()) {
            GameStateView state = room.snapshot();
            persistenceService.persistFinishedGame(
                    room.getRoomId(),
                    GameType.TIC_TAC_TOE.name(),
                    room.getPlayerX(),
                    room.getPlayerO(),
                    state.winner(),
                    state.draw()
            );
        }
    }

    private void validatePlayerName(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Player name is required");
        }
        if (playerName.trim().length() < 3 || playerName.trim().length() > 24) {
            throw new IllegalArgumentException("Player name length must be 3..24");
        }
    }

    private void validatePlayerId(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID is required");
        }
        String clean = playerId.trim();
        if (clean.length() < 3 || clean.length() > 24) {
            throw new IllegalArgumentException("Player ID length must be 3..24");
        }
    }
}
