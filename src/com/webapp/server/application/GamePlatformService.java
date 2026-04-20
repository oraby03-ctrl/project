package com.webapp.server.application;

import com.webapp.server.domain.CheckersRoom;
import com.webapp.server.domain.ConnectFourRoom;
import com.webapp.server.domain.GameRoom;
import com.webapp.server.domain.IRoom;
import com.webapp.server.domain.MatchTicketState;
import com.webapp.server.domain.MoveOutcome;
import com.webapp.server.domain.PlayerSessionRegistry;
import com.webapp.server.infrastructure.PersistenceService;
import com.webapp.shared.dto.CheckersMoveRequest;
import com.webapp.shared.dto.ConnectFourMoveRequest;
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
        String playerName = sessions.requirePlayerName(sessionId);
        if ("admin".equals(playerId)) {
            throw new IllegalStateException("Admin account cannot play games");
        }
        if (gameType == GameType.BATTLESHIP) {
            return new MatchTicket("", "Battleship is under development");
        }
        String ticketId = matchmaking.enqueue(playerId, playerName, gameType);
        return new MatchTicket(ticketId, "Queued for matchmaking");
    }

    public MatchStatus pollMatch(String sessionId, String ticketId) {
        sessions.requireSession(sessionId);
        MatchTicketState ticket = matchmaking.getTicketState(ticketId);
        if (ticket == null) {
            return new MatchStatus(false, null, null, null, "Ticket not found", null);
        }
        if (!ticket.isMatched()) {
            return new MatchStatus(false, null, null, null, "Waiting for another player...", null);
        }
        return new MatchStatus(true, ticket.getRoomId(), ticket.getSymbol(), ticket.getOpponent(), "Match found", ticket.getGameType());
    }

    public MoveResult makeMove(MoveRequest moveRequest) {
        String playerId = sessions.requirePlayerId(moveRequest.sessionId());
        IRoom iRoom = matchmaking.requireRoomByPlayer(playerId, moveRequest.roomId());
        iRoom.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);

        if (!(iRoom instanceof GameRoom room)) {
            return new MoveResult(false, "Use the checkers move endpoint for this game");
        }

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

    public MoveResult makeCheckersMove(CheckersMoveRequest req) {
        String playerId = sessions.requirePlayerId(req.sessionId());
        IRoom iRoom = matchmaking.requireRoomByPlayer(playerId, req.roomId());
        iRoom.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);

        if (!(iRoom instanceof CheckersRoom room)) {
            return new MoveResult(false, "This room is not a Checkers game");
        }

        MoveOutcome outcome = room.makeMove(playerId, req.fromRow(), req.fromCol(), req.toRow(), req.toCol());
        if (!outcome.accepted()) {
            return new MoveResult(false, outcome.message());
        }

        String symbol = room.symbolFor(playerId);
        persistenceService.logMove(room.getRoomId(), outcome.moveNumber(), playerId, symbol,
                req.toRow(), req.toCol());

        persistIfFinished(room);
        return new MoveResult(true, outcome.message());
    }

    public GameStateView getGameState(String sessionId, String roomId) {
        String playerId = sessions.requirePlayerId(sessionId);
        IRoom room = matchmaking.requireRoomByPlayer(playerId, roomId);
        room.touch(playerId);
        room.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);
        persistIfFinished(room);
        return room.snapshot();
    }

    public void heartbeat(String sessionId, String roomId) {
        String playerId = sessions.requirePlayerId(sessionId);
        IRoom room = matchmaking.requireRoomByPlayer(playerId, roomId);
        room.touch(playerId);
        room.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);
        persistIfFinished(room);
    }

    public void leaveGame(String sessionId, String roomId) {
        String playerId = sessions.requirePlayerId(sessionId);
        IRoom room = matchmaking.requireRoomByPlayer(playerId, roomId);
        room.forfeit(playerId);
        persistIfFinished(room);
    }

    public MoveResult makeConnectFourMove(ConnectFourMoveRequest req) {
        String playerId = sessions.requirePlayerId(req.sessionId());
        IRoom iRoom = matchmaking.requireRoomByPlayer(playerId, req.roomId());
        iRoom.applyDisconnectForfeitIfNeeded(HEARTBEAT_TIMEOUT_MS);

        if (!(iRoom instanceof ConnectFourRoom room)) {
            return new MoveResult(false, "This room is not a Connect Four game");
        }

        MoveOutcome outcome = room.makeMove(playerId, req.col());
        if (!outcome.accepted()) {
            return new MoveResult(false, outcome.message());
        }

        String symbol = room.symbolFor(playerId);
        persistenceService.logMove(room.getRoomId(), outcome.moveNumber(), playerId, symbol, 0, req.col());
        persistIfFinished(room);
        return new MoveResult(true, outcome.message());
    }

    public List<GameType> listSupportedGames() {
        return Arrays.asList(GameType.TIC_TAC_TOE, GameType.CHECKERS, GameType.CONNECT_FOUR);
    }

    public List<PlayerProfileView> getScoreboard() {
        return persistenceService.getScoreboard().stream()
                .filter(p -> !"admin".equals(p.playerId()))
                .toList();
    }

    /** Admin-only: deletes another player's account from the database. */
    public void deletePlayer(String adminSessionId, String targetPlayerId) {
        requireAdmin(adminSessionId);
        if ("admin".equals(targetPlayerId)) {
            throw new IllegalArgumentException("Cannot delete the admin account");
        }
        persistenceService.deletePlayer(targetPlayerId);
    }

    /** Admin-only: resets a player's W/D/L stats to zero. */
    public void resetPlayerStats(String adminSessionId, String targetPlayerId) {
        requireAdmin(adminSessionId);
        if ("admin".equals(targetPlayerId)) {
            throw new IllegalArgumentException("Cannot reset the admin account");
        }
        persistenceService.resetPlayerStats(targetPlayerId);
    }

    public java.util.List<java.util.Map<String, Object>> getActiveRooms(String adminSessionId) {
        requireAdmin(adminSessionId);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.webapp.server.domain.IRoom room : matchmaking.getAllRooms()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("roomId",    room.getRoomId());
            m.put("gameType",  room.getGameType().name());
            m.put("playerX",   room.getPlayerX());
            m.put("playerO",   room.getPlayerO());
            m.put("finished",  room.isFinished());
            com.webapp.shared.dto.GameStateView snap = room.snapshot();
            m.put("winner",    snap.winner());
            m.put("draw",      snap.draw());
            result.add(m);
        }
        return result;
    }

    /** Admin-only: returns the most recent finished game records. */
    public java.util.List<com.webapp.server.infrastructure.jpa.GameRecordEntity> getRecentGames(String adminSessionId, int limit) {
        requireAdmin(adminSessionId);
        return persistenceService.getRecentGames(limit);
    }

    /** Admin-only: force-ends a live room (playerX forfeits, playerO wins). */
    public void forceEndRoom(String adminSessionId, String roomId) {
        requireAdmin(adminSessionId);
        matchmaking.forceEndRoom(roomId);
    }

    private void requireAdmin(String sessionId) {
        String callerId = sessions.requirePlayerId(sessionId);
        if (!"admin".equals(callerId)) {
            throw new SecurityException("Access denied: admin only");
        }
    }

    private void persistIfFinished(IRoom room) {
        if (room.isAdminEnded()) {
            room.markPersistedIfNeeded(); // consume the flag so it won't fire again
            return;                        // skip stats update and game record
        }
        if (room.markPersistedIfNeeded()) {
            GameStateView state = room.snapshot();
            persistenceService.persistFinishedGame(
                    room.getRoomId(),
                    room.getGameType().name(),
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

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
    }

    /**
     * Registers a brand-new player account.
     * Throws if the player ID is already taken.
     */
    public RegisterResponse createAccount(String playerId, String playerName, String password) {
        validatePlayerId(playerId);
        validatePlayerName(playerName);
        validatePassword(password);
        String cleanId   = playerId.trim();
        String cleanName = playerName.trim();
        persistenceService.registerNewPlayer(cleanId, cleanName, password);
        String sessionId = sessions.register(cleanId, cleanName);
        return new RegisterResponse(sessionId, cleanName);
    }

    /**
     * Logs in an existing player by verifying their password.
     * Throws if the player does not exist or the password is wrong.
     */
    public RegisterResponse loginPlayer(String playerId, String password) {
        if (playerId == null || playerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Player ID is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        String cleanId = playerId.trim();
        String playerName = persistenceService.verifyLogin(cleanId, password);
        String sessionId = sessions.register(cleanId, playerName);
        return new RegisterResponse(sessionId, playerName);
    }
}
