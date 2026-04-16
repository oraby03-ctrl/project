package com.webapp.server.presentation;

import com.webapp.server.application.GamePlatformService;
import com.webapp.server.application.MatchmakingService;
import com.webapp.server.domain.PlayerSessionRegistry;
import com.webapp.server.infrastructure.PersistenceService;

public class ServerMain {
    public static final String BINDING_NAME = "GameServer";

    public static void main(String[] args) {
        try {
            String jdbcUrl = "jdbc:sqlite:web-game-platform.db";

            PlayerSessionRegistry sessions = new PlayerSessionRegistry();
            MatchmakingService matchmakingService = new MatchmakingService();
            PersistenceService persistenceService = new PersistenceService(jdbcUrl);
            GamePlatformService gamePlatformService = new GamePlatformService(
                    sessions,
                    matchmakingService,
                    persistenceService
            );

            WebAppHttpServer webServer = new WebAppHttpServer(gamePlatformService);
            webServer.start(8080);

            System.out.println("Web server started at http://localhost:8080");
        } catch (Exception exception) {
            System.err.println("Failed to start server: " + exception.getMessage());
            exception.printStackTrace();
        }
    }
}
