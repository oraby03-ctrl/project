package com.webapp.server.infrastructure;

import com.webapp.server.infrastructure.jdbc.JdbcAuditRepository;
import com.webapp.server.infrastructure.jpa.GameRecordJpaRepository;
import com.webapp.server.infrastructure.jpa.JpaBootstrap;
import com.webapp.server.infrastructure.jpa.PlayerJpaRepository;
import com.webapp.shared.dto.PlayerProfileView;

public class PersistenceService {
    private final PlayerJpaRepository playerRepository;
    private final GameRecordJpaRepository gameRecordRepository;
    private final JdbcAuditRepository auditRepository;

    public PersistenceService(String jdbcUrl) {
        this.playerRepository = new PlayerJpaRepository(JpaBootstrap.emf());
        this.gameRecordRepository = new GameRecordJpaRepository(JpaBootstrap.emf());
        this.auditRepository = new JdbcAuditRepository(jdbcUrl);
    }

    public void ensurePlayer(String playerId, String playerName) {
        playerRepository.ensureExists(playerId, playerName);
    }

    public PlayerProfileView getProfile(String playerId) {
        return playerRepository.getProfile(playerId);
    }

    public void logMove(String roomId, int moveNo, String playerId, String symbol, int row, int col) {
        auditRepository.logMove(roomId, moveNo, playerId, symbol, row, col);
    }

    public void persistFinishedGame(String roomId,
                                    String gameType,
                                    String playerX,
                                    String playerO,
                                    String winner,
                                    boolean draw) {
        gameRecordRepository.saveFinishedGame(roomId, gameType, playerX, playerO, winner, draw);
        playerRepository.updateStats(playerX, playerO, winner, draw);
    }
}
