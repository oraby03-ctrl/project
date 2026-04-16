package com.webapp.server.infrastructure.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JdbcAuditRepository {
    private final String jdbcUrl;

    public JdbcAuditRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    public void logMove(String roomId, int moveNo, String username, String symbol, int row, int col) {
        String sql = "insert into game_move_audit(room_id, move_no, username, symbol, row_idx, col_idx) values(?, ?, ?, ?, ?, ?)";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setInt(2, moveNo);
            ps.setString(3, username);
            ps.setString(4, symbol);
            ps.setInt(5, row);
            ps.setInt(6, col);
            ps.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to log move", exception);
        }
    }

    private void initSchema() {
        String sql = "create table if not exists game_move_audit ("
                + "id integer primary key autoincrement,"
                + "room_id text not null,"
                + "move_no integer not null,"
                + "username text not null,"
                + "symbol text not null,"
                + "row_idx integer not null,"
                + "col_idx integer not null,"
                + "created_at datetime default current_timestamp"
                + ")";
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize JDBC schema", exception);
        }
    }
}
