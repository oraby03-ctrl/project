# Multiplayer Web Game Platform (MVC + Layered Architecture)

This project provides a browser-based multiplayer platform for turn-based games.
Currently implemented game logic: `TIC_TAC_TOE`.

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
