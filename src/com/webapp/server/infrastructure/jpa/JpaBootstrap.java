package com.webapp.server.infrastructure.jpa;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public final class JpaBootstrap {
    private static final EntityManagerFactory EMF = Persistence.createEntityManagerFactory("game-platform-pu");

    private JpaBootstrap() {
    }

    public static EntityManagerFactory emf() {
        return EMF;
    }
}
