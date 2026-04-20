package com.webapp.server.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class PlayerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String playerId;

    @Column(nullable = false, length = 40)
    private String playerName;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(nullable = false)
    private int draws;

    @Column(length = 64)
    private String passwordHash;

    @Column(length = 32)
    private String passwordSalt;

    public PlayerEntity() {
    }

    public PlayerEntity(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
    }

    public PlayerEntity(String playerId, String playerName, String passwordHash, String passwordSalt) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
    }

    public Long getId() {
        return id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public void incrementWin() {
        wins++;
    }

    public void incrementLoss() {
        losses++;
    }

    public void incrementDraw() {
        draws++;
    }

    public void resetStats() {
        wins = 0;
        losses = 0;
        draws = 0;
    }
}
