package com.webapp.client.presentation;

import com.webapp.client.application.ClientGameService;
import com.webapp.client.model.ClientModel;
import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.MatchStatus;
import com.webapp.shared.dto.MatchTicket;
import com.webapp.shared.dto.MoveResult;
import com.webapp.shared.dto.RegisterResponse;

public class ClientController {
    private final ClientModel model;
    private final ConsoleView view;
    private final ClientGameService service;

    public ClientController(ClientModel model, ConsoleView view, ClientGameService service) {
        this.model = model;
        this.view = view;
        this.service = service;
    }

    public void run() {
        String username = view.askUsername();
        RegisterResponse registerResponse = service.register(username);

        model.setUsername(registerResponse.username());
        model.setSessionId(registerResponse.sessionId());

        view.printInfo("Logged in as " + model.getUsername());
        view.printInfo("Requesting TIC_TAC_TOE match...");

        MatchTicket ticket = service.requestTicTacToe(model.getSessionId());
        if (ticket.ticketId() == null || ticket.ticketId().isBlank()) {
            view.printInfo(ticket.message());
            return;
        }

        MatchStatus status;
        while (true) {
            status = service.pollMatch(model.getSessionId(), ticket.ticketId());
            if (status.matched()) {
                break;
            }
            view.printInfo(status.message());
            sleep(1000);
        }

        model.setRoomId(status.roomId());
        model.setSymbol(status.symbol());
        view.printInfo("Matched against " + status.opponent() + " | You are '" + model.getSymbol() + "'");

        gameLoop();
    }

    private void gameLoop() {
        while (true) {
            GameStateView state = service.state(model.getSessionId(), model.getRoomId());
            view.printBoard(state);

            if (state.finished()) {
                if (state.draw()) {
                    view.printInfo("Game finished: draw");
                } else {
                    view.printInfo("Winner: " + state.winner());
                }
                return;
            }

            if (model.getSymbol().equals(state.currentTurn())) {
                view.printInfo("Your turn");
                int[] move = view.askMove();
                MoveResult moveResult = service.move(model.getSessionId(), model.getRoomId(), move[0], move[1]);
                view.printInfo(moveResult.message());
            } else {
                view.printInfo("Opponent's turn...");
                sleep(1000);
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", exception);
        }
    }
}
