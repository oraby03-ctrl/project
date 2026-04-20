# Multiplayer Game Platform

A Java-based multiplayer game server supporting **Tic-Tac-Toe**, **Checkers**, and **Connect Four** in real time. Players connect through a browser (HTTP/REST) or through a console client (Java RMI). An admin panel provides live room management and player administration.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| HTTP server | `com.sun.net.httpserver.HttpServer` (JDK built-in) |
| RMI server | `java.rmi` (Java Remote Method Invocation) |
| ORM / JPA | Hibernate + Jakarta Persistence (SQLite) |
| JDBC audit | Raw JDBC — move-by-move audit log |
| Database | SQLite file `web-game-platform.db` |
| Build | Maven (`pom.xml`) |
| Java version | Java 21+ |
| Public tunnel | ngrok (auto-launched on startup) |

---

## Project Structure

```
src/
├── Main.java                          ← startup hint message
├── META-INF/persistence.xml           ← JPA persistence unit config
├── web/index.html                     ← browser UI (served at /)
└── com/webapp/
    ├── client/                        ← RMI console client
    │   ├── application/
    │   │   ├── ClientGameService.java
    │   │   └── RemoteGameGateway.java
    │   ├── model/
    │   │   └── ClientModel.java
    │   └── presentation/
    │       ├── ClientController.java
    │       ├── ClientMain.java
    │       └── ConsoleView.java
    ├── server/                        ← game server
    │   ├── application/
    │   │   ├── GamePlatformService.java
    │   │   └── MatchmakingService.java
    │   ├── domain/
    │   │   ├── BattleshipRoom.java    ← empty stub (future)
    │   │   ├── CheckersRoom.java
    │   │   ├── ConnectFourRoom.java
    │   │   ├── GameRoom.java          ← Tic-Tac-Toe room
    │   │   ├── IRoom.java
    │   │   ├── MatchRequest.java
    │   │   ├── MatchTicketState.java
    │   │   ├── MoveOutcome.java
    │   │   ├── PlayerSessionRegistry.java
    │   │   └── SessionInfo.java
    │   ├── infrastructure/
    │   │   ├── PersistenceService.java
    │   │   ├── jdbc/
    │   │   │   └── JdbcAuditRepository.java
    │   │   └── jpa/
    │   │       ├── GameRecordEntity.java
    │   │       ├── GameRecordJpaRepository.java
    │   │       ├── JpaBootstrap.java
    │   │       ├── PlayerEntity.java
    │   │       └── PlayerJpaRepository.java
    │   └── presentation/
    │       ├── RmiGameServer.java
    │       ├── ServerMain.java
    │       └── WebAppHttpServer.java
    └── shared/
        ├── dto/
        │   ├── BattleshipStateView.java  ← empty stub (future)
        │   ├── CheckersMoveRequest.java
        │   ├── ConnectFourMoveRequest.java
        │   ├── GameStateView.java
        │   ├── GameType.java
        │   ├── MatchStatus.java
        │   ├── MatchTicket.java
        │   ├── MoveRequest.java
        │   ├── MoveResult.java
        │   ├── PlayerProfileView.java
        │   └── RegisterResponse.java
        └── remote/
            └── GameServerRemote.java
```

---

## How to Run

### Start the server
```
Run: com.webapp.server.presentation.ServerMain
```
The server prints:
```
 Local:   http://localhost:8080
 Network: http://<LAN-IP>:8080
 RMI:     rmi://localhost:1099/GameServer
 Public:  https://xxxx.ngrok-free.app  ← share with players
```

### Start a console (RMI) client
```
Run: com.webapp.client.presentation.ClientMain
```
Connects to `rmi://localhost:1099/GameServer` and plays Tic-Tac-Toe through the terminal.

### Admin login
Username: `admin` / Password: `admin`
The admin account is created automatically on first startup.

---

---

# File Reference — Every Class and Function

---

## `Main.java`

Entry point stub. Prints a message telling the user which class to run for the server or client. Contains no logic.

```
main(String[] args)
```
Prints instructions. Does not start anything.

---

---

## `server/presentation/ServerMain.java`

**The application entry point.** Wires all dependencies together and starts both the HTTP and RMI servers.

### Fields
| Field | Description |
|-------|-------------|
| `BINDING_NAME = "GameServer"` | RMI registry binding name used by both server and client |
| `NGROK_PATH` | Absolute path to the `ngrok.exe` binary |

### Methods

#### `main(String[] args)`
1. Creates `PlayerSessionRegistry`, `MatchmakingService`, `PersistenceService`, `GamePlatformService`.
2. Starts `WebAppHttpServer` on port 8080.
3. Creates the RMI registry on port 1099, instantiates `RmiGameServer`, and binds it.
4. Calls `persistenceService.ensurePlayer("admin","admin")` to guarantee the admin account exists.
5. Calls `printLanAddresses(8080)` and `startNgrok(8080)`.

