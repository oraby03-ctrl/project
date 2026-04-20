package com.webapp.server.domain;

import com.webapp.shared.dto.GameStateView;
import com.webapp.shared.dto.GameType;

import java.util.Arrays;
import java.util.UUID;

/**
 * Standard 8×8 checkers (English draughts).
 *
 * Board layout:
 *   Pieces occupy dark squares only: (row + col) % 2 == 1
 *   Black pieces start at rows 0–2 (top), Red pieces at rows 5–7 (bottom).
 *   Red moves toward row 0, Black moves toward row 7.
 *
 * Piece encoding (stored in board[row][col]):
 *   null  = empty
 *   "r"   = red man,   "R" = red king
 *   "b"   = black man, "B" = black king
 *
 * Mapping to GameStateView fields (shared DTO reused across games):
 *   playerX / playerXName = Red player
 *   playerO / playerOName = Black player
 *   currentTurn           = "R" or "B"
 *   winner                = playerId of winner, or null
 *
 * Multi-jump handling:
 *   After a jump the server checks whether another jump is available
 *   from the landing square.  If so, the turn does NOT switch and
 *   mustJumpFrom is set.  The next move from this player MUST start
 *   from that square; the server rejects any other source.
 *   Promotion to king ends a multi-jump sequence (standard rules).
 */
public class CheckersRoom implements IRoom {

    private static final int SIZE = 8;

    private final String roomId;
    private final String playerRed;        // playerX
    private final String playerRedName;
    private final String playerBlack;      // playerO
    private final String playerBlackName;

    private final String[][] board;
    private String currentTurn;            // "R" or "B"
    private String winner;                 // playerId of winner, or null
    private boolean draw;
    private boolean finished;
    private int moveCount;
    private boolean persisted;
    private boolean adminEnded;
    private long lastSeenRed;
    private long lastSeenBlack;
    private String statusMessage;

    /** Non-null during a multi-jump; contains {row, col} of the piece that must continue. */
    private int[] mustJumpFrom;

    public CheckersRoom(String playerRed, String playerRedName,
                        String playerBlack, String playerBlackName) {
        this.roomId = UUID.randomUUID().toString();
        this.playerRed = playerRed;
        this.playerRedName = playerRedName;
        this.playerBlack = playerBlack;
        this.playerBlackName = playerBlackName;
        this.board = new String[SIZE][SIZE];
        this.currentTurn = "R";
        this.moveCount = 0;
        this.persisted = false;
        this.lastSeenRed = System.currentTimeMillis();
        this.lastSeenBlack = System.currentTimeMillis();
        this.statusMessage = "Game started — Red moves first";
        this.mustJumpFrom = null;
        initBoard();
    }

    // ── Initialisation ────────────────────────────────────────────────

