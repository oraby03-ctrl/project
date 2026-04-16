package com.webapp.server.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "game_records")
public class GameRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String roomId;

    @Column(nullable = false)
    private String gameType;

    @Column(nullable = false)
    private String playerX;

    @Column(nullable = false)
    private String playerO;

    @Column
    private String winner;

    @Column(nullable = false)
    private boolean draw;

    @Column(nullable = false)
    private Instant finishedAt;

    public GameRecordEntity() {
    }

    public GameRecordEntity(String roomId, String gameType, String playerX, String playerO, String winner, boolean draw) {
        this.roomId = roomId;
        this.gameType = gameType;
        this.playerX = playerX;
        this.playerO = playerO;
        this.winner = winner;
        this.draw = draw;
        this.finishedAt = Instant.now();
    }
}