#### `startNgrok(int port)`
Launches ngrok as a subprocess, polls `http://127.0.0.1:4040/api/tunnels` for up to 5 seconds, and prints the public HTTPS URL. Registers a JVM shutdown hook to destroy the process on exit.

#### `printLanAddresses(int port)`
Iterates all network interfaces and prints the IPv4 address of each active non-loopback interface as an `http://` URL.

---

## `server/presentation/WebAppHttpServer.java`

**The HTTP server.** Handles all browser requests. Uses `com.sun.net.httpserver.HttpServer`.

### Routes registered at startup

| Method | Path | Handler |
|--------|------|---------|
| GET | `/` | `handleIndex` — serves `web/index.html` |
| POST | `/api/login` | `handleLogin` — registers/logs in a player |
| GET | `/api/profile` | `handleProfile` — returns player stats |
| GET | `/api/games` | `handleGames` — lists supported game types |
| POST | `/api/match/request` | `handleMatchRequest` — queues player for matchmaking |
| GET | `/api/match/poll` | `handleMatchPoll` — polls queue status |
| GET | `/api/game/state` | `handleGameState` — returns board snapshot |
| POST | `/api/game/move` | `handleGameMove` — Tic-Tac-Toe move |
| POST | `/api/game/checkers/move` | `handleCheckersMove` — Checkers move |
| POST | `/api/game/connectfour/move` | `handleConnectFourMove` — Connect Four move |
| POST | `/api/game/heartbeat` | `handleHeartbeat` — keeps the session alive |
| POST | `/api/game/leave` | `handleLeave` — player forfeits and leaves |
| GET | `/api/scoreboard` | `handleScoreboard` — all players sorted by score |
| POST | `/api/admin/delete-player` | `handleAdminDeletePlayer` — admin deletes a player |
| POST | `/api/admin/reset-stats` | `handleAdminResetStats` — admin resets W/D/L |
| GET | `/api/admin/rooms` | `handleAdminRooms` — list all live rooms |
| POST | `/api/admin/force-end-room` | `handleAdminForceEndRoom` — terminate a room |
| GET | `/api/admin/recent-games` | `handleAdminRecentGames` — last 50 finished games |

### Private helper methods

| Method | Purpose |
|--------|---------|
| `parseQuery(exchange)` | Parses URL query string into a `Map<String,String>` |
| `parseFormBody(exchange)` | Reads the request body and parses it as form-encoded key/value pairs |
| `parseFormEncoded(raw)` | Splits `key=value&key=value` strings into a map; URL-decodes keys and values |
| `decode(value)` | `URLDecoder.decode` wrapper (UTF-8) |
| `required(map, key)` | Returns the value for a key, throwing `IllegalArgumentException` if absent or blank |
| `profileToJson(profile)` | Serializes a `PlayerProfileView` to a JSON string |
| `ticketToJson(ticket)` | Serializes a `MatchTicket` to JSON |
| `matchStatusToJson(status)` | Serializes a `MatchStatus` to JSON |
| `moveResultToJson(result)` | Serializes a `MoveResult` to JSON |
| `gameStateToJson(state)` | Serializes a `GameStateView` (including 2-D board) to JSON |
| `jsonError(message)` | Wraps an error string in `{"error":"..."}` |
| `maybeString(value)` | Returns `null` (JSON literal) if the value is null, otherwise a quoted string |
| `esc(value)` | Escapes `\`, `"`, `\n`, `\r` in strings for safe JSON embedding |
| `sendJson(exchange, statusCode, json)` | Writes a JSON response |
| `sendText(exchange, statusCode, body, contentType)` | Low-level response writer |

---

## `server/presentation/RmiGameServer.java`

**The RMI server stub.** Extends `UnicastRemoteObject` and implements `GameServerRemote`. Every method delegates to `GamePlatformService`. Bound in the RMI registry as `"GameServer"` on port 1099.

### Methods (all `throws RemoteException`)

| Method | Delegates to |
|--------|-------------|
| `registerPlayer(String username)` | `service.registerPlayer(username)` — single-arg overload that auto-generates a player ID with the prefix `legacy-` |
| `requestMatch(String sessionId, GameType gameType)` | `service.requestMatch(...)` |
| `pollMatch(String sessionId, String ticketId)` | `service.pollMatch(...)` |
| `makeMove(MoveRequest moveRequest)` | `service.makeMove(...)` |
| `getGameState(String sessionId, String roomId)` | `service.getGameState(...)` |
| `listSupportedGames()` | `service.listSupportedGames()` |

---

---

## `server/application/GamePlatformService.java`

**The core application service.** All business logic that crosses multiple domain objects lives here. Wired by `ServerMain`.

