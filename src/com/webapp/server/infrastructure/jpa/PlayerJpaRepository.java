package com.webapp.server.infrastructure.jpa;

import com.webapp.shared.dto.PlayerProfileView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;

public class PlayerJpaRepository {
    private final EntityManagerFactory entityManagerFactory;

    public PlayerJpaRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void ensureExists(String playerId, String playerName) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            PlayerEntity player = getByPlayerId(em, playerId);
            if (player == null) {
                em.persist(new PlayerEntity(playerId, playerName));
            } else {
                player.setPlayerName(playerName);
            }
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    public PlayerProfileView getProfile(String playerId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            PlayerEntity player = getByPlayerId(em, playerId);
            if (player == null) {
                throw new IllegalArgumentException("Player not found");
            }
            int score = player.getWins() * 3 + player.getDraws();
            return new PlayerProfileView(
                    player.getPlayerId(),
                    player.getPlayerName(),
                    player.getWins(),
                    player.getLosses(),
                    player.getDraws(),
                    score
            );
        } finally {
            em.close();
        }
    }

    public void updateStats(String playerXId, String playerOId, String winnerPlayerId, boolean draw) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            PlayerEntity x = getByPlayerId(em, playerXId);
            PlayerEntity o = getByPlayerId(em, playerOId);
            if (x == null || o == null) {
                throw new IllegalStateException("Players must exist before updating stats");
            }

            if (draw) {
                x.incrementDraw();
                o.incrementDraw();
            } else if (playerXId.equals(winnerPlayerId)) {
                x.incrementWin();
                o.incrementLoss();
            } else if (playerOId.equals(winnerPlayerId)) {
                o.incrementWin();
                x.incrementLoss();
            }
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    public List<PlayerProfileView> getAllProfiles() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            List<PlayerEntity> players = em.createQuery(
                    "select p from PlayerEntity p order by (p.wins * 3 + p.draws) desc", PlayerEntity.class)
                    .getResultList();
            return players.stream().map(p -> new PlayerProfileView(
                    p.getPlayerId(),
                    p.getPlayerName(),
                    p.getWins(),
                    p.getLosses(),
                    p.getDraws(),
                    p.getWins() * 3 + p.getDraws()
            )).toList();
        } finally {
            em.close();
        }
    }

    private PlayerEntity getByPlayerId(EntityManager em, String playerId) {
        List<PlayerEntity> result = em.createQuery("select p from PlayerEntity p where p.playerId = :playerId", PlayerEntity.class)
                .setParameter("playerId", playerId)
                .setMaxResults(1)
                .getResultList();
        return result.isEmpty() ? null : result.get(0);
    }
}
