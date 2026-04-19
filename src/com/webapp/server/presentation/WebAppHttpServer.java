package com.webapp.server.presentation;

import com.webapp.server.application.GamePlatformService;
import com.webapp.shared.dto.CheckersMoveRequest;
import com.webapp.shared.dto.ConnectFourMoveRequest;
import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;
import com.webapp.shared.dto.MatchStatus;
import com.webapp.shared.dto.MatchTicket;
import com.webapp.shared.dto.MoveRequest;
import com.webapp.shared.dto.MoveResult;
import com.webapp.shared.dto.PlayerProfileView;
import com.webapp.shared.dto.RegisterResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebAppHttpServer {
    private final GamePlatformService service;
    private HttpServer httpServer;

    public WebAppHttpServer(GamePlatformService service) {
        this.service = service;
    }

    public void start(int port) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/", this::handleIndex);
        httpServer.createContext("/api/login", this::handleLogin);
        httpServer.createContext("/api/profile", this::handleProfile);
        httpServer.createContext("/api/games", this::handleGames);
        httpServer.createContext("/api/match/request", this::handleMatchRequest);
        httpServer.createContext("/api/match/poll", this::handleMatchPoll);
        httpServer.createContext("/api/game/state", this::handleGameState);
        httpServer.createContext("/api/game/move", this::handleGameMove);
        httpServer.createContext("/api/game/checkers/move", this::handleCheckersMove);
        httpServer.createContext("/api/game/connectfour/move", this::handleConnectFourMove);
        httpServer.createContext("/api/game/heartbeat", this::handleHeartbeat);
        httpServer.createContext("/api/game/leave", this::handleLeave);
        httpServer.createContext("/api/scoreboard", this::handleScoreboard);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.start();
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed", "text/plain");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path)) {
            sendText(exchange, 404, "Not Found", "text/plain");
            return;
        }

        InputStream in = getClass().getClassLoader().getResourceAsStream("web/index.html");
        if (in == null) {
            sendText(exchange, 500, "web/index.html not found in resources", "text/plain");
            return;
        }
        byte[] bytes = in.readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            String playerId = required(form, "playerId");
            String playerName = required(form, "playerName");

            RegisterResponse registerResponse = service.registerPlayer(playerId, playerName);
            PlayerProfileView profile = service.getProfile(registerResponse.sessionId());
            String body = "{\"sessionId\":\"" + esc(registerResponse.sessionId()) + "\","
                    + "\"playerName\":\"" + esc(registerResponse.username()) + "\","
                    + "\"profile\":" + profileToJson(profile) + "}";
            sendJson(exchange, 200, body);
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleProfile(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            String sessionId = required(parseQuery(exchange), "sessionId");
            PlayerProfileView profile = service.getProfile(sessionId);
            sendJson(exchange, 200, profileToJson(profile));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleGames(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        List<GameType> games = service.listSupportedGames();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"games\":[");
        for (int i = 0; i < games.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(esc(games.get(i).name())).append('"');
        }
        sb.append("]}");
        sendJson(exchange, 200, sb.toString());
    }

    private void handleMatchRequest(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            String sessionId = required(form, "sessionId");
            String gameTypeText = required(form, "gameType");
            GameType gameType = Arrays.stream(GameType.values())
                    .filter(g -> g.name().equalsIgnoreCase(gameTypeText))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown game type"));
            MatchTicket ticket = service.requestMatch(sessionId, gameType);
            sendJson(exchange, 200, ticketToJson(ticket));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleMatchPoll(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> query = parseQuery(exchange);
            String sessionId = required(query, "sessionId");
            String ticketId = required(query, "ticketId");
            MatchStatus status = service.pollMatch(sessionId, ticketId);
            sendJson(exchange, 200, matchStatusToJson(status));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleGameState(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> query = parseQuery(exchange);
            String sessionId = required(query, "sessionId");
            String roomId = required(query, "roomId");
            GameStateView state = service.getGameState(sessionId, roomId);
            sendJson(exchange, 200, gameStateToJson(state));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleConnectFourMove(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            MoveResult result = service.makeConnectFourMove(new ConnectFourMoveRequest(
                    required(form, "sessionId"),
                    required(form, "roomId"),
                    Integer.parseInt(required(form, "col"))
            ));
            sendJson(exchange, 200, moveResultToJson(result));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleCheckersMove(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            MoveResult result = service.makeCheckersMove(new CheckersMoveRequest(
                    required(form, "sessionId"),
                    required(form, "roomId"),
                    Integer.parseInt(required(form, "fromRow")),
                    Integer.parseInt(required(form, "fromCol")),
                    Integer.parseInt(required(form, "toRow")),
                    Integer.parseInt(required(form, "toCol"))
            ));
            sendJson(exchange, 200, moveResultToJson(result));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleGameMove(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            MoveResult result = service.makeMove(new MoveRequest(
                    required(form, "sessionId"),
                    required(form, "roomId"),
                    Integer.parseInt(required(form, "row")),
                    Integer.parseInt(required(form, "col"))
            ));
            sendJson(exchange, 200, moveResultToJson(result));
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleHeartbeat(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            service.heartbeat(required(form, "sessionId"), required(form, "roomId"));
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleLeave(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            Map<String, String> form = parseFormBody(exchange);
            service.leaveGame(required(form, "sessionId"), required(form, "roomId"));
            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private void handleScoreboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, jsonError("Method Not Allowed"));
            return;
        }
        try {
            var board = service.getScoreboard();
            StringBuilder sb = new StringBuilder("{\"scoreboard\":[");
            for (int i = 0; i < board.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(profileToJson(board.get(i)));
            }
            sb.append("]}");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception ex) {
            sendJson(exchange, 400, jsonError(ex.getMessage()));
        }
    }

    private Map<String, String> parseQuery(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        return parseFormEncoded(raw == null ? "" : raw);
    }

    private Map<String, String> parseFormBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseFormEncoded(body);
    }

    private Map<String, String> parseFormEncoded(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String required(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    private String profileToJson(PlayerProfileView profile) {
        return "{\"playerId\":\"" + esc(profile.playerId()) + "\","
                + "\"playerName\":\"" + esc(profile.playerName()) + "\","
                + "\"wins\":" + profile.wins() + ","
                + "\"losses\":" + profile.losses() + ","
                + "\"draws\":" + profile.draws() + ","
                + "\"score\":" + profile.score() + "}";
    }

    private String ticketToJson(MatchTicket ticket) {
        return "{\"ticketId\":\"" + esc(ticket.ticketId()) + "\","
                + "\"message\":\"" + esc(ticket.message()) + "\"}";
    }

    private String matchStatusToJson(MatchStatus status) {
        return "{\"matched\":" + status.matched() + ","
                + "\"roomId\":" + maybeString(status.roomId()) + ","
                + "\"symbol\":" + maybeString(status.symbol()) + ","
                + "\"opponent\":" + maybeString(status.opponent()) + ","
                + "\"gameType\":" + maybeString(status.gameType()) + ","
                + "\"message\":\"" + esc(status.message()) + "\"}";
    }

    private String moveResultToJson(MoveResult result) {
        return "{\"accepted\":" + result.accepted() + ","
                + "\"message\":\"" + esc(result.message()) + "\"}";
    }

    private String gameStateToJson(GameStateView state) {
        StringBuilder boardJson = new StringBuilder("[");
        for (int r = 0; r < state.board().length; r++) {
            if (r > 0) {
                boardJson.append(',');
            }
            boardJson.append('[');
            for (int c = 0; c < state.board()[r].length; c++) {
                if (c > 0) {
                    boardJson.append(',');
                }
                boardJson.append(maybeString(state.board()[r][c]));
            }
            boardJson.append(']');
        }
        boardJson.append(']');

        return "{\"roomId\":\"" + esc(state.roomId()) + "\","
                + "\"board\":" + boardJson + ","
                + "\"currentTurn\":\"" + esc(state.currentTurn()) + "\","
                + "\"winner\":" + maybeString(state.winner()) + ","
                + "\"draw\":" + state.draw() + ","
                + "\"finished\":" + state.finished() + ","
                + "\"playerX\":\"" + esc(state.playerX()) + "\","
                + "\"playerO\":\"" + esc(state.playerO()) + "\","
                + "\"playerXName\":\"" + esc(state.playerXName()) + "\","
                + "\"playerOName\":\"" + esc(state.playerOName()) + "\","
                + "\"statusMessage\":" + maybeString(state.statusMessage()) + "}";
    }

    private String jsonError(String message) {
        return "{\"error\":\"" + esc(message == null ? "Unknown error" : message) + "\"}";
    }

    private String maybeString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + esc(value) + "\"";
    }

    private String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendText(exchange, statusCode, json, "application/json; charset=utf-8");
    }

    private void sendText(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}