package com.webapp.server.presentation;

import com.webapp.server.application.GamePlatformService;
import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;
import com.webapp.shared.dto.MatchStatus;
import com.webapp.shared.dto.MatchTicket;
import com.webapp.shared.dto.MoveRequest;
import com.webapp.shared.dto.MoveResult;
import com.webapp.shared.dto.RegisterResponse;
import com.webapp.shared.remote.GameServerRemote;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

public class RmiGameServer extends UnicastRemoteObject implements GameServerRemote {
    private final GamePlatformService service;

    public RmiGameServer(GamePlatformService service) throws RemoteException {
        this.service = service;
    }

    @Override
    public RegisterResponse registerPlayer(String username) throws RemoteException {
        return service.registerPlayer(username);
    }

    @Override
    public MatchTicket requestMatch(String sessionId, GameType gameType) throws RemoteException {
        return service.requestMatch(sessionId, gameType);
    }

    @Override
    public MatchStatus pollMatch(String sessionId, String ticketId) throws RemoteException {
        return service.pollMatch(sessionId, ticketId);
    }

    @Override
    public MoveResult makeMove(MoveRequest moveRequest) throws RemoteException {
        return service.makeMove(moveRequest);
    }

    @Override
    public GameStateView getGameState(String sessionId, String roomId) throws RemoteException {
        return service.getGameState(sessionId, roomId);
    }

    @Override
    public List<GameType> listSupportedGames() throws RemoteException {
        return service.listSupportedGames();
    }
}