### Constructor
```java
GamePlatformService(PlayerSessionRegistry sessions,
                    MatchmakingService matchmaking,
                    PersistenceService persistenceService)
```

### Public methods

| Method | Description |
|--------|-------------|
| `registerPlayer(String playerName)` | Legacy RMI-only overload. Auto-generates `playerId = "legacy-" + name`. Calls the two-arg overload. |
| `registerPlayer(String playerId, String playerName)` | Validates both fields (3–24 chars). Persists the player via JPA. Creates a session and returns `RegisterResponse`. |
| `getProfile(String sessionId)` | Requires a valid session. Returns `PlayerProfileView` from the database. |
| `requestMatch(String sessionId, GameType gameType)` | Validates the player is not `admin`. Blocks on Battleship (stub). Enqueues the player in `MatchmakingService` and returns a `MatchTicket`. |
| `pollMatch(String sessionId, String ticketId)` | Returns the current `MatchStatus` for a ticket (waiting / matched). |
| `makeMove(MoveRequest moveRequest)` | Validates session, resolves the `GameRoom` (Tic-Tac-Toe), calls `room.makeMove()`, logs via JDBC, persists if game ended. |
| `makeCheckersMove(CheckersMoveRequest req)` | Same as above but for `CheckersRoom` and uses `fromRow/fromCol/toRow/toCol`. |
| `makeConnectFourMove(ConnectFourMoveRequest req)` | Same but for `ConnectFourRoom` with a single `col` parameter. |
| `getGameState(String sessionId, String roomId)` | Touches (heartbeat), checks for disconnect forfeit, persists if finished, returns a board snapshot. |
| `heartbeat(String sessionId, String roomId)` | Calls `room.touch()` to reset the disconnect timer. |
| `leaveGame(String sessionId, String roomId)` | Forfeits the player from the room, then persists. |
| `listSupportedGames()` | Returns `[TIC_TAC_TOE, CHECKERS, CONNECT_FOUR]`. |
| `getScoreboard()` | Returns all player profiles sorted by score, excluding the `admin` account. |
| `deletePlayer(String adminSessionId, String targetPlayerId)` | Admin-only. Deletes a player from the database (cannot delete `admin`). |
| `resetPlayerStats(String adminSessionId, String targetPlayerId)` | Admin-only. Resets W/D/L to 0 (cannot reset `admin`). |
| `getActiveRooms(String adminSessionId)` | Admin-only. Returns a list of all rooms (finished or live) as plain maps. |
| `getRecentGames(String adminSessionId, int limit)` | Admin-only. Returns the most recent finished game records. |
| `forceEndRoom(String adminSessionId, String roomId)` | Admin-only. Marks the room as admin-ended and forfeits playerX. No stats are recorded. |

### Private methods

| Method | Description |
|--------|-------------|
| `requireAdmin(String sessionId)` | Throws `SecurityException` if the caller's player ID is not `"admin"`. |
| `persistIfFinished(IRoom room)` | Checks `isAdminEnded()` first — if true, skips stats; otherwise, if `markPersistedIfNeeded()` returns true, writes to `GameRecordEntity` and updates player W/D/L. |
| `validatePlayerName(String playerName)` | Rejects null, blank, or names outside 3–24 characters. |
| `validatePlayerId(String playerId)` | Rejects null, blank, or IDs outside 3–24 characters. |

---

## `server/application/MatchmakingService.java`

**Matchmaking queue and room registry.** Runs a background thread that continuously pairs waiting players.

### Data structures
| Field | Type | Purpose |
|-------|------|---------|
| `queues` | `EnumMap<GameType, BlockingQueue<MatchRequest>>` | One FIFO queue per game type |
| `tickets` | `ConcurrentHashMap<String, MatchTicketState>` | Ticket ID → ticket state |
| `rooms` | `ConcurrentHashMap<String, IRoom>` | Room ID → room |
| `playerToRoom` | `ConcurrentHashMap<String, String>` | Player ID → room ID |
| `playersQueuing` | `ConcurrentHashSet<String>` | Players currently in a queue |
| `matcherExecutor` | `ExecutorService` (single thread) | Runs `matchLoop()` |

### Public methods

| Method | Description |
|--------|-------------|
| `enqueue(String playerId, String playerName, GameType gameType)` | Adds player to the appropriate queue. If the player was already queued (e.g., stale browser tab), their previous request is cancelled first. Returns a ticket ID. Throws if the player is already in a live game. |
| `getTicketState(String ticketId)` | Returns the `MatchTicketState` for a given ticket, or null. |
| `requireRoomByPlayer(String playerId, String roomId)` | Validates that `playerId` is assigned to `roomId` and the room exists. Returns the `IRoom`. |
| `getAllRooms()` | Returns a snapshot list of all rooms (used by admin panel). |
| `forceEndRoom(String roomId)` | Sets `adminEnded` flag on the room and calls `forfeit(playerX)` to finish it without recording stats. |

