package com.webapp.server.domain;

import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;

import java.util.Arrays;
import java.util.UUID;

public class GameRoom implements IRoom {
    private final String roomId;
    private final String playerX;
    private final String playerXName;
    private final String playerO;
    private final String playerOName;
    private final String[][] board;
    private String currentTurn;
    private String winnerSymbol;
    private boolean draw;
    private boolean finished;
    private int moveCount;
    private boolean persisted;
    private boolean adminEnded;
    private long lastSeenX;
    private long lastSeenO;
    private String statusMessage;

    public GameRoom(String playerX, String playerXName, String playerO, String playerOName) {
        this.roomId = UUID.randomUUID().toString();
        this.playerX = playerX;
        this.playerXName = playerXName;
        this.playerO = playerO;
        this.playerOName = playerOName;
        this.board = new String[3][3];
        this.currentTurn = "X";
        this.moveCount = 0;
        this.persisted = false;
        this.lastSeenX = System.currentTimeMillis();
        this.lastSeenO = System.currentTimeMillis();
        this.statusMessage = "Game started";
    }

    @Override
    public String getRoomId() {
        return roomId;
    }

    @Override
    public String getPlayerX() {
        return playerX;
    }

    @Override
    public String getPlayerO() {
        return playerO;
    }

    @Override
    public GameType getGameType() {
        return GameType.TIC_TAC_TOE;
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
            statusMessage = "Player " + ("X".equals(symbol) ? playerXName : playerOName) + " won";
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
            statusMessage = "Player " + playerXName + " disconnected and lost by forfeit";
        } else if (oTimedOut && !xTimedOut) {
            winnerSymbol = "X";
            statusMessage = "Player " + playerOName + " disconnected and lost by forfeit";
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
        if (adminEnded) {
            statusMessage = "Game terminated by admin";
            finished = true;
            return;
        }
        if (playerX.equals(quittingPlayerId)) {
            winnerSymbol = "O";
            statusMessage = "Player " + playerXName + " left and lost by forfeit";
        } else if (playerO.equals(quittingPlayerId)) {
            winnerSymbol = "X";
            statusMessage = "Player " + playerOName + " left and lost by forfeit";
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
        String winnerId = null;
        if (winnerSymbol != null) {
            winnerId = "X".equals(winnerSymbol) ? playerX : playerO;
        }
        return new GameStateView(
                roomId,
                deepCopyBoard(),
                currentTurn,
                winnerId,
                draw,
                finished,
                playerX,
                playerO,
                playerXName,
                playerOName,
                statusMessage
        );
    }

    public synchronized boolean isFinished() {
        return finished;
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

    @Override
    public synchronized boolean isAdminEnded() { return adminEnded; }

    @Override
    public synchronized void setAdminEnded() { adminEnded = true; }

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
