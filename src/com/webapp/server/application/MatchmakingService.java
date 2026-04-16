package com.webapp.server.application;

import com.webapp.server.domain.GameRoom;
import com.webapp.server.domain.MatchRequest;
import com.webapp.server.domain.MatchTicketState;
import com.webapp.shared.dto.GameType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class MatchmakingService {
    private final Map<GameType, BlockingQueue<MatchRequest>> queues = new EnumMap<>(GameType.class);
    private final Map<String, MatchTicketState> tickets = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> playerToRoom = new ConcurrentHashMap<>();
    private final ExecutorService matcherExecutor = Executors.newSingleThreadExecutor();

    public MatchmakingService() {
        for (GameType gameType : GameType.values()) {
            queues.put(gameType, new LinkedBlockingQueue<>());
        }
        matcherExecutor.submit(this::matchLoop);
    }

    public String enqueue(String playerId, GameType gameType) {
        String ticketId = UUID.randomUUID().toString();
        MatchTicketState state = new MatchTicketState(ticketId);
        tickets.put(ticketId, state);
        queues.get(gameType).offer(new MatchRequest(ticketId, playerId, gameType));
        return ticketId;
    }

    public MatchTicketState getTicketState(String ticketId) {
        return tickets.get(ticketId);
    }

    public GameRoom requireRoomByPlayer(String playerId, String roomId) {
        String current = playerToRoom.get(playerId);
        if (current == null || !current.equals(roomId)) {
            throw new IllegalArgumentException("Player is not assigned to room");
        }
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }
        return room;
    }

    private void matchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                for (Map.Entry<GameType, BlockingQueue<MatchRequest>> entry : queues.entrySet()) {
                    BlockingQueue<MatchRequest> queue = entry.getValue();
                    if (queue.size() < 2) {
                        continue;
                    }
                    MatchRequest p1 = queue.poll();
                    MatchRequest p2 = queue.poll();
                    if (p1 == null || p2 == null) {
                        continue;
                    }
                    createRoomForPair(p1, p2);
                }
                Thread.sleep(80);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void createRoomForPair(MatchRequest p1, MatchRequest p2) {
        GameRoom room = new GameRoom(p1.username(), p2.username());
        rooms.put(room.getRoomId(), room);
        playerToRoom.put(p1.username(), room.getRoomId());
        playerToRoom.put(p2.username(), room.getRoomId());

        MatchTicketState s1 = tickets.get(p1.ticketId());
        MatchTicketState s2 = tickets.get(p2.ticketId());
        if (s1 != null) {
            s1.setMatchedData(room.getRoomId(), "X", p2.username());
        }
        if (s2 != null) {
            s2.setMatchedData(room.getRoomId(), "O", p1.username());
        }
    }
}
