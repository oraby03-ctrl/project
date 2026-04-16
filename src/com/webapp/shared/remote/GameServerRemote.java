package com.webapp.shared.remote;

import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;
import com.webapp.shared.dto.MatchStatus;
import com.webapp.shared.dto.MatchTicket;
import com.webapp.shared.dto.MoveRequest;
import com.webapp.shared.dto.MoveResult;
import com.webapp.shared.dto.RegisterResponse;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface GameServerRemote extends Remote {
    RegisterResponse registerPlayer(String username) throws RemoteException;

    MatchTicket requestMatch(String sessionId, GameType gameType) throws RemoteException;

    MatchStatus pollMatch(String sessionId, String ticketId) throws RemoteException;

    MoveResult makeMove(MoveRequest moveRequest) throws RemoteException;

    GameStateView getGameState(String sessionId, String roomId) throws RemoteException;

    List<GameType> listSupportedGames() throws RemoteException;
}