### Private methods

| Method | Description |
|--------|-------------|
| `matchLoop()` | Runs forever on the background thread. Every 80 ms it checks each queue; if two different players are waiting, calls `createRoomForPair`. |
| `cancelPendingRequests(String playerId)` | Removes all queued `MatchRequest` entries for a given player ID across all game-type queues. |
| `createRoomForPair(MatchRequest p1, MatchRequest p2)` | Creates the correct room type based on `gameType`. Registers the room, maps both players to it, and updates both `MatchTicketState` objects with match data. |

---

---

## `server/domain/IRoom.java`

**Interface implemented by every game room.** Defines the contract shared by Tic-Tac-Toe, Checkers, and Connect Four rooms.

| Method | Description |
|--------|-------------|
| `getRoomId()` | Unique UUID string for this room |
| `getPlayerX()` | Player ID of the "X" / Red / Yellow player |
| `getPlayerO()` | Player ID of the "O" / Black / Red player |
| `getGameType()` | Returns the `GameType` enum value |
| `isFinished()` | True when the game has ended for any reason |
| `touch(String playerId)` | Updates the player's last-seen timestamp (disconnect detection) |
| `applyDisconnectForfeitIfNeeded(long timeoutMs)` | If a player has not been seen for `timeoutMs` ms, forfeits them |
| `forfeit(String quittingPlayerId)` | Immediately ends the game; the other player wins |
| `snapshot()` | Returns a `GameStateView` representing the current board state |
| `markPersistedIfNeeded()` | Atomically marks the room as persisted; returns true only the first time after finishing |
| `getMoveCount()` | Total number of moves played |
| `isAdminEnded()` | True if the admin force-ended this room |
| `setAdminEnded()` | Flags the room as admin-terminated |

---

## `server/domain/GameRoom.java`

**Tic-Tac-Toe room.** 3×3 grid, two players ("X" and "O"), standard win conditions.

### Constructor
```java
GameRoom(String playerX, String playerXName, String playerO, String playerOName)
```
Generates a UUID room ID, initialises a blank 3×3 board, sets `currentTurn = "X"`.

### Public methods

| Method | Description |
|--------|-------------|
| `makeMove(String playerId, int row, int col)` | Validates bounds, turn, and cell availability. Places the symbol, increments move count, checks win (rows/cols/diagonals) and draw (9 moves). Returns `MoveOutcome`. |
| `touch(String playerId)` | Updates `lastSeenX` or `lastSeenO` to now. |
| `applyDisconnectForfeitIfNeeded(long timeoutMs)` | Ends the game if either player has been absent longer than `timeoutMs` ms. Both absent → draw. |
| `forfeit(String quittingPlayerId)` | Immediately awards the win to the other player. If `adminEnded` is set, the game ends without a winner. |
| `symbolFor(String playerId)` | Returns `"X"` or `"O"` for a given player ID, or null if not a participant. |
| `snapshot()` | Returns a deep copy of the board wrapped in `GameStateView`. |
| `isFinished()` | Returns `finished`. |
| `getMoveCount()` | Returns `moveCount`. |
| `markPersistedIfNeeded()` | Returns true and sets `persisted = true` exactly once after the game finishes. |
| `isAdminEnded()` / `setAdminEnded()` | Gets/sets the admin-terminated flag. |

### Private methods

| Method | Description |
|--------|-------------|
| `isWinner(String symbol)` | Checks all 8 win lines (3 rows, 3 cols, 2 diagonals) for the given symbol. |
| `deepCopyBoard()` | Returns a clone of the board array to prevent mutation of the snapshot. |

---

## `server/domain/CheckersRoom.java`

**Standard 8×8 English draughts (checkers) room.**

Board encoding: pieces occupy dark squares only `(row+col) % 2 == 1`. Black starts at rows 0–2, Red at rows 5–7.

| Symbol | Meaning |
|--------|---------|
| `"r"` | Red man |
| `"R"` | Red king |
| `"b"` | Black man |
| `"B"` | Black king |

### Constructor
```java
CheckersRoom(String playerRed, String playerRedName, String playerBlack, String playerBlackName)
```
Calls `initBoard()` to place all pieces. Red moves first (`currentTurn = "R"`).

### Public methods

| Method | Description |
|--------|-------------|
| `makeMove(String playerId, int fromRow, int fromCol, int toRow, int toCol)` | Full checkers rule enforcement (see below). Returns `MoveOutcome`. |
| `symbolFor(String playerId)` | Returns `"R"` or `"B"` for a player, or null. |
| `touch`, `applyDisconnectForfeitIfNeeded`, `forfeit`, `snapshot`, `markPersistedIfNeeded`, `isAdminEnded`, `setAdminEnded` | Implement `IRoom`; same semantics as `GameRoom`. |

