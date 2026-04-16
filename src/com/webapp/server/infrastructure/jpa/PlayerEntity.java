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

    public PlayerEntity() {
    }

    public PlayerEntity(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
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

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
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
}
