package com.webapp.server.domain;

import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;

import java.util.UUID;

public class ConnectFourRoom implements IRoom {

    private static final int ROWS = 6;
    private static final int COLS = 7;

    private final String roomId;
    private final String playerYellow;
    private final String playerRed;
    private final String playerYellowName;
    private final String playerRedName;

    private final String[][] board = new String[ROWS][COLS];
    private String currentTurn; // "Y" or "R"
    private String winner;      // playerId of winner, or null
    private boolean draw;
    private boolean finished;
    private int moveCount;
    private boolean persisted;
    private boolean adminEnded;

    private long lastSeenYellow;
    private long lastSeenRed;

    public ConnectFourRoom(String playerYellow, String playerYellowName,
                           String playerRed,    String playerRedName) {
        this.roomId          = UUID.randomUUID().toString();
        this.playerYellow    = playerYellow;
        this.playerYellowName = playerYellowName;
        this.playerRed       = playerRed;
        this.playerRedName   = playerRedName;
        this.currentTurn     = "Y";
        long now = System.currentTimeMillis();
        this.lastSeenYellow  = now;
        this.lastSeenRed     = now;
    }

    // ── IRoom ─────────────────────────────────────────────────────────

    @Override public String getRoomId()   { return roomId; }
    @Override public String getPlayerX()  { return playerYellow; }
    @Override public String getPlayerO()  { return playerRed; }
    @Override public GameType getGameType() { return GameType.CONNECT_FOUR; }
    @Override public synchronized boolean isFinished() { return finished; }
    @Override public synchronized int getMoveCount()   { return moveCount; }

    @Override
    public synchronized void touch(String playerId) {
        long now = System.currentTimeMillis();
        if (playerYellow.equals(playerId)) lastSeenYellow = now;
        else if (playerRed.equals(playerId)) lastSeenRed  = now;
    }

    @Override
    public synchronized void applyDisconnectForfeitIfNeeded(long timeoutMs) {
        if (finished) return;
        long now = System.currentTimeMillis();
        boolean yellowOut = now - lastSeenYellow > timeoutMs;
        boolean redOut    = now - lastSeenRed    > timeoutMs;
        if (!yellowOut && !redOut) return;
        if (yellowOut && !redOut) {
            winner = playerRed;
        } else if (redOut && !yellowOut) {
            winner = playerYellow;
        } else {
            draw = true;
        }
        finished = true;
    }

    @Override
    public synchronized void forfeit(String quittingPlayerId) {
        if (finished) return;
        if (adminEnded) {
            finished = true;
            return; // no winner — admin-terminated
        }
        if (playerYellow.equals(quittingPlayerId)) winner = playerRed;
        else if (playerRed.equals(quittingPlayerId)) winner = playerYellow;
        finished = true;
    }

    @Override
    public synchronized GameStateView snapshot() {
        String[][] copy = new String[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) copy[r] = board[r].clone();
        return new GameStateView(
                roomId, copy, currentTurn, winner, draw, finished,
                playerYellow, playerRed, playerYellowName, playerRedName,
                buildStatus());
    }

    @Override
    public synchronized boolean markPersistedIfNeeded() {
        if (!finished || persisted) return false;
        persisted = true;
        return true;
    }

    @Override
    public synchronized boolean isAdminEnded() { return adminEnded; }

    @Override
    public synchronized void setAdminEnded() { adminEnded = true; }

    // ── Move ──────────────────────────────────────────────────────────

    public synchronized MoveOutcome makeMove(String playerId, int col) {
        if (finished) return new MoveOutcome(false, "Game is already over", moveCount, true);
        String symbol = symbolFor(playerId);
        if (symbol == null)       return new MoveOutcome(false, "Player not in this room", moveCount, false);
        if (!symbol.equals(currentTurn)) return new MoveOutcome(false, "Not your turn", moveCount, false);
        if (col < 0 || col >= COLS) return new MoveOutcome(false, "Column out of range", moveCount, false);

        int row = lowestEmptyRow(col);
        if (row == -1) return new MoveOutcome(false, "Column is full", moveCount, false);

        board[row][col] = symbol;
        touch(playerId);
        moveCount++;

        if (checkWin(row, col, symbol)) {
            winner   = playerId;
            finished = true;
        } else if (moveCount == ROWS * COLS) {
            draw     = true;
            finished = true;
        } else {
            currentTurn = symbol.equals("Y") ? "R" : "Y";
        }
        return new MoveOutcome(true, "Move accepted", moveCount, finished);
    }

    public String symbolFor(String playerId) {
        if (playerYellow.equals(playerId)) return "Y";
        if (playerRed.equals(playerId))    return "R";
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private int lowestEmptyRow(int col) {
        for (int r = ROWS - 1; r >= 0; r--) {
            if (board[r][col] == null) return r;
        }
        return -1;
    }

    private boolean checkWin(int row, int col, String sym) {
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1
                    + countDir(row, col, d[0], d[1], sym)
                    + countDir(row, col, -d[0], -d[1], sym);
            if (count >= 4) return true;
        }
        return false;
    }

    private int countDir(int r, int c, int dr, int dc, String sym) {
        int n = 0;
        r += dr;
        c += dc;
        while (r >= 0 && r < ROWS && c >= 0 && c < COLS && sym.equals(board[r][c])) {
            n++;
            r += dr;
            c += dc;
        }
        return n;
    }

    private String buildStatus() {
        if (adminEnded) return "Game terminated by admin";
        if (finished) {
            if (winner != null) {
                String name = winner.equals(playerYellow) ? playerYellowName : playerRedName;
                return name + " wins!";
            }
            return "It's a draw!";
        }
        String name = currentTurn.equals("Y") ? playerYellowName : playerRedName;
        return name + "'s turn (" + (currentTurn.equals("Y") ? "Yellow" : "Red") + ")";
    }
}