#### `makeMove` rule enforcement
1. Validates turn, bounds, piece ownership.
2. If a **multi-jump** is in progress (`mustJumpFrom != null`), enforces that the move starts from the flagged square.
3. Validates diagonal movement, direction (men can only move forward), and distance (1 for simple move, 2 for jump).
4. **Mandatory jump rule**: if any jump is available for the current player, a simple move is rejected.
5. For jumps: verifies an opponent piece occupies the middle square, removes it, moves the piece.
6. **Promotion**: promotes to king if the piece reaches the back rank.
7. **Multi-jump continuation**: after a jump, checks if another jump is available from the landing square. If yes, sets `mustJumpFrom` and returns without switching turns.
8. Win condition: opponent has no pieces left or no legal moves.

### Private helper methods

| Method | Description |
|--------|-------------|
| `initBoard()` | Places all 12 black and 12 red pieces on dark squares. |
| `isOwn(String piece, String symbol)` | Returns true if `piece` belongs to the current player. |
| `isOutOfBounds(int r, int c)` | Bounds check for the 8×8 grid. |
| `tryPromote(int row, int col, String symbol)` | Promotes man to king if at the back rank; returns true if promoted. |
| `hasAnyJump(String symbol)` | Scans all own pieces to check if any jump is available (enforces mandatory-jump rule). |
| `hasJumpFrom(int row, int col, String symbol)` | Checks whether a jump is available specifically from a given square. |
| `hasAnyMove(String symbol)` | Returns true if the given player has at least one legal move. |
| `hasPieces(String symbol)` | Returns true if the player still has at least one piece. |
| `buildStatusMessage()` | Builds a human-readable status string for the current board state. |
| `deepCopyBoard()` | Returns a defensive copy of the board for `snapshot()`. |

---

## `server/domain/ConnectFourRoom.java`

**Connect Four room.** 6-row × 7-column board. Yellow moves first.

| Symbol | Player |
|--------|--------|
| `"Y"` | Yellow (playerX) |
| `"R"` | Red (playerO) |

### Constructor
```java
ConnectFourRoom(String playerYellow, String playerYellowName, String playerRed, String playerRedName)
```

### Public methods

| Method | Description |
|--------|-------------|
| `makeMove(String playerId, int col)` | Validates turn and column bounds. Drops piece to the lowest empty row in the column. Checks win and draw (all 42 cells filled). Returns `MoveOutcome`. |
| `symbolFor(String playerId)` | Returns `"Y"` or `"R"`, or null. |
| `touch`, `applyDisconnectForfeitIfNeeded`, `forfeit`, `snapshot`, `markPersistedIfNeeded`, `isAdminEnded`, `setAdminEnded` | Standard `IRoom` implementations. |

### Private helper methods

| Method | Description |
|--------|-------------|
| `lowestEmptyRow(int col)` | Scans bottom-to-top; returns the lowest empty row index, or -1 if the column is full. |
| `checkWin(int row, int col, String sym)` | Checks all four directions (horizontal, vertical, two diagonals) for 4-in-a-row from the last-placed piece. |
| `countDir(int r, int c, int dr, int dc, String sym)` | Counts consecutive matching pieces in one direction. |
| `buildStatus()` | Returns a human-readable game status string. |

---

## `server/domain/BattleshipRoom.java`

Empty stub. Battleship is planned but not implemented.

---

## `server/domain/PlayerSessionRegistry.java`

**In-memory session store.** Maps session ID strings to player identity.

### Methods

| Method | Description |
|--------|-------------|
| `register(String playerId, String playerName)` | Generates a UUID session ID, stores a `SessionInfo` record, returns the session ID. |
| `requireSession(String sessionId)` | Returns the `SessionInfo` or throws `IllegalArgumentException` if the session does not exist. |
| `requirePlayerId(String sessionId)` | Returns just the player ID from the session, or throws. |
| `requirePlayerName(String sessionId)` | Returns just the player name from the session, or throws. |

---

## `server/domain/SessionInfo.java`

Immutable record holding `(sessionId, playerId, playerName)`. Stored in `PlayerSessionRegistry`.

---

## `server/domain/MoveOutcome.java`

Immutable record returned by every room's `makeMove` method: `(accepted, message, moveNumber, finished)`.

---

## `server/domain/MatchRequest.java`

Immutable record used in the matchmaking queue: `(ticketId, playerId, playerName, gameType)`.

---

## `server/domain/MatchTicketState.java`

Mutable state object for a single matchmaking ticket. Fields are volatile for thread visibility.

