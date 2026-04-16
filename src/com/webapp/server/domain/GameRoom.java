package com.webapp.server.domain;

import com.webapp.shared.dto.GameStateView;

import java.util.Arrays;
import java.util.UUID;

public class GameRoom {
    private final String roomId;
    private final String playerX;
    private final String playerO;
    private final String[][] board;
    private String currentTurn;
    private String winnerSymbol;
    private boolean draw;
    private boolean finished;
    private int moveCount;
    private boolean persisted;
    private long lastSeenX;
    private long lastSeenO;
    private String statusMessage;

    public GameRoom(String playerX, String playerO) {
        this.roomId = UUID.randomUUID().toString();
        this.playerX = playerX;
        this.playerO = playerO;
        this.board = new String[3][3];
        this.currentTurn = "X";
        this.moveCount = 0;
        this.persisted = false;
        this.lastSeenX = System.currentTimeMillis();
        this.lastSeenO = System.currentTimeMillis();
        this.statusMessage = "Game started";
    }

    public String getRoomId() {
        return roomId;
    }

    public String getPlayerX() {
        return playerX;
    }

    public String getPlayerO() {
        return playerO;
    }

    public synchronized MoveOutcome makeMove(String playerId, int row, int col) {
        if (finished) {
            return new MoveOutcome(false, "Game is already finished", moveCount, true);
        }
        if (row < 0 || row >= 3 || col < 0 || col >= 3) {
            return new MoveOutcome(false, "Move out of bounds", moveCount, false);
        }

        String symbol = symbolFor(playerId);
        if (symbol == null) {
            return new MoveOutcome(false, "Player is not part of this room", moveCount, false);
        }
        if (!currentTurn.equals(symbol)) {
            return new MoveOutcome(false, "It is not your turn", moveCount, false);
        }
        if (board[row][col] != null) {
            return new MoveOutcome(false, "Cell is already occupied", moveCount, false);
        }

        touch(playerId);
        board[row][col] = symbol;
        moveCount++;

        if (isWinner(symbol)) {
            winnerSymbol = symbol;
            finished = true;
            statusMessage = "Player " + winnerName() + " won";
        } else if (moveCount == 9) {
            draw = true;
            finished = true;
            statusMessage = "Draw";
        } else {
            currentTurn = "X".equals(symbol) ? "O" : "X";
            statusMessage = "Turn switched";
        }

        return new MoveOutcome(true, "Move accepted", moveCount, finished);
    }

    public synchronized void touch(String playerId) {
        if (playerX.equals(playerId)) {
            lastSeenX = System.currentTimeMillis();
            return;
        }
        if (playerO.equals(playerId)) {
            lastSeenO = System.currentTimeMillis();
        }
    }

    public synchronized void applyDisconnectForfeitIfNeeded(long timeoutMs) {
        if (finished) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean xTimedOut = now - lastSeenX > timeoutMs;
        boolean oTimedOut = now - lastSeenO > timeoutMs;
        if (!xTimedOut && !oTimedOut) {
            return;
        }

        if (xTimedOut && !oTimedOut) {
            winnerSymbol = "O";
            statusMessage = "Player " + playerX + " disconnected and lost by forfeit";
        } else if (oTimedOut && !xTimedOut) {
            winnerSymbol = "X";
            statusMessage = "Player " + playerO + " disconnected and lost by forfeit";
        } else {
            draw = true;
            statusMessage = "Both players disconnected";
        }
        finished = true;
    }

    public synchronized void forfeit(String quittingPlayerId) {
        if (finished) {
            return;
        }
        if (playerX.equals(quittingPlayerId)) {
            winnerSymbol = "O";
            statusMessage = "Player " + playerX + " left and lost by forfeit";
        } else if (playerO.equals(quittingPlayerId)) {
            winnerSymbol = "X";
            statusMessage = "Player " + playerO + " left and lost by forfeit";
        }
        finished = true;
    }

    public synchronized String symbolFor(String playerId) {
        if (playerX.equals(playerId)) {
            return "X";
        }
        if (playerO.equals(playerId)) {
            return "O";
        }
        return null;
    }

    public synchronized GameStateView snapshot() {
        String winnerName = null;
        if (winnerSymbol != null) {
            winnerName = "X".equals(winnerSymbol) ? playerX : playerO;
        }
        return new GameStateView(
                roomId,
                deepCopyBoard(),
                currentTurn,
                winnerName,
                draw,
                finished,
                playerX,
                playerO,
                statusMessage
        );
    }

    public synchronized boolean isFinished() {
        return finished;
    }

    public synchronized String winnerName() {
        if (winnerSymbol == null) {
            return null;
        }
        return "X".equals(winnerSymbol) ? playerX : playerO;
    }

    public synchronized int getMoveCount() {
        return moveCount;
    }

    public synchronized boolean markPersistedIfNeeded() {
        if (!finished || persisted) {
            return false;
        }
        persisted = true;
        return true;
    }

    private boolean isWinner(String symbol) {
        for (int i = 0; i < 3; i++) {
            if (symbol.equals(board[i][0]) && symbol.equals(board[i][1]) && symbol.equals(board[i][2])) {
                return true;
            }
            if (symbol.equals(board[0][i]) && symbol.equals(board[1][i]) && symbol.equals(board[2][i])) {
                return true;
            }
        }
        return (symbol.equals(board[0][0]) && symbol.equals(board[1][1]) && symbol.equals(board[2][2]))
                || (symbol.equals(board[0][2]) && symbol.equals(board[1][1]) && symbol.equals(board[2][0]));
    }

    private String[][] deepCopyBoard() {
        String[][] copy = new String[3][3];
        for (int i = 0; i < board.length; i++) {
            copy[i] = Arrays.copyOf(board[i], board[i].length);
        }
        return copy;
    }
}
