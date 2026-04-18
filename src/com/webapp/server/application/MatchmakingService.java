package com.webapp.server.application;

import com.webapp.server.domain.GameRoom;
import com.webapp.server.domain.MatchRequest;
import com.webapp.server.domain.MatchTicketState;
import com.webapp.shared.dto.GameType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
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
    private final Set<String> playersQueuing = ConcurrentHashMap.newKeySet();
    private final ExecutorService matcherExecutor = Executors.newSingleThreadExecutor();

    public MatchmakingService() {
        for (GameType gameType : GameType.values()) {
            queues.put(gameType, new LinkedBlockingQueue<>());
        }
        matcherExecutor.submit(this::matchLoop);
    }

    public String enqueue(String playerId, String playerName, GameType gameType) {
        if (!playersQueuing.add(playerId)) {
            // Player has a stale queue entry (e.g. browser was closed without leaving).
            // Cancel it so they can search again with a clean slate.
            cancelPendingRequests(playerId);
            playersQueuing.add(playerId);
        }
        String activeRoomId = playerToRoom.get(playerId);
        if (activeRoomId != null) {
            GameRoom activeRoom = rooms.get(activeRoomId);
            if (activeRoom != null && !activeRoom.isFinished()) {
                playersQueuing.remove(playerId);
                throw new IllegalStateException("Already in an active game");
            }
        }
        String ticketId = UUID.randomUUID().toString();
        MatchTicketState state = new MatchTicketState(ticketId);
        tickets.put(ticketId, state);
        queues.get(gameType).offer(new MatchRequest(ticketId, playerId, playerName, gameType));
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
                        if (p1 != null) queue.offer(p1);
                        if (p2 != null) queue.offer(p2);
                        continue;
                    }
                    if (p1.playerId().equals(p2.playerId())) {
                        // same player queued from two tabs — put both back and wait for a real opponent
                        queue.offer(p1);
                        queue.offer(p2);
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

    private void cancelPendingRequests(String playerId) {
        for (BlockingQueue<MatchRequest> queue : queues.values()) {
            queue.removeIf(req -> req.playerId().equals(playerId));
        }
    }

    private void createRoomForPair(MatchRequest p1, MatchRequest p2) {
        playersQueuing.remove(p1.playerId());
        playersQueuing.remove(p2.playerId());
        GameRoom room = new GameRoom(p1.playerId(), p1.playerName(), p2.playerId(), p2.playerName());
        rooms.put(room.getRoomId(), room);
        playerToRoom.put(p1.playerId(), room.getRoomId());
        playerToRoom.put(p2.playerId(), room.getRoomId());

        MatchTicketState s1 = tickets.get(p1.ticketId());
        MatchTicketState s2 = tickets.get(p2.ticketId());
        if (s1 != null) {
            s1.setMatchedData(room.getRoomId(), "X", p2.playerName());
        }
        if (s2 != null) {
            s2.setMatchedData(room.getRoomId(), "O", p1.playerName());
        }
    }
}