| Field / Method | Description |
|----------------|-------------|
| `ticketId` | UUID string for this ticket |
| `isMatched()` | True once the matchmaker has paired this player |
| `setMatchedData(roomId, symbol, opponent, gameType)` | Called by the matchmaking thread when a pair is found |
| `getRoomId()` | Room ID (null until matched) |
| `getSymbol()` | The player's assigned symbol (e.g. `"X"`, `"R"`) |
| `getOpponent()` | Opponent's name (null until matched) |
| `getGameType()` | Game type name string (null until matched) |

---

---

## `server/infrastructure/PersistenceService.java`

**Facade over JPA and JDBC.** `GamePlatformService` only talks to this class for persistence; it never accesses repositories directly.

### Constructor
```java
PersistenceService(String jdbcUrl)
```
Creates `PlayerJpaRepository`, `GameRecordJpaRepository` (both using `JpaBootstrap.emf()`), and `JdbcAuditRepository` (with the provided JDBC URL).

### Methods

| Method | Description |
|--------|-------------|
| `ensurePlayer(String playerId, String playerName)` | Inserts the player if not found; updates their display name if they exist. |
| `getProfile(String playerId)` | Returns a `PlayerProfileView` from JPA. |
| `getScoreboard()` | Returns all player profiles sorted by score (JPA). |
| `logMove(String roomId, int moveNo, String playerId, String symbol, int row, int col)` | Writes one row to the JDBC `game_move_audit` table. |
| `persistFinishedGame(roomId, gameType, playerX, playerO, winner, draw)` | Saves a `GameRecordEntity` and updates W/D/L stats via JPA. |
| `deletePlayer(String playerId)` | Deletes the player row from JPA. |
| `resetPlayerStats(String playerId)` | Resets W/D/L to 0 in JPA. |
| `getRecentGames(int limit)` | Returns the most recent `GameRecordEntity` list from JPA. |

---

## `server/infrastructure/jdbc/JdbcAuditRepository.java`

**Raw JDBC move audit log.** Writes every individual move to a `game_move_audit` table.

### Constructor
```java
JdbcAuditRepository(String jdbcUrl)
```
Stores the URL and immediately calls `initSchema()`.

### Methods

| Method | Description |
|--------|-------------|
| `logMove(String roomId, int moveNumber, String playerId, String symbol, int row, int col)` | Inserts one row: room ID, move number, player ID, symbol played, row, column, and current timestamp. |
| `initSchema()` *(private)* | Creates the `game_move_audit` table if it does not exist. Schema: `id INTEGER PRIMARY KEY AUTOINCREMENT`, `room_id`, `move_number`, `player_id`, `symbol`, `row`, `col`, `played_at DATETIME`. |

---

## `server/infrastructure/jpa/PlayerJpaRepository.java`

**JPA repository for the `players` table.**

### Methods

| Method | Description |
|--------|-------------|
| `ensureExists(String playerId, String playerName)` | Upsert: creates the player if not found, updates their display name if they exist. |
| `getProfile(String playerId)` | Loads the `PlayerEntity` and projects it into a `PlayerProfileView` (score = wins×3 + draws). |
| `updateStats(String playerXId, String playerOId, String winnerPlayerId, boolean draw)` | Loads both player entities and increments win/loss/draw counters. |
| `getAllProfiles()` | Returns all players as `PlayerProfileView` sorted by score descending. |
| `deletePlayer(String playerId)` | Removes the `PlayerEntity` row. |
| `resetStats(String playerId)` | Sets wins, losses, draws to 0. |
| `getByPlayerId(EntityManager em, String playerId)` *(private)* | JPQL query helper — returns the `PlayerEntity` or null. |

---

## `server/infrastructure/jpa/GameRecordJpaRepository.java`

**JPA repository for the `game_records` table.**

### Methods

| Method | Description |
|--------|-------------|
| `saveFinishedGame(roomId, gameType, playerX, playerO, winner, draw)` | Persists a new `GameRecordEntity` with the current timestamp. |
| `getRecentGames(int limit)` | Returns up to `limit` game records ordered by `finishedAt` descending. |

---

## `server/infrastructure/jpa/JpaBootstrap.java`

Singleton factory for the `EntityManagerFactory`. Reads `META-INF/persistence.xml` via `Persistence.createEntityManagerFactory("game-platform-pu")`. Provides a single static method:

| Method | Description |
|--------|-------------|
| `emf()` | Returns the shared `EntityManagerFactory` instance. |

---

## `server/infrastructure/jpa/PlayerEntity.java`

JPA entity mapped to the `players` table.

| Field | Column | Description |
|-------|--------|-------------|
| `id` | `id` (PK, auto-increment) | Surrogate key |
| `playerId` | `player_id` (unique) | Business key chosen by the player |
| `playerName` | `player_name` | Display name |
| `wins` | `wins` | Win count |
| `losses` | `losses` | Loss count |
| `draws` | `draws` | Draw count |

