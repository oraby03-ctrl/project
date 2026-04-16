package com.webapp.server.infrastructure.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

public class GameRecordJpaRepository {
    private final EntityManagerFactory entityManagerFactory;

    public GameRecordJpaRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public void saveFinishedGame(String roomId,
                                 String gameType,
                                 String playerX,
                                 String playerO,
                                 String winner,
                                 boolean draw) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(new GameRecordEntity(roomId, gameType, playerX, playerO, winner, draw));
            em.getTransaction().commit();
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }
}