    private void initBoard() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if ((row + col) % 2 == 1) {
                    if (row <= 2) {
                        board[row][col] = "b";
                    } else if (row >= 5) {
                        board[row][col] = "r";
                    }
                }
            }
        }
    }

    // ── IRoom ─────────────────────────────────────────────────────────

    @Override public String getRoomId() { return roomId; }
    @Override public String getPlayerX() { return playerRed; }
    @Override public String getPlayerO() { return playerBlack; }
    @Override public GameType getGameType() { return GameType.CHECKERS; }

    @Override
    public synchronized boolean isFinished() { return finished; }

    @Override
    public synchronized int getMoveCount() { return moveCount; }

    @Override
    public synchronized void touch(String playerId) {
        if (playerRed.equals(playerId)) {
            lastSeenRed = System.currentTimeMillis();
        } else if (playerBlack.equals(playerId)) {
            lastSeenBlack = System.currentTimeMillis();
        }
    }

    @Override
    public synchronized void applyDisconnectForfeitIfNeeded(long timeoutMs) {
        if (finished) return;
        long now = System.currentTimeMillis();
        boolean redOut   = now - lastSeenRed   > timeoutMs;
        boolean blackOut = now - lastSeenBlack > timeoutMs;
        if (!redOut && !blackOut) return;

        if (redOut && !blackOut) {
            winner = playerBlack;
            statusMessage = playerRedName + " disconnected — forfeit";
        } else if (blackOut && !redOut) {
            winner = playerRed;
            statusMessage = playerBlackName + " disconnected — forfeit";
        } else {
            draw = true;
            statusMessage = "Both players disconnected";
        }
        finished = true;
    }

    @Override
    public synchronized void forfeit(String quittingPlayerId) {
        if (finished) return;
        if (adminEnded) {
            statusMessage = "Game terminated by admin";
            finished = true;
            return;
        }
        if (playerRed.equals(quittingPlayerId)) {
            winner = playerBlack;
            statusMessage = playerRedName + " left — forfeit";
        } else if (playerBlack.equals(quittingPlayerId)) {
            winner = playerRed;
            statusMessage = playerBlackName + " left — forfeit";
        }
        finished = true;
    }

    @Override
    public synchronized GameStateView snapshot() {
        return new GameStateView(
                roomId,
                deepCopyBoard(),
                currentTurn,
                winner,
                draw,
                finished,
                playerRed,
                playerBlack,
                playerRedName,
                playerBlackName,
                buildStatusMessage()
        );
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

    // ── Move logic ────────────────────────────────────────────────────

    /**
     * Attempts to move/jump from (fromRow, fromCol) to (toRow, toCol).
     * Returns a {@link MoveOutcome} indicating success or the reason for rejection.
     */
    public synchronized MoveOutcome makeMove(String playerId,
                                             int fromRow, int fromCol,
                                             int toRow, int toCol) {
        if (finished) {
            return new MoveOutcome(false, "Game is already finished", moveCount, true);
        }

        String symbol = symbolFor(playerId);
        if (symbol == null) {
            return new MoveOutcome(false, "Player is not part of this room", moveCount, false);
        }
        if (!currentTurn.equals(symbol)) {
            return new MoveOutcome(false, "It is not your turn", moveCount, false);
        }

        if (isOutOfBounds(fromRow, fromCol) || isOutOfBounds(toRow, toCol)) {
            return new MoveOutcome(false, "Square is out of bounds", moveCount, false);
        }

        String piece = board[fromRow][fromCol];
        if (piece == null || !isOwn(piece, symbol)) {
            return new MoveOutcome(false, "No valid piece at that square", moveCount, false);
        }

        // Multi-jump: must continue from the piece that last jumped
        if (mustJumpFrom != null &&
                (fromRow != mustJumpFrom[0] || fromCol != mustJumpFrom[1])) {
            return new MoveOutcome(false,
                    "Must continue jump from row " + mustJumpFrom[0] + ", col " + mustJumpFrom[1],
                    moveCount, false);
        }

        int dr = toRow - fromRow;
        int dc = toCol - fromCol;

        if (Math.abs(dr) != Math.abs(dc)) {
            return new MoveOutcome(false, "Move must be diagonal", moveCount, false);
        }

        boolean king = Character.isUpperCase(piece.charAt(0));
        if (!king) {
            if (symbol.equals("R") && dr >= 0) {
                return new MoveOutcome(false, "Red men move toward row 0", moveCount, false);
            }
            if (symbol.equals("B") && dr <= 0) {
                return new MoveOutcome(false, "Black men move toward row 7", moveCount, false);
            }
        }

        int dist = Math.abs(dr);
        if (dist != 1 && dist != 2) {
            return new MoveOutcome(false, "Moves must be 1 or 2 squares diagonally", moveCount, false);
        }

        if (board[toRow][toCol] != null) {
            return new MoveOutcome(false, "Destination is occupied", moveCount, false);
        }

        boolean isJump = (dist == 2);

        // Enforce mandatory jump rule
        if (!isJump && mustJumpFrom == null && hasAnyJump(symbol)) {
            return new MoveOutcome(false, "A jump is available and must be taken", moveCount, false);
        }

        touch(playerId);

        if (isJump) {
            int midRow = fromRow + dr / 2;
            int midCol = fromCol + dc / 2;
            String mid = board[midRow][midCol];
            if (mid == null || isOwn(mid, symbol)) {
                return new MoveOutcome(false, "No opponent piece to jump over", moveCount, false);
            }
            // Execute jump
            board[toRow][toCol] = piece;
            board[fromRow][fromCol] = null;
            board[midRow][midCol] = null;
            moveCount++;

            boolean promoted = tryPromote(toRow, toCol, symbol);

            // Check for continuation (multi-jump) — promotion ends the sequence
            if (!promoted && hasJumpFrom(toRow, toCol, symbol)) {
                mustJumpFrom = new int[]{toRow, toCol};
                statusMessage = "Multi-jump! Continue from row " + toRow + ", col " + toCol;
                return new MoveOutcome(true, "Jump accepted — continue your multi-jump", moveCount, false);
            }
            mustJumpFrom = null;
        } else {
            // Simple diagonal move
            board[toRow][toCol] = piece;
            board[fromRow][fromCol] = null;
            moveCount++;
            tryPromote(toRow, toCol, symbol);
            mustJumpFrom = null;
        }

        // Check win / loss
        String opponent = symbol.equals("R") ? "B" : "R";
        if (!hasPieces(opponent) || !hasAnyMove(opponent)) {
            winner = symbol.equals("R") ? playerRed : playerBlack;
            finished = true;
            statusMessage = (symbol.equals("R") ? playerRedName : playerBlackName) + " wins!";
        } else {
            currentTurn = opponent;
            statusMessage = (opponent.equals("R") ? playerRedName : playerBlackName) + "'s turn";
        }

        return new MoveOutcome(true, "Move accepted", moveCount, finished);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    public synchronized String symbolFor(String playerId) {
        if (playerRed.equals(playerId))   return "R";
        if (playerBlack.equals(playerId)) return "B";
        return null;
    }

    private boolean isOwn(String piece, String symbol) {
        return piece.equalsIgnoreCase(symbol);
    }

    private boolean isOutOfBounds(int r, int c) {
        return r < 0 || r >= SIZE || c < 0 || c >= SIZE;
    }

    /**
     * Promotes a man to king if it has reached the back rank.
     * @return true if promotion occurred
     */
    private boolean tryPromote(int row, int col, String symbol) {
        String piece = board[row][col];
        if (piece == null) return false;
        if (symbol.equals("R") && row == 0 && piece.equals("r")) {
            board[row][col] = "R";
            return true;
        }
        if (symbol.equals("B") && row == SIZE - 1 && piece.equals("b")) {
            board[row][col] = "B";
            return true;
        }
        return false;
    }

    private boolean hasPieces(String symbol) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != null && isOwn(board[r][c], symbol)) return true;
            }
        }
        return false;
    }

    private boolean hasAnyJump(String symbol) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != null && isOwn(board[r][c], symbol) && hasJumpFrom(r, c, symbol)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasJumpFrom(int row, int col, String symbol) {
        String piece = board[row][col];
        if (piece == null) return false;
        boolean king = Character.isUpperCase(piece.charAt(0));
        int[][] dirs = moveDirs(symbol, king);
        for (int[] d : dirs) {
            int midRow = row + d[0];
            int midCol = col + d[1];
            int toRow  = row + 2 * d[0];
            int toCol  = col + 2 * d[1];
            if (isOutOfBounds(toRow, toCol) || isOutOfBounds(midRow, midCol)) continue;
            String mid = board[midRow][midCol];
            if (mid != null && !isOwn(mid, symbol) && board[toRow][toCol] == null) return true;
        }
        return false;
    }

    private boolean hasAnyMove(String symbol) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (board[r][c] != null && isOwn(board[r][c], symbol)) {
                    if (hasJumpFrom(r, c, symbol) || hasSimpleMoveFrom(r, c, symbol)) return true;
                }
            }
        }
        return false;
    }

    private boolean hasSimpleMoveFrom(int row, int col, String symbol) {
        String piece = board[row][col];
        if (piece == null) return false;
        boolean king = Character.isUpperCase(piece.charAt(0));
        int[][] dirs = moveDirs(symbol, king);
        for (int[] d : dirs) {
            int toRow = row + d[0];
            int toCol = col + d[1];
            if (!isOutOfBounds(toRow, toCol) && board[toRow][toCol] == null) return true;
        }
        return false;
    }

    /**
     * Returns the set of unit diagonal directions a piece can move/jump toward.
     * Kings move in all four diagonal directions; men only forward.
     */
    private int[][] moveDirs(String symbol, boolean king) {
        if (king) {
            return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        }
        // Red moves toward row 0 (dr = -1), Black toward row 7 (dr = +1)
        int dr = symbol.equals("R") ? -1 : 1;
        return new int[][]{{dr, -1}, {dr, 1}};
    }

    private String buildStatusMessage() {
        if (mustJumpFrom != null) {
            return statusMessage + " [MUST_JUMP_FROM:" + mustJumpFrom[0] + "," + mustJumpFrom[1] + "]";
        }
        return statusMessage;
    }

    private String[][] deepCopyBoard() {
        String[][] copy = new String[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            copy[i] = Arrays.copyOf(board[i], SIZE);
        }
        return copy;
    }
}
