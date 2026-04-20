package com.webapp.server.presentation;

import com.webapp.server.application.GamePlatformService;
import com.webapp.server.application.MatchmakingService;
import com.webapp.server.domain.PlayerSessionRegistry;
import com.webapp.server.infrastructure.PersistenceService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;

public class ServerMain {
    public static final String BINDING_NAME = "GameServer";

    // Path to ngrok executable — update if installed elsewhere
    private static final String NGROK_PATH = "C:\\Users\\oraby\\Downloads\\ngrok.exe";

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

            // Start RMI server
            int rmiPort = 1099;
            Registry rmiRegistry = LocateRegistry.createRegistry(rmiPort);
            RmiGameServer rmiServer = new RmiGameServer(gamePlatformService);
            rmiRegistry.rebind(BINDING_NAME, rmiServer);

            // Ensure the built-in admin account always exists with a default password.
            // The password is only set if the account has none yet (first run).
            persistenceService.ensurePlayerWithPassword("admin", "admin", "admin");

            System.out.println("==============================================");
            System.out.println(" Game server running on port 8080");
            System.out.println("----------------------------------------------");
            System.out.println(" Local:   http://localhost:8080");
            System.out.println(" RMI:     rmi://localhost:1099/" + BINDING_NAME);
            printLanAddresses(8080);

            // Start ngrok and print the public URL
            startNgrok(8080);

            System.out.println("==============================================");
        } catch (Exception exception) {
            System.err.println("Failed to start server: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private static void startNgrok(int port) {
        try {
            System.out.println("----------------------------------------------");
            System.out.println(" Starting ngrok tunnel...");

            Process process = new ProcessBuilder(NGROK_PATH, "http", String.valueOf(port))
                    .redirectErrorStream(true)
                    .start();

            // Drain ngrok output in a background thread (prevents blocking)
            Thread.ofVirtual().start(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    reader.lines().forEach(line -> {}); // discard — we poll the API instead
                } catch (Exception ignored) {}
            });

            // Poll the ngrok local API until the tunnel URL is available (up to 5 seconds)
            HttpClient client = HttpClient.newHttpClient();
            String publicUrl = null;
            for (int attempt = 0; attempt < 10 && publicUrl == null; attempt++) {
                Thread.sleep(500);
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:4040/api/tunnels"))
                            .build();
                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();
                    // Extract the public_url value for an https tunnel
                    int idx = body.indexOf("\"public_url\":\"https://");
                    if (idx != -1) {
                        int start = idx + 14;
                        int end = body.indexOf('"', start);
                        publicUrl = body.substring(start, end);
                    }
                } catch (Exception ignored) {}
            }

            if (publicUrl != null) {
                System.out.println(" Public:  " + publicUrl + "  ← share with players");
            } else {
                System.out.println(" ngrok started but could not read public URL.");
                System.out.println(" Check http://127.0.0.1:4040 in your browser.");
            }

            // Shut down ngrok gracefully when the JVM exits
            Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));

        } catch (Exception e) {
            System.out.println(" ngrok not started: " + e.getMessage());
            System.out.println(" Run manually: " + NGROK_PATH + " http " + port);
        }
    }

    private static void printLanAddresses(int port) {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address) {
                        System.out.printf(" Network: http://%s:%d  (%s)%n",
                                addr.getHostAddress(), port, iface.getDisplayName());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
