/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.tools.MessageReadStatus;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Data access object for message and message-read-status tables.
 *
 * <p>Mirrors Python's {@code MessageDao} in
 * {@code openjiuwen/agent_teams/tools/database/message_dao.py}.</p>
 */
public class MessageDao {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final int DB_RETRY_ATTEMPTS = 3;
    private static final long DB_RETRY_BASE_DELAY_MILLIS = 500L;

    private final DatabaseEngine engine;

    public MessageDao() {
        this(createDefaultEngine());
    }

    public MessageDao(DatabaseEngine engine) {
        this.engine = engine;
        ensureInitialized();
    }

    public CompletableFuture<Optional<TeamMessage>> getMessage(String messageId) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT message_id, team_name, from_member_name, to_member_name, content, timestamp, "
                                + "broadcast, is_read FROM " + currentMessageTable() + " WHERE message_id = ?")) {
                    statement.setString(1, messageId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(mapMessage(resultSet));
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get message " + messageId, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> createMessage(
            String messageId,
            String teamName,
            String fromMemberName,
            String content,
            String toMemberName,
            boolean broadcast,
            boolean isRead) {
        return supplyAsyncWithSessionContext(() -> {
            for (int attempt = 0; attempt < DB_RETRY_ATTEMPTS; attempt++) {
                try {
                    synchronized (engine) {
                        insertMessageRow(messageId, teamName, fromMemberName, content, toMemberName, broadcast, isRead);
                    }
                    TEAM_LOGGER.info("Message %s created", messageId);
                    return true;
                } catch (SQLException exception) {
                    if (isIntegrityViolation(exception)) {
                        TEAM_LOGGER.error("Failed to create %s, reason is %s", messageId, exception.getMessage());
                        return false;
                    }
                    if (isRetryableLockError(exception) && attempt < DB_RETRY_ATTEMPTS - 1) {
                        long retryDelayMillis = DB_RETRY_BASE_DELAY_MILLIS * (1L << attempt);
                        TEAM_LOGGER.warning(
                                "Database locked on create_message (attempt %d), retrying in %ss",
                                attempt + 1,
                                retryDelayMillis / 1000.0
                        );
                        sleep(retryDelayMillis);
                        continue;
                    }
                    if (isRetryableLockError(exception)) {
                        TEAM_LOGGER.error(
                                "Failed to create message %s after %d attempts: %s",
                                messageId,
                                DB_RETRY_ATTEMPTS,
                                exception.getMessage()
                        );
                        return false;
                    }
                    throw new RuntimeException("Failed to create message " + messageId, exception);
                }
            }
            return false;
        });
    }

    public CompletableFuture<List<TeamMessage>> getMessages(
            String teamName,
            String toMemberName,
            boolean unreadOnly,
            String fromMemberName) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT message_id, team_name, from_member_name, to_member_name, content, timestamp, "
                                + "broadcast, is_read FROM " + currentMessageTable()
                                + " WHERE team_name = ? AND to_member_name = ? AND broadcast = ? "
                                + "ORDER BY timestamp")) {
                    statement.setString(1, teamName);
                    statement.setString(2, toMemberName);
                    statement.setBoolean(3, false);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamMessage> rows = new ArrayList<>();
                        while (resultSet.next()) {
                            TeamMessage message = mapMessage(resultSet);
                            if (fromMemberName != null && !fromMemberName.equals(message.getFromMemberName())) {
                                continue;
                            }
                            if (unreadOnly && !Boolean.FALSE.equals(message.getIsRead())) {
                                continue;
                            }
                            rows.add(message);
                        }
                        rows.sort(Comparator.comparing(TeamMessage::getTimestamp));
                        return rows;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get direct messages for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamMessage>> getBroadcastMessages(
            String teamName,
            String memberName,
            boolean unreadOnly,
            String fromMemberName) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    List<TeamMessage> rows = new ArrayList<>();
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT message_id, team_name, from_member_name, to_member_name, content, timestamp, "
                                    + "broadcast, is_read FROM " + currentMessageTable()
                                    + " WHERE team_name = ? AND broadcast = ? AND from_member_name <> ? "
                                    + "ORDER BY timestamp")) {
                        statement.setString(1, teamName);
                        statement.setBoolean(2, true);
                        statement.setString(3, memberName);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                TeamMessage message = mapMessage(resultSet);
                                if (fromMemberName != null && !fromMemberName.equals(message.getFromMemberName())) {
                                    continue;
                                }
                                rows.add(message);
                            }
                        }
                    }
                    if (!unreadOnly) {
                        return rows;
                    }

                    Long readAt = null;
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT member_name, team_name, read_at FROM " + currentReadStatusTable()
                                    + " WHERE member_name = ? AND team_name = ?")) {
                        statement.setString(1, memberName);
                        statement.setString(2, teamName);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                MessageReadStatus readStatus = mapReadStatus(resultSet);
                                readAt = readStatus.getReadAt();
                            }
                        }
                    }

                    List<TeamMessage> filtered = new ArrayList<>();
                    for (TeamMessage row : rows) {
                        if (readAt == null || row.getTimestamp() > readAt) {
                            filtered.add(row);
                        }
                    }
                    return filtered;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get broadcast messages for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamMessage>> getTeamMessages(String teamName, Boolean broadcast) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT message_id, team_name, from_member_name, to_member_name, content, timestamp, "
                                + "broadcast, is_read FROM " + currentMessageTable()
                                + " WHERE team_name = ? ORDER BY timestamp")) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamMessage> rows = new ArrayList<>();
                        while (resultSet.next()) {
                            TeamMessage message = mapMessage(resultSet);
                            if (broadcast == null || broadcast.equals(message.getBroadcast())) {
                                rows.add(message);
                            }
                        }
                        rows.sort(Comparator.comparing(TeamMessage::getTimestamp));
                        return rows;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get team messages for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> hasUnreadMessages(String teamName, boolean includeBroadcast) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT message_id FROM " + currentMessageTable()
                                    + " WHERE team_name = ? AND broadcast = ? AND is_read = ? LIMIT 1")) {
                        statement.setString(1, teamName);
                        statement.setBoolean(2, false);
                        statement.setBoolean(3, false);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (resultSet.next()) {
                                return true;
                            }
                        }
                    }

                    if (!includeBroadcast) {
                        return false;
                    }

                    List<TeamMessage> broadcasts = new ArrayList<>();
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT message_id, team_name, from_member_name, to_member_name, content, timestamp, "
                                    + "broadcast, is_read FROM " + currentMessageTable()
                                    + " WHERE team_name = ? AND broadcast = ?")) {
                        statement.setString(1, teamName);
                        statement.setBoolean(2, true);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                broadcasts.add(mapMessage(resultSet));
                            }
                        }
                    }
                    if (broadcasts.isEmpty()) {
                        return false;
                    }

                    List<String> members = new ArrayList<>();
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT member_name, team_name, display_name, \"desc\", agent_card, status, "
                                    + "execution_status, mode, role, prompt, model_ref_json, updated_at "
                                    + "FROM team_member WHERE team_name = ?")) {
                        statement.setString(1, teamName);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                TeamMember member = mapTeamMember(resultSet);
                                members.add(member.getMemberName());
                            }
                        }
                    }

                    Map<String, Long> readAtByMember = new HashMap<>();
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT member_name, team_name, read_at FROM " + currentReadStatusTable()
                                    + " WHERE team_name = ?")) {
                        statement.setString(1, teamName);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                MessageReadStatus readStatus = mapReadStatus(resultSet);
                                readAtByMember.put(readStatus.getMemberName(), readStatus.getReadAt());
                            }
                        }
                    }

                    for (String memberName : members) {
                        Long watermark = readAtByMember.get(memberName);
                        for (TeamMessage message : broadcasts) {
                            if (memberName.equals(message.getFromMemberName())) {
                                continue;
                            }
                            if (watermark == null || message.getTimestamp() > watermark) {
                                return true;
                            }
                        }
                    }
                    return false;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to check unread messages for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> hasUnreadMessages(String teamName) {
        return hasUnreadMessages(teamName, true);
    }

    public CompletableFuture<Boolean> markMessageRead(String messageId, String memberName) {
        return supplyAsyncWithSessionContext(() -> {
            synchronized (engine) {
                try {
                    TeamMessage message;
                    try (PreparedStatement statement = connection().prepareStatement(
                            "SELECT message_id, team_name, from_member_name, to_member_name, content, timestamp, "
                                    + "broadcast, is_read FROM " + currentMessageTable() + " WHERE message_id = ?")) {
                        statement.setString(1, messageId);
                        try (ResultSet resultSet = statement.executeQuery()) {
                            if (!resultSet.next()) {
                                TEAM_LOGGER.error("Message %s not found", messageId);
                                return false;
                            }
                            message = mapMessage(resultSet);
                        }
                    }

                    if ("user".equals(memberName)) {
                        if (Boolean.TRUE.equals(message.getBroadcast())) {
                            TEAM_LOGGER.error("'user' pseudo-member cannot read broadcast message %s", messageId);
                            return false;
                        }
                    } else if (!memberExists(memberName, message.getTeamName())) {
                        TEAM_LOGGER.error("Member %s not found", memberName);
                        return false;
                    }

                    if (Boolean.TRUE.equals(message.getBroadcast())) {
                        upsertReadStatus(memberName, message.getTeamName(), message.getTimestamp());
                    } else {
                        try (PreparedStatement statement = connection().prepareStatement(
                                "UPDATE " + currentMessageTable() + " SET is_read = ? WHERE message_id = ?")) {
                            statement.setBoolean(1, true);
                            statement.setString(2, messageId);
                            statement.executeUpdate();
                        }
                    }

                    TEAM_LOGGER.info("Message %s marked as read by %s", messageId, memberName);
                    return true;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to mark message " + messageId + " read", exception);
                }
            }
        });
    }

    private static DatabaseEngine createDefaultEngine() {
        DatabaseConfig config = new DatabaseConfig();
        config.setConnectionString(":memory:");
        DatabaseEngine engine = new DatabaseEngine(config);
        engine.initialize().join();
        return engine;
    }

    private void ensureInitialized() {
        if (!engine.isInitialized()) {
            engine.initialize().join();
        }
        ensureCurrentSessionTables();
    }

    private Connection connection() {
        Connection connection = engine.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Database engine is not initialized");
        }
        return connection;
    }

    private <T> CompletableFuture<T> supplyAsyncWithSessionContext(Supplier<T> supplier) {
        String sessionId = AgentTeamsContext.getSessionId();
        return CompletableFuture.supplyAsync(() -> {
            AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId(sessionId);
            try {
                ensureCurrentSessionTables();
                return supplier.get();
            } finally {
                AgentTeamsContext.resetSessionId(token);
            }
        });
    }

    private void ensureCurrentSessionTables() {
        String sessionId = AgentTeamsContext.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String suffix = DatabaseEngine.sanitizeSessionIdForTable(sessionId);
        synchronized (engine) {
            try (Statement statement = connection().createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS "team_message_%s" (
                            message_id VARCHAR(255) PRIMARY KEY,
                            team_name VARCHAR(255) NOT NULL,
                            from_member_name VARCHAR(255) NOT NULL,
                            to_member_name VARCHAR(255),
                            content CLOB NOT NULL,
                            timestamp BIGINT NOT NULL,
                            broadcast BOOLEAN NOT NULL,
                            is_read BOOLEAN
                        )
                        """.formatted(suffix));
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS "message_read_status_%s" (
                            member_name VARCHAR(255) NOT NULL,
                            team_name VARCHAR(255) NOT NULL,
                            read_at BIGINT,
                            PRIMARY KEY (member_name, team_name)
                        )
                        """.formatted(suffix));
            } catch (SQLException exception) {
                throw new RuntimeException("Failed to ensure message session tables", exception);
            }
        }
    }

    private boolean memberExists(String memberName, String teamName) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT member_name FROM team_member WHERE member_name = ? AND team_name = ?")) {
            statement.setString(1, memberName);
            statement.setString(2, teamName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void insertMessageRow(
            String messageId,
            String teamName,
            String fromMemberName,
            String content,
            String toMemberName,
            boolean broadcast,
            boolean isRead) throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(
                "INSERT INTO " + currentMessageTable()
                        + " (message_id, team_name, from_member_name, to_member_name, content, "
                        + "timestamp, broadcast, is_read) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, messageId);
            statement.setString(2, teamName);
            statement.setString(3, fromMemberName);
            statement.setString(4, toMemberName);
            statement.setString(5, content);
            statement.setLong(6, DatabaseEngine.getCurrentTime());
            statement.setBoolean(7, broadcast);
            if (broadcast) {
                statement.setObject(8, null);
            } else {
                statement.setBoolean(8, isRead);
            }
            statement.executeUpdate();
        }
    }

    private void upsertReadStatus(String memberName, String teamName, Long timestamp) throws SQLException {
        Long currentReadAt = null;
        boolean found = false;
        try (PreparedStatement select = connection().prepareStatement(
                "SELECT read_at FROM " + currentReadStatusTable() + " WHERE member_name = ? AND team_name = ?")) {
            select.setString(1, memberName);
            select.setString(2, teamName);
            try (ResultSet resultSet = select.executeQuery()) {
                if (resultSet.next()) {
                    found = true;
                    currentReadAt = getNullableLong(resultSet, "read_at");
                }
            }
        }
        if (!found) {
            try (PreparedStatement insert = connection().prepareStatement(
                    "INSERT INTO " + currentReadStatusTable()
                            + " (member_name, team_name, read_at) VALUES (?, ?, ?)")) {
                insert.setString(1, memberName);
                insert.setString(2, teamName);
                insert.setLong(3, timestamp);
                insert.executeUpdate();
            }
            return;
        }
        if (currentReadAt == null || timestamp > currentReadAt) {
            try (PreparedStatement update = connection().prepareStatement(
                    "UPDATE " + currentReadStatusTable() + " SET read_at = ? WHERE member_name = ? AND team_name = ?")) {
                update.setLong(1, timestamp);
                update.setString(2, memberName);
                update.setString(3, teamName);
                update.executeUpdate();
            }
        }
    }

    private String currentMessageTable() {
        return quotedTableName("team_message_" + currentSessionSuffix());
    }

    private String currentReadStatusTable() {
        return quotedTableName("message_read_status_" + currentSessionSuffix());
    }

    private String currentSessionSuffix() {
        String sessionId = AgentTeamsContext.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IllegalStateException("No session_id in context");
        }
        return DatabaseEngine.sanitizeSessionIdForTable(sessionId);
    }

    private String quotedTableName(String rawTableName) {
        return "\"" + rawTableName.replace("\"", "\"\"") + "\"";
    }

    private boolean isIntegrityViolation(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("23")) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("unique");
    }

    private boolean isRetryableLockError(SQLException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("database is locked")
                || normalized.contains("database table is locked")
                || normalized.contains("lock wait timeout")
                || normalized.contains("deadlock");
    }

    private void sleep(long delayMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying database write", interruptedException);
        }
    }

    private TeamMessage mapMessage(ResultSet resultSet) throws SQLException {
        return new TeamMessage(
                resultSet.getString("message_id"),
                resultSet.getString("team_name"),
                resultSet.getString("from_member_name"),
                resultSet.getString("to_member_name"),
                resultSet.getString("content"),
                resultSet.getLong("timestamp"),
                resultSet.getBoolean("broadcast"),
                getNullableBoolean(resultSet, "is_read")
        );
    }

    private MessageReadStatus mapReadStatus(ResultSet resultSet) throws SQLException {
        return new MessageReadStatus(
                resultSet.getString("member_name"),
                resultSet.getString("team_name"),
                getNullableLong(resultSet, "read_at")
        );
    }

    private TeamMember mapTeamMember(ResultSet resultSet) throws SQLException {
        return new TeamMember(
                resultSet.getString("member_name"),
                resultSet.getString("team_name"),
                resultSet.getString("display_name"),
                resultSet.getString("desc"),
                resultSet.getString("agent_card"),
                resultSet.getString("status"),
                resultSet.getString("execution_status"),
                resultSet.getString("mode"),
                resultSet.getString("role"),
                resultSet.getString("prompt"),
                resultSet.getString("model_ref_json"),
                getNullableLong(resultSet, "updated_at")
        );
    }

    private Long getNullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Boolean getNullableBoolean(ResultSet resultSet, String columnName) throws SQLException {
        boolean value = resultSet.getBoolean(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
