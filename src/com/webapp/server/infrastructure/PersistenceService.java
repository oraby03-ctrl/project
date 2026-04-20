package com.webapp.server.infrastructure;

import com.webapp.server.infrastructure.jdbc.JdbcAuditRepository;
import com.webapp.server.infrastructure.jpa.GameRecordEntity;
import com.webapp.server.infrastructure.jpa.GameRecordJpaRepository;
import com.webapp.server.infrastructure.jpa.JpaBootstrap;
import com.webapp.server.infrastructure.jpa.PlayerJpaRepository;
import com.webapp.shared.dto.PlayerProfileView;

import java.util.List;

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

    /** Registers a brand-new player account with a password. Fails if the player ID already exists. */
    public void registerNewPlayer(String playerId, String playerName, String password) {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        playerRepository.createNewPlayer(playerId, playerName, hash, salt);
    }

    /**
     * Verifies credentials for an existing player.
     * Returns the player's display name on success, throws on failure.
     */
    public String verifyLogin(String playerId, String password) {
        return playerRepository.verifyLogin(playerId, password);
    }

    /**
     * Ensures an account exists with a password; creates it if absent, or sets the password if
     * the account has none (used for bootstrapping built-in accounts such as admin).
     */
    public void ensurePlayerWithPassword(String playerId, String playerName, String password) {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        playerRepository.ensureExistsWithPassword(playerId, playerName, hash, salt);
    }

    public PlayerProfileView getProfile(String playerId) {
        return playerRepository.getProfile(playerId);
    }

    public List<PlayerProfileView> getScoreboard() {
        return playerRepository.getAllProfiles();
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

    public void deletePlayer(String playerId) {
        playerRepository.deletePlayer(playerId);
    }

    public void resetPlayerStats(String playerId) {
        playerRepository.resetStats(playerId);
    }

    public java.util.List<GameRecordEntity> getRecentGames(int limit) {
        return gameRecordRepository.getRecentGames(limit);
    }
}