Mutation methods: `incrementWin()`, `incrementLoss()`, `incrementDraw()`, `resetStats()`, `setPlayerName(String)`.

---

## `server/infrastructure/jpa/GameRecordEntity.java`

JPA entity mapped to the `game_records` table. Immutable after construction (all fields set in constructor, getters only).

| Field | Description |
|-------|-------------|
| `roomId` | UUID of the room (unique) |
| `gameType` | Game type name string |
| `playerX` | Player ID of X/Red/Yellow |
| `playerO` | Player ID of O/Black/Red |
| `winner` | Player ID of winner, or null for a draw |
| `draw` | True if the game ended in a draw |
| `finishedAt` | `Instant.now()` at construction time |

---

---

## `client/presentation/ClientMain.java`

**RMI console client entry point.** Connects to the RMI registry at `localhost:1099`, creates all client-side objects, and calls `controller.run()`.

---

## `client/presentation/ClientController.java`

**Console game loop controller.** Drives the player through registration → matchmaking → gameplay.

### Methods

| Method | Description |
|--------|-------------|
| `run()` | Registers the player, requests a Tic-Tac-Toe match, polls until matched, then calls `gameLoop()`. |
| `gameLoop()` | Repeatedly fetches the game state, prints the board, and either submits a move (player's turn) or waits 1 s (opponent's turn). Ends when `state.finished()` is true. |
| `sleep(long ms)` | `Thread.sleep` wrapper; propagates interruption correctly. |

---

## `client/presentation/ConsoleView.java`

**Terminal I/O for the console client.**

| Method | Description |
|--------|-------------|
| `askUsername()` | Prompts the user to enter a username and returns the input. |
| `printInfo(String message)` | Prints a line to stdout. |
| `askMove()` | Prompts for a `row,col` input; returns `int[]{row, col}`, or `{-1,-1}` on bad input. |
| `printBoard(GameStateView state)` | Renders the 3×3 Tic-Tac-Toe board with separators. |

---

## `client/application/ClientGameService.java`

**Client-side service layer.** Wraps all RMI calls and converts checked `RemoteException` into `RuntimeException`.

| Method | Description |
|--------|-------------|
| `register(String username)` | Calls `remote.registerPlayer(username)`. Returns `RegisterResponse`. |
| `requestTicTacToe(String sessionId)` | Calls `remote.requestMatch(sessionId, TIC_TAC_TOE)`. Returns `MatchTicket`. |
| `pollMatch(String sessionId, String ticketId)` | Calls `remote.pollMatch(...)`. Returns `MatchStatus`. |
| `move(String sessionId, String roomId, int row, int col)` | Builds a `MoveRequest` and calls `remote.makeMove(...)`. Returns `MoveResult`. |
| `state(String sessionId, String roomId)` | Calls `remote.getGameState(...)`. Returns `GameStateView`. |

---

## `client/application/RemoteGameGateway.java`

**RMI connection factory.**

| Method | Description |
|--------|-------------|
| `connect(String host, int port, String bindingName)` | Looks up the RMI registry at `host:port` and returns the `GameServerRemote` stub. |

---

## `client/model/ClientModel.java`

Simple mutable POJO that holds the client's in-memory state: `username`, `sessionId`, `roomId`, `symbol`. All fields have getters and setters.

---

---

## `shared/remote/GameServerRemote.java`

**RMI interface.** Extends `java.rmi.Remote`. All methods throw `RemoteException`. Both `RmiGameServer` (server side) and `ClientMain` (client side) use this contract.

| Method | Description |
|--------|-------------|
| `registerPlayer(String username)` | Returns `RegisterResponse` |
| `requestMatch(String sessionId, GameType gameType)` | Returns `MatchTicket` |
| `pollMatch(String sessionId, String ticketId)` | Returns `MatchStatus` |
| `makeMove(MoveRequest moveRequest)` | Returns `MoveResult` |
| `getGameState(String sessionId, String roomId)` | Returns `GameStateView` |
| `listSupportedGames()` | Returns `List<GameType>` |

---

## Shared DTOs (`shared/dto/`)

All records implement `Serializable` for RMI transport.

| Class | Fields | Description |
|-------|--------|-------------|
| `RegisterResponse` | `sessionId`, `username` | Returned after successful login |
| `MatchTicket` | `ticketId`, `message` | Returned when queuing for a game |
| `MatchStatus` | `matched`, `roomId`, `symbol`, `opponent`, `message`, `gameType` | Polling response — tells client if they've been paired |
| `MoveRequest` | `sessionId`, `roomId`, `row`, `col` | Tic-Tac-Toe move payload |
| `CheckersMoveRequest` | `sessionId`, `roomId`, `fromRow`, `fromCol`, `toRow`, `toCol` | Checkers move payload |
| `ConnectFourMoveRequest` | `sessionId`, `roomId`, `col` | Connect Four move payload (column drop) |
| `MoveResult` | `accepted`, `message` | Response to any move attempt |
| `GameStateView` | `roomId`, `board[][]`, `currentTurn`, `winner`, `draw`, `finished`, `playerX`, `playerO`, `playerXName`, `playerOName`, `statusMessage` | Full board snapshot |
| `PlayerProfileView` | `playerId`, `playerName`, `wins`, `losses`, `draws`, `score` | Player stats; score = wins×3 + draws |
| `GameType` | `TIC_TAC_TOE`, `CHECKERS`, `CONNECT_FOUR`, `BATTLESHIP` | Game type enum; BATTLESHIP is a stub |
| `BattleshipStateView` | *(empty)* | Future stub |

---

## `META-INF/persistence.xml`

JPA configuration file. Defines the persistence unit `"game-platform-pu"` and lists the managed entity classes (`PlayerEntity`, `GameRecordEntity`). Uses Hibernate as the JPA provider with `hibernate.hbm2ddl.auto=update` so tables are created/migrated automatically.

---

## `web/index.html`

Single-page application served at `GET /`. All game interaction happens through `fetch()` calls to the REST API. Features:

- **Login / registration** — enter a Player ID and display name.
- **Game selection** — choose Tic-Tac-Toe, Checkers, or Connect Four.
- **Matchmaking** — polls `/api/match/poll` every second until paired.
- **Live game board** — renders Tic-Tac-Toe (3×3), Checkers (8×8), or Connect Four (6×7). Sends moves via the appropriate endpoint.
- **Scoreboard** — fetches `/api/scoreboard`.
- **Admin panel** — visible only after logging in as `admin`. Shows live rooms (auto-refreshes every 5 s), recent games, and player management controls (delete, reset stats, force-end room).
- **Sign out** — clears session and returns to the login screen.

## What You Can Do

- Connect with `playerId` and `playerName`.
- Player profile is loaded on login.
- Profile and score are persisted in SQLite.
- Choose a game from the supported list.
- Match with another remote player.
- If a player exits/disconnects during a match, that player loses by forfeit.

## Tech Stack

- Java 17
- HTTP web server (`HttpServer` from JDK)
- Layered architecture with strict MVC boundaries
- JPA (Hibernate) for core persistence
- JDBC for move-audit persistence
- SQLite database (`web-game-platform.db`)
- Thread-safe matchmaking and game state handling

## Architecture

### Layered Architecture

- Presentation layer:
  - Web server + endpoints: `com.webapp.server.presentation.WebAppHttpServer`
  - Server bootstrap: `com.webapp.server.presentation.ServerMain`
- Application layer:
  - `com.webapp.server.application.GamePlatformService`
  - `com.webapp.server.application.MatchmakingService`
- Domain layer:
  - `com.webapp.server.domain.*`
- Infrastructure layer:
  - JPA repositories/entities: `com.webapp.server.infrastructure.jpa.*`
  - JDBC repository: `com.webapp.server.infrastructure.jdbc.JdbcAuditRepository`
  - Persistence orchestration: `com.webapp.server.infrastructure.PersistenceService`
- Shared contract layer:
  - DTOs: `com.webapp.shared.dto.*`

### MVC (Web)

- Model: domain entities and game state (`GameRoom`, player profile model)
- View: browser UI at `src/web/index.html`
- Controller: HTTP endpoint handlers in `WebAppHttpServer`

## Threading Model

- Matchmaking uses a dedicated worker thread in `MatchmakingService`.
- Waiting queues are per-game-type and thread-safe (`LinkedBlockingQueue`).
- Active maps use `ConcurrentHashMap`.
- Turn processing and heartbeat/forfeit logic in `GameRoom` are synchronized.

## Persistence Model

- JPA/Hibernate:
  - `players` table (playerId, playerName, wins/losses/draws)
  - `game_records` table
- JDBC:
  - `game_move_audit` table

## Run Instructions

1. Build:

```bash
mvn clean compile
```

2. Start server:

```bash
mvn exec:java
```

3. Open browser:

- http://localhost:8080

4. In browser:

- Enter `playerId` and `playerName`
- View profile and score
- Choose a game
- Queue and play

## Notes

- Forfeit behavior:
  - Explicit leave triggers immediate loss.
  - Disconnect/close tab triggers loss by heartbeat timeout.
- If you are migrating from an older DB schema, keep using the new DB file `web-game-platform.db`.

## Extension Points

- Add game engines for `CHECKERS` and `BATTLESHIP` in domain/application layers.
- Extend matchmaking for game-specific room logic.
- Add auth tokens and reconnect restoration.
- Add leaderboard and match history pages.
