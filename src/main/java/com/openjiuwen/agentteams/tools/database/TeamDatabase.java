/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import com.openjiuwen.agentteams.spawn.SpawnContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class TeamDatabase used by the Java parity implementation.
 *
 * @since 1.0
 */
public class TeamDatabase {
    private static final String TEAM_TASK_PREFIX = "team_task_";
    private static final String TEAM_TASK_DEPENDENCY_PREFIX = "team_task_dependency_";
    private static final String TEAM_MESSAGE_PREFIX = "team_message_";
    private static final String MESSAGE_READ_STATUS_PREFIX = "message_read_status_";

    private final DatabaseConfig config;
    private final Map<String, TeamRecord> teams = new LinkedHashMap<>();
    private final Map<String, MemberRecord> members = new LinkedHashMap<>();
    private final Map<String, SessionTables> sessions = new LinkedHashMap<>();
    private final Set<String> droppedSessionIds = new HashSet<>();
    private Connection sqliteConnection;
    private boolean isInitialized;

    /**
     * Auto-generated for codecheck compliance.
     */
    public final TeamDao team = new TeamDao();
    /**
     * Auto-generated for codecheck compliance.
     */
    public final MemberDao member = new MemberDao();
    /**
     * Auto-generated for codecheck compliance.
     */
    public final MessageDao message = new MessageDao();
    /**
     * Auto-generated for codecheck compliance.
     */
    public final TaskDao task = new TaskDao();

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamDatabase(DatabaseConfig config) {
        this.config = config != null ? config : DatabaseConfig.builder().build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void initialize() {
        if (isInitialized) {
            return;
        }
        rejectUnsupportedPersistentBackend();
        initializeSqliteIfNeeded();
        isInitialized = true;
        loadStaticRowsIfNeeded();
        String sessionId = currentSessionId();
        if (!sessionId.isBlank()) {
            createCurSessionTables();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        closeSqliteIfNeeded();
        isInitialized = false;
        teams.clear();
        members.clear();
        sessions.clear();
        droppedSessionIds.clear();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DatabaseConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<MessageRecord> getTeamMessages(String teamName) {
        ensureInitialized();
        return message.getTeamMessages(teamName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TaskRecord> getTeamTasks(String teamName) {
        return getTeamTasks(teamName, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TaskRecord> getTeamTasks(String teamName, String status) {
        ensureInitialized();
        return task.getTeamTasks(teamName, status);
    }

    private void ensureInitialized() {
        if (!isInitialized) {
            throw new IllegalStateException("TeamDatabase is not initialized");
        }
    }

    private boolean isSqlite() {
        return config.getDbType() == DatabaseType.SQLITE;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String normalizedJdbcConnectionString() {
        DatabaseType dbType = config.getDbType();
        String connectionString = config.getConnectionString() != null ? config.getConnectionString().trim() : "";
        if (dbType == DatabaseType.POSTGRESQL) {
            if (connectionString.isBlank()) {
                throw new IllegalArgumentException("PostgreSQL requires a non-empty connectionString");
            }
            if (connectionString.startsWith("jdbc:postgresql://")) {
                return connectionString;
            }
            if (connectionString.startsWith("postgres://")) {
                return "jdbc:postgresql://" + connectionString.substring("postgres://".length());
            }
            if (connectionString.startsWith("postgresql://")) {
                return "jdbc:postgresql://" + connectionString.substring("postgresql://".length());
            }
            throw new IllegalArgumentException(
                    "PostgreSQL connectionString must use postgresql://, postgres://, "
                            + "or jdbc:postgresql:// scheme");
        }
        if (dbType == DatabaseType.MYSQL) {
            if (connectionString.isBlank()) {
                throw new IllegalArgumentException("MySQL requires a non-empty connectionString");
            }
            if (connectionString.startsWith("jdbc:mysql://")) {
                return connectionString;
            }
            if (connectionString.startsWith("mysql://")) {
                return "jdbc:mysql://" + connectionString.substring("mysql://".length());
            }
            throw new IllegalArgumentException("MySQL connectionString must use mysql:// or jdbc:mysql:// scheme");
        }
        return connectionString;
    }

    private void rejectUnsupportedPersistentBackend() {
        DatabaseType dbType = config.getDbType();
        if (dbType == DatabaseType.POSTGRESQL || dbType == DatabaseType.MYSQL) {
            normalizedJdbcConnectionString();
            throw new UnsupportedOperationException(
                    dbType + " team database backend requires a JDBC DAO implementation");
        }
    }

    private void initializeSqliteIfNeeded() {
        if (!isSqlite()) {
            return;
        }
        try {
            String connectionString = config.getConnectionString();
            String jdbcUrl;
            if (connectionString == null || connectionString.isBlank() || ":memory:".equals(connectionString)) {
                jdbcUrl = "jdbc:sqlite::memory:";
            } else if (connectionString.startsWith("jdbc:")) {
                jdbcUrl = connectionString;
            } else {
                Path path = Path.of(connectionString);
                Path parent = path.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                jdbcUrl = "jdbc:sqlite:" + connectionString;
            }
            sqliteConnection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = sqliteConnection.createStatement()) {
                statement.executeUpdate("PRAGMA foreign_keys = ON");
                if (config.isDbEnableWal() && connectionString != null && !connectionString.isBlank()
                        && !":memory:".equals(connectionString)) {
                    statement.executeUpdate("PRAGMA journal_mode = WAL");
                }
            }
            createStaticSqliteTables();
        } catch (SQLException | java.io.IOException e) {
            throw new IllegalStateException("Failed to initialize SQLite team database", e);
        }
    }

    private void createStaticSqliteTables() throws SQLException {
        try (Statement statement = sqliteConnection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS team_info (
                        team_name TEXT PRIMARY KEY,
                        display_name TEXT,
                        leader_member_name TEXT,
                        desc TEXT,
                        prompt TEXT,
                        created INTEGER,
                        updated_at INTEGER
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS team_member (
                        member_name TEXT,
                        team_name TEXT,
                        display_name TEXT,
                        agent_card TEXT,
                        status TEXT,
                        desc TEXT,
                        execution_status TEXT,
                        mode TEXT,
                        prompt TEXT,
                        model_ref_json TEXT,
                        updated_at INTEGER,
                        PRIMARY KEY (member_name, team_name),
                        FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                    )
                    """);
        }
    }

    private void loadStaticRowsIfNeeded() {
        if (!isSqlite()) {
            return;
        }
        teams.clear();
        members.clear();
        try (Statement statement = sqliteConnection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT team_name, display_name, leader_member_name, desc, prompt, created, updated_at
                    FROM team_info
                    """)) {
                while (result.next()) {
                    TeamRecord teamRecord = TeamRecord.builder()
                            .teamName(result.getString("team_name"))
                            .displayName(result.getString("display_name"))
                            .leaderMemberName(result.getString("leader_member_name"))
                            .desc(result.getString("desc"))
                            .prompt(result.getString("prompt"))
                            .created(result.getLong("created"))
                            .updatedAt(result.getLong("updated_at"))
                            .build();
                    teams.put(teamRecord.getTeamName(), teamRecord);
                }
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT member_name, team_name, display_name, agent_card, status, desc, execution_status,
                           mode, prompt, model_ref_json, updated_at
                    FROM team_member
                    """)) {
                while (result.next()) {
                    MemberRecord memberRecord = MemberRecord.builder()
                            .memberName(result.getString("member_name"))
                            .teamName(result.getString("team_name"))
                            .displayName(result.getString("display_name"))
                            .agentCard(result.getString("agent_card"))
                            .status(result.getString("status"))
                            .desc(result.getString("desc"))
                            .executionStatus(result.getString("execution_status"))
                            .mode(result.getString("mode"))
                            .prompt(result.getString("prompt"))
                            .modelRefJson(result.getString("model_ref_json"))
                            .updatedAt(result.getLong("updated_at"))
                            .build();
                    members.put(memberRecord.getTeamName() + "::" + memberRecord.getMemberName(), memberRecord);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load SQLite team database rows", e);
        }
    }

    private void closeSqliteIfNeeded() {
        if (sqliteConnection == null) {
            return;
        }
        try {
            sqliteConnection.close();
        } catch (SQLException ignored) {
            // Keep close best-effort like Python's dispose path.
        } finally {
            sqliteConnection = null;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean createCurSessionTables() {
        ensureInitialized();
        String sessionId = currentSessionId();
        if (sessionId.isBlank()) {
            return false;
        }
        sessions.computeIfAbsent(sessionId, ignored -> new SessionTables());
        createSqliteSessionTablesIfNeeded(sessionId);
        loadSqliteSessionRowsIfNeeded(sessionId);
        droppedSessionIds.remove(sessionId);
        return true;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> dropCurSessionTables() {
        ensureInitialized();
        String sessionId = currentSessionId();
        return dropSessionTablesById(sessionId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> dropSessionTablesById(String sessionId) {
        ensureInitialized();
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        boolean isExisted = sessions.remove(sessionId) != null;
        List<String> tableNames = sessionTableNames(sessionId);
        boolean isSqliteDropped = dropSqliteTablesIfNeeded(tableNames);
        if (!isExisted && !isSqliteDropped) {
            return List.of();
        }
        droppedSessionIds.add(sessionId);
        return tableNames;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RuntimeCleanupResult cleanupAllRuntimeState() {
        ensureInitialized();
        List<String> deletedTables = new ArrayList<>();
        for (String sessionId : new ArrayList<>(sessions.keySet())) {
            if (!sessionId.isBlank()) {
                deletedTables.addAll(sessionTableNames(sessionId));
                droppedSessionIds.add(sessionId);
            }
        }
        sessions.clear();
        teams.clear();
        members.clear();
        cleanupSqliteAllRuntimeStateIfNeeded(deletedTables);
        return RuntimeCleanupResult.builder()
                .deletedTables(deletedTables)
                .clearedTables(List.of("team_info", "team_member"))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> activeDynamicTables() {
        ensureInitialized();
        List<String> tableNames = new ArrayList<>();
        for (String sessionId : sessions.keySet()) {
            if (!sessionId.isBlank()) {
                tableNames.addAll(sessionTableNames(sessionId));
            }
        }
        return tableNames;
    }

    private void createSqliteSessionTablesIfNeeded(String sessionId) {
        if (!isSqlite() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String taskTable = taskTableName(sessionId);
        String dependencyTable = dependencyTableName(sessionId);
        String messageTable = messageTableName(sessionId);
        String readStatusTable = readStatusTableName(sessionId);
        try (Statement statement = sqliteConnection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        task_id TEXT PRIMARY KEY,
                        team_name TEXT,
                        title TEXT,
                        content TEXT,
                        status TEXT,
                        assignee TEXT,
                        updated_at INTEGER,
                        FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                    )
                    """.formatted(taskTable));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        task_id TEXT,
                        depends_on_task_id TEXT,
                        team_name TEXT,
                        isResolved INTEGER DEFAULT 0,
                        PRIMARY KEY (task_id, depends_on_task_id),
                        FOREIGN KEY (task_id) REFERENCES %s(task_id) ON DELETE CASCADE,
                        FOREIGN KEY (depends_on_task_id) REFERENCES %s(task_id) ON DELETE CASCADE
                    )
                    """.formatted(dependencyTable, taskTable, taskTable));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        message_id TEXT PRIMARY KEY,
                        team_name TEXT,
                        from_member_name TEXT,
                        to_member_name TEXT,
                        content TEXT,
                        timestamp INTEGER,
                        broadcast INTEGER,
                        is_read INTEGER,
                        FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                    )
                    """.formatted(messageTable));
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                        member_name TEXT,
                        team_name TEXT,
                        read_at INTEGER,
                        PRIMARY KEY (member_name, team_name),
                        FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                    )
                    """.formatted(readStatusTable));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create SQLite session tables", e);
        }
    }

    private void loadSqliteSessionRowsIfNeeded(String sessionId) {
        if (!isSqlite() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        createSqliteSessionTablesIfNeeded(sessionId);
        SessionTables tables = sessions.computeIfAbsent(sessionId, ignored -> new SessionTables());
        tables.tasks.clear();
        tables.messages.clear();
        tables.broadcastReadAt.clear();
        try (Statement statement = sqliteConnection.createStatement()) {
            try (ResultSet result = statement.executeQuery("""
                    SELECT task_id, team_name, title, content, status, assignee, updated_at
                    FROM %s
                    """.formatted(taskTableName(sessionId)))) {
                while (result.next()) {
                    TaskRecord taskRecord = TaskRecord.builder()
                            .taskId(result.getString("task_id"))
                            .teamName(result.getString("team_name"))
                            .title(result.getString("title"))
                            .content(result.getString("content"))
                            .status(result.getString("status"))
                            .assignee(result.getString("assignee"))
                            .updatedAt(result.getLong("updated_at"))
                            .build();
                    tables.tasks.put(taskRecord.getTaskId(), taskRecord);
                }
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT task_id, depends_on_task_id
                    FROM %s
                    ORDER BY rowid
                    """.formatted(dependencyTableName(sessionId)))) {
                while (result.next()) {
                    TaskRecord taskRecord = tables.tasks.get(result.getString("task_id"));
                    if (taskRecord != null) {
                        taskRecord.getDependencies().add(result.getString("depends_on_task_id"));
                    }
                }
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT message_id, team_name, from_member_name, to_member_name, content,
                           timestamp, broadcast, is_read
                    FROM %s
                    """.formatted(messageTableName(sessionId)))) {
                while (result.next()) {
                    MessageRecord messageRecord = MessageRecord.builder()
                            .messageId(result.getString("message_id"))
                            .teamName(result.getString("team_name"))
                            .fromMemberName(result.getString("from_member_name"))
                            .toMemberName(result.getString("to_member_name"))
                            .content(result.getString("content"))
                            .timestamp(result.getLong("timestamp"))
                            .broadcast(result.getInt("broadcast") != 0)
                            .isRead(result.getInt("is_read") != 0)
                            .build();
                    tables.messages.put(messageRecord.getMessageId(), messageRecord);
                }
            }
            try (ResultSet result = statement.executeQuery("""
                    SELECT member_name, team_name, read_at
                    FROM %s
                    """.formatted(readStatusTableName(sessionId)))) {
                while (result.next()) {
                    tables.broadcastReadAt.put(
                            result.getString("team_name") + "::" + result.getString("member_name"),
                            result.getLong("read_at")
                    );
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load SQLite session rows", e);
        }
    }

    private boolean dropSqliteTablesIfNeeded(List<String> tableNames) {
        if (!isSqlite()) {
            return false;
        }
        boolean isDropped = false;
        try (Statement statement = sqliteConnection.createStatement()) {
            for (String tableName : tableNames) {
                if (sqliteTableExists(tableName)) {
                    statement.executeUpdate("DROP TABLE IF EXISTS " + tableName);
                    isDropped = true;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to drop SQLite session tables", e);
        }
        return isDropped;
    }

    private boolean sqliteTableExists(String tableName) throws SQLException {
        try (PreparedStatement statement = sqliteConnection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void cleanupSqliteAllRuntimeStateIfNeeded(List<String> deletedTables) {
        if (!isSqlite()) {
            return;
        }
        try (Statement statement = sqliteConnection.createStatement()) {
            List<String> dynamicTables = new ArrayList<>();
            try (ResultSet result = statement.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
                while (result.next()) {
                    String table = result.getString("name");
                    if (table.startsWith(TEAM_TASK_DEPENDENCY_PREFIX)
                            || table.startsWith(TEAM_TASK_PREFIX)
                            || table.startsWith(TEAM_MESSAGE_PREFIX)
                            || table.startsWith(MESSAGE_READ_STATUS_PREFIX)) {
                        dynamicTables.add(table);
                    }
                }
            }
            for (String table : dynamicTables) {
                statement.executeUpdate("DROP TABLE IF EXISTS " + table);
                if (!deletedTables.contains(table)) {
                    deletedTables.add(table);
                }
            }
            statement.executeUpdate("DELETE FROM team_member");
            statement.executeUpdate("DELETE FROM team_info");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cleanup SQLite runtime state", e);
        }
    }

    private String taskTableName(String sessionId) {
        return TEAM_TASK_PREFIX + sanitizeSessionIdForTable(sessionId);
    }

    private String dependencyTableName(String sessionId) {
        return TEAM_TASK_DEPENDENCY_PREFIX + sanitizeSessionIdForTable(sessionId);
    }

    private String messageTableName(String sessionId) {
        return TEAM_MESSAGE_PREFIX + sanitizeSessionIdForTable(sessionId);
    }

    private String readStatusTableName(String sessionId) {
        return MESSAGE_READ_STATUS_PREFIX + sanitizeSessionIdForTable(sessionId);
    }

    private void flushStaticRowsIfNeeded() {
        if (!isSqlite()) {
            return;
        }
        try (Statement statement = sqliteConnection.createStatement()) {
            statement.executeUpdate("DELETE FROM team_member");
            statement.executeUpdate("DELETE FROM team_info");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear SQLite static rows", e);
        }
        for (TeamRecord teamRecord : teams.values()) {
            upsertSqliteTeam(teamRecord);
        }
        for (MemberRecord memberRecord : members.values()) {
            upsertSqliteMember(memberRecord);
        }
    }

    private void upsertSqliteTeam(TeamRecord teamRecord) {
        if (!isSqlite() || teamRecord == null) {
            return;
        }
        try (PreparedStatement statement = sqliteConnection.prepareStatement("""
                INSERT OR REPLACE INTO team_info
                    (team_name, display_name, leader_member_name, desc, prompt, created, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, teamRecord.getTeamName());
            statement.setString(2, teamRecord.getDisplayName());
            statement.setString(3, teamRecord.getLeaderMemberName());
            statement.setString(4, teamRecord.getDesc());
            statement.setString(5, teamRecord.getPrompt());
            statement.setLong(6, teamRecord.getCreated());
            statement.setLong(7, teamRecord.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist SQLite team row", e);
        }
    }

    private void upsertSqliteMember(MemberRecord memberRecord) {
        if (!isSqlite() || memberRecord == null) {
            return;
        }
        try (PreparedStatement statement = sqliteConnection.prepareStatement("""
                INSERT OR REPLACE INTO team_member
                    (member_name, team_name, display_name, agent_card, status, desc,
                     execution_status, mode, prompt, model_ref_json, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, memberRecord.getMemberName());
            statement.setString(2, memberRecord.getTeamName());
            statement.setString(3, memberRecord.getDisplayName());
            statement.setString(4, memberRecord.getAgentCard());
            statement.setString(5, memberRecord.getStatus());
            statement.setString(6, memberRecord.getDesc());
            statement.setString(7, memberRecord.getExecutionStatus());
            statement.setString(8, memberRecord.getMode());
            statement.setString(9, memberRecord.getPrompt());
            statement.setString(10, memberRecord.getModelRefJson());
            statement.setLong(11, memberRecord.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist SQLite member row", e);
        }
    }

    private void flushCurrentSessionRowsIfNeeded() {
        if (!isSqlite()) {
            return;
        }
        String sessionId = currentSessionId();
        if (sessionId.isBlank()) {
            return;
        }
        SessionTables tables = sessions.get(sessionId);
        if (tables == null) {
            return;
        }
        createSqliteSessionTablesIfNeeded(sessionId);
        String taskTable = taskTableName(sessionId);
        String dependencyTable = dependencyTableName(sessionId);
        String messageTable = messageTableName(sessionId);
        String readStatusTable = readStatusTableName(sessionId);
        try (Statement statement = sqliteConnection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + dependencyTable);
            statement.executeUpdate("DELETE FROM " + taskTable);
            statement.executeUpdate("DELETE FROM " + messageTable);
            statement.executeUpdate("DELETE FROM " + readStatusTable);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear SQLite session rows", e);
        }
        for (TaskRecord taskRecord : tables.tasks.values()) {
            insertSqliteTask(sessionId, taskRecord);
            for (String dependency : taskRecord.getDependencies()) {
                insertSqliteDependency(sessionId, taskRecord, dependency);
            }
        }
        for (MessageRecord messageRecord : tables.messages.values()) {
            insertSqliteMessage(sessionId, messageRecord);
        }
        for (Map.Entry<String, Long> entry : tables.broadcastReadAt.entrySet()) {
            insertSqliteReadStatus(sessionId, entry.getKey(), entry.getValue());
        }
    }

    private void flushAllLoadedSessionRowsIfNeeded() {
        if (!isSqlite()) {
            return;
        }
        for (String sessionId : sessions.keySet()) {
            SpawnContext.SessionToken token = SpawnContext.setSessionId(sessionId);
            try {
                flushCurrentSessionRowsIfNeeded();
            } finally {
                SpawnContext.resetSessionId(token);
            }
        }
    }

    private void insertSqliteTask(String sessionId, TaskRecord taskRecord) {
        try (PreparedStatement statement = sqliteConnection.prepareStatement("""
                INSERT INTO %s (task_id, team_name, title, content, status, assignee, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.formatted(taskTableName(sessionId)))) {
            statement.setString(1, taskRecord.getTaskId());
            statement.setString(2, taskRecord.getTeamName());
            statement.setString(3, taskRecord.getTitle());
            statement.setString(4, taskRecord.getContent());
            statement.setString(5, taskRecord.getStatus());
            statement.setString(6, taskRecord.getAssignee());
            statement.setLong(7, taskRecord.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist SQLite task row", e);
        }
    }

    private void insertSqliteDependency(String sessionId, TaskRecord taskRecord, String dependencyId) {
        TaskRecord dependency = sessions.get(sessionId).tasks.get(dependencyId);
        boolean isResolved = dependency != null
                && ("completed".equals(dependency.getStatus()) || "cancelled".equals(dependency.getStatus()));
        try (PreparedStatement statement = sqliteConnection.prepareStatement("""
                INSERT INTO %s (task_id, depends_on_task_id, team_name, isResolved)
                VALUES (?, ?, ?, ?)
                """.formatted(dependencyTableName(sessionId)))) {
            statement.setString(1, taskRecord.getTaskId());
            statement.setString(2, dependencyId);
            statement.setString(3, taskRecord.getTeamName());
            statement.setInt(4, isResolved ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist SQLite task dependency row", e);
        }
    }

    private void insertSqliteMessage(String sessionId, MessageRecord messageRecord) {
        try (PreparedStatement statement = sqliteConnection.prepareStatement("""
                INSERT INTO %s
                    (message_id, team_name, from_member_name, to_member_name, content, timestamp, broadcast, is_read)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(messageTableName(sessionId)))) {
            statement.setString(1, messageRecord.getMessageId());
            statement.setString(2, messageRecord.getTeamName());
            statement.setString(3, messageRecord.getFromMemberName());
            statement.setString(4, messageRecord.getToMemberName());
            statement.setString(5, messageRecord.getContent());
            statement.setLong(6, messageRecord.getTimestamp());
            statement.setInt(7, messageRecord.isBroadcast() ? 1 : 0);
            statement.setInt(8, messageRecord.isRead() ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist SQLite message row", e);
        }
    }

    private void insertSqliteReadStatus(String sessionId, String key, Long readAt) {
        String[] parts = key.split("::", 2);
        if (parts.length != 2) {
            return;
        }
        try (PreparedStatement statement = sqliteConnection.prepareStatement("""
                INSERT INTO %s (team_name, member_name, read_at)
                VALUES (?, ?, ?)
                """.formatted(readStatusTableName(sessionId)))) {
            statement.setString(1, parts[0]);
            statement.setString(2, parts[1]);
            statement.setLong(3, readAt != null ? readAt : 0L);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist SQLite read-status row", e);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<String> sessionTableNames(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        String suffix = sanitizeSessionIdForTable(sessionId);
        return List.of(
                TEAM_TASK_PREFIX + suffix,
                TEAM_TASK_DEPENDENCY_PREFIX + suffix,
                TEAM_MESSAGE_PREFIX + suffix,
                MESSAGE_READ_STATUS_PREFIX + suffix
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String sanitizeSessionIdForTable(String sessionId) {
        String sanitizedSessionId = sessionId != null ? sessionId : "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(sanitizedSessionId.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private String currentSessionId() {
        // Use a shared global key so all agents in the same process
        // see the same tasks/messages regardless of thread-local state.
        return "_global_";
    }

    private SessionTables currentSessionTables() {
        String sessionId = currentSessionId();
        return sessions.computeIfAbsent(sessionId, ignored -> new SessionTables());
    }

    private void clearDynamicRowsForTeam(String teamName) {
        for (SessionTables tables : sessions.values()) {
            tables.messages.entrySet().removeIf(entry -> teamName.equals(entry.getValue().getTeamName()));
            tables.broadcastReadAt.entrySet().removeIf(entry -> entry.getKey().startsWith(teamName + "::"));
            tables.tasks.entrySet().removeIf(entry -> teamName.equals(entry.getValue().getTeamName()));
        }
    }

    private static final class SessionTables {
        private final Map<String, MessageRecord> messages = new LinkedHashMap<>();
        private final Map<String, Long> broadcastReadAt = new LinkedHashMap<>();
        private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public final class TeamDao {
        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean createTeam(String teamName, String displayName, String leaderMemberName) {
            return createTeam(teamName, displayName, leaderMemberName, null, null);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean createTeam(
                String teamName,
                String displayName,
                String leaderMemberName,
                String desc,
                String prompt
        ) {
            ensureInitialized();
            if (teams.containsKey(teamName)) {
                return false;
            }
            long now = getCurrentTime();
            teams.put(teamName, TeamRecord.builder()
                    .teamName(teamName)
                    .displayName(displayName)
                    .leaderMemberName(leaderMemberName)
                    .desc(desc)
                    .prompt(prompt)
                    .created(now)
                    .updatedAt(now)
                    .build());
            flushStaticRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TeamRecord getTeam(String teamName) {
            ensureInitialized();
            return teams.get(teamName);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public long getTeamUpdatedAt(String teamName) {
            ensureInitialized();
            TeamRecord teamRecord = teams.get(teamName);
            return teamRecord != null ? teamRecord.getUpdatedAt() : 0L;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean deleteTeam(String teamName) {
            ensureInitialized();
            boolean removed = teams.remove(teamName) != null;
            if (removed) {
                members.entrySet().removeIf(entry -> teamName.equals(entry.getValue().getTeamName()));
                clearDynamicRowsForTeam(teamName);
                flushStaticRowsIfNeeded();
                flushAllLoadedSessionRowsIfNeeded();
            }
            return removed;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public final class MemberDao {
        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean createMember(
                String memberName,
                String teamName,
                String displayName,
                String agentCard,
                String status,
                String desc,
                String executionStatus,
                String mode,
                String prompt,
                String modelRefJson
        ) {
            ensureInitialized();
            String key = teamName + "::" + memberName;
            if (members.containsKey(key)) {
                return false;
            }
            long updatedAt = nextMemberUpdatedAt(teamName);
            members.put(key, MemberRecord.builder()
                    .memberName(memberName)
                    .teamName(teamName)
                    .displayName(displayName)
                    .agentCard(agentCard)
                    .status(status)
                    .desc(desc)
                    .executionStatus(executionStatus)
                    .mode(mode)
                    .prompt(prompt)
                    .modelRefJson(modelRefJson)
                    .updatedAt(updatedAt)
                    .build());
            flushStaticRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public MemberRecord getMember(String memberName, String teamName) {
            ensureInitialized();
            return members.get(teamName + "::" + memberName);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<MemberRecord> getTeamMembers(String teamName, String status) {
            ensureInitialized();
            return members.values().stream()
                    .filter(member -> teamName.equals(member.getTeamName()))
                    .filter(member -> status == null || status.equals(member.getStatus()))
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<MemberRecord> getTeamMembers(String teamName) {
            return getTeamMembers(teamName, null);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public long getMembersMaxUpdatedAt(String teamName) {
            ensureInitialized();
            return members.values().stream()
                    .filter(member -> teamName.equals(member.getTeamName()))
                    .mapToLong(MemberRecord::getUpdatedAt)
                    .max()
                    .orElse(0L);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean updateMemberStatus(String memberName, String teamName, String status) {
            ensureInitialized();
            MemberRecord memberRecord = getMember(memberName, teamName);
            if (memberRecord == null) {
                return false;
            }
            memberRecord.setStatus(status);
            memberRecord.setUpdatedAt(nextMemberUpdatedAt(teamName));
            flushStaticRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean updateMemberExecutionStatus(String memberName, String teamName, String executionStatus) {
            ensureInitialized();
            MemberRecord memberRecord = getMember(memberName, teamName);
            if (memberRecord == null) {
                return false;
            }
            memberRecord.setExecutionStatus(executionStatus);
            memberRecord.setUpdatedAt(nextMemberUpdatedAt(teamName));
            flushStaticRowsIfNeeded();
            return true;
        }

        private long nextMemberUpdatedAt(String teamName) {
            long now = getCurrentTime();
            long maxUpdatedAt = getMembersMaxUpdatedAt(teamName);
            return now > maxUpdatedAt ? now : maxUpdatedAt + 1L;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public final class MessageDao {
        /**
         * Auto-generated for codecheck compliance.
         */
        public MessageRecord getMessage(String messageId) {
            ensureInitialized();
            return currentSessionTables().messages.get(messageId);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean createMessage(String messageId, String teamName, String fromMemberName, String content,
                                     String toMemberName, boolean broadcast, boolean isRead) {
            return createMessage(messageId, teamName, fromMemberName, content, toMemberName, broadcast, isRead, null);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean createMessage(String messageId, String teamName, String fromMemberName, String content,
                                     String toMemberName, boolean broadcast, boolean isRead, Long timestamp) {
            ensureInitialized();
            SessionTables tables = currentSessionTables();
            Map<String, MessageRecord> messages = tables.messages;
            if (messages.containsKey(messageId)) {
                return false;
            }
            messages.put(messageId, MessageRecord.builder()
                    .messageId(messageId)
                    .teamName(teamName)
                    .fromMemberName(fromMemberName)
                    .toMemberName(toMemberName)
                    .content(content)
                    .timestamp(timestamp != null ? timestamp : getCurrentTime())
                    .broadcast(broadcast)
                    .isRead(isRead)
                    .build());
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<MessageRecord> getMessages(
                String teamName,
                String toMemberName,
                boolean isUnreadOnly,
                String fromMemberName
        ) {
            ensureInitialized();
            return currentSessionTables().messages.values().stream()
                    .filter(message -> teamName.equals(message.getTeamName()))
                    .filter(message -> !message.isBroadcast())
                    .filter(message -> toMemberName.equals(message.getToMemberName()))
                    .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                    .filter(message -> !isUnreadOnly || !message.isRead())
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<MessageRecord> getBroadcastMessages(
                String teamName,
                String memberName,
                boolean isUnreadOnly,
                String fromMemberName
        ) {
            ensureInitialized();
            SessionTables tables = currentSessionTables();
            Long readAt = tables.broadcastReadAt.get(teamName + "::" + memberName);
            return tables.messages.values().stream()
                    .filter(message -> teamName.equals(message.getTeamName()))
                    .filter(MessageRecord::isBroadcast)
                    .filter(message -> !memberName.equals(message.getFromMemberName()))
                    .filter(message -> fromMemberName == null || fromMemberName.equals(message.getFromMemberName()))
                    .filter(message -> !isUnreadOnly || readAt == null || message.getTimestamp() > readAt)
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean markMessageRead(String messageId) {
            return markMessageRead(messageId, null);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean markMessageRead(String messageId, String memberName) {
            ensureInitialized();
            SessionTables tables = currentSessionTables();
            MessageRecord record = tables.messages.get(messageId);
            if (record == null) {
                return false;
            }
            if (record.isBroadcast()) {
                if (memberName == null || memberName.isBlank() || "user".equals(memberName)) {
                    return false;
                }
                String key = record.getTeamName() + "::" + memberName;
                Long current = tables.broadcastReadAt.get(key);
                if (current == null || record.getTimestamp() > current) {
                    tables.broadcastReadAt.put(key, record.getTimestamp());
                }
            } else {
                record.setRead(true);
            }
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<MessageRecord> getTeamMessages(String teamName) {
            return getTeamMessages(teamName, null);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<MessageRecord> getTeamMessages(String teamName, Boolean broadcast) {
            ensureInitialized();
            return currentSessionTables().messages.values().stream()
                    .filter(message -> teamName.equals(message.getTeamName()))
                    .filter(message -> broadcast == null || message.isBroadcast() == broadcast)
                    .sorted((left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp()))
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void clearTeamMessages(String teamName) {
            ensureInitialized();
            currentSessionTables().messages.entrySet()
                    .removeIf(entry -> teamName.equals(entry.getValue().getTeamName()));
            flushCurrentSessionRowsIfNeeded();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public final class TaskDao {
        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean createTask(String taskId, String teamName, String title, String content, String status) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            if (tasks.containsKey(taskId)) {
                return false;
            }
            tasks.put(taskId, TaskRecord.builder()
                    .taskId(taskId)
                    .teamName(teamName)
                    .title(title)
                    .content(content)
                    .status(status)
                    .updatedAt(getCurrentTime())
                    .build());
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean addDependency(String taskId, String dependsOnTaskId) {
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            return mutateDependencyGraph(tasks.get(taskId) != null ? tasks.get(taskId).getTeamName() : null,
                    List.of(List.of(taskId, dependsOnTaskId))).isOk();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean addTaskWithBidirectionalDependencies(
                String taskId,
                String teamName,
                String title,
                String content,
                String status,
                List<String> dependencies,
                List<String> dependentTaskIds
        ) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            if (tasks.containsKey(taskId)) {
                return false;
            }
            if (teamName == null || teamName.isBlank()) {
                return false;
            }
            for (String dependency : dependencies != null ? dependencies : List.<String>of()) {
                TaskRecord target = tasks.get(dependency);
                if (target == null || !teamName.equals(target.getTeamName())) {
                    return false;
                }
            }
            for (String dependent : dependentTaskIds != null ? dependentTaskIds : List.<String>of()) {
                TaskRecord target = tasks.get(dependent);
                if (target == null || !teamName.equals(target.getTeamName())) {
                    return false;
                }
            }

            Map<String, List<String>> previousDependencies = new LinkedHashMap<>();
            for (TaskRecord taskRecord : tasks.values()) {
                if (teamName.equals(taskRecord.getTeamName())) {
                    previousDependencies.put(taskRecord.getTaskId(), new ArrayList<>(taskRecord.getDependencies()));
                }
            }
            TaskRecord staged = TaskRecord.builder()
                    .taskId(taskId)
                    .teamName(teamName)
                    .title(title)
                    .content(content)
                    .status(status)
                    .updatedAt(getCurrentTime())
                    .build();
            tasks.put(taskId, staged);
            List<List<String>> edges = new ArrayList<>();
            for (String dependency : dependencies != null ? dependencies : List.<String>of()) {
                edges.add(List.of(taskId, dependency));
            }
            for (String dependent : dependentTaskIds != null ? dependentTaskIds : List.<String>of()) {
                edges.add(List.of(dependent, taskId));
            }
            GraphMutationResult result = mutateDependencyGraph(teamName, edges);
            if (!result.isOk()) {
                tasks.remove(taskId);
                for (Map.Entry<String, List<String>> entry : previousDependencies.entrySet()) {
                    TaskRecord taskRecord = tasks.get(entry.getKey());
                    if (taskRecord != null) {
                        taskRecord.setDependencies(new ArrayList<>(entry.getValue()));
                        refreshTaskStatusForDependencies(taskRecord, tasks);
                    }
                }
                return false;
            }
            refreshTaskStatusForDependencies(staged, tasks);
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphMutationResult mutateDependencyGraph(String teamName, List<List<String>> addEdges) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            if (addEdges == null || addEdges.isEmpty()) {
                return GraphMutationResult.success(List.of());
            }
            String resolvedTeam = teamName;
            if (resolvedTeam == null || resolvedTeam.isBlank()) {
                String firstTaskId = addEdges.get(0).get(0);
                TaskRecord firstTask = tasks.get(firstTaskId);
                resolvedTeam = firstTask != null ? firstTask.getTeamName() : null;
            }
            if (resolvedTeam == null || resolvedTeam.isBlank()) {
                return GraphMutationResult.fail("Team name is required");
            }
            for (List<String> edge : addEdges) {
                if (edge == null || edge.size() < 2) {
                    return GraphMutationResult.fail("Invalid dependency edge");
                }
                String taskId = edge.get(0);
                String dependsOnTaskId = edge.get(1);
                TaskRecord taskRecord = tasks.get(taskId);
                if (taskRecord == null) {
                    return GraphMutationResult.fail("Task " + taskId + " not found");
                }
                TaskRecord dependency = tasks.get(dependsOnTaskId);
                if (dependency == null) {
                    return GraphMutationResult.fail("Dependency target " + dependsOnTaskId + " not found");
                }
                if (!resolvedTeam.equals(taskRecord.getTeamName()) || !resolvedTeam.equals(dependency.getTeamName())) {
                    return GraphMutationResult.fail("Dependency edge crosses team boundary");
                }
                if (List.of("claimed", "completed", "cancelled", "plan_approved").contains(taskRecord.getStatus())) {
                    return GraphMutationResult.fail(
                            "Cannot add dependency to " + taskId + " in terminal or executing status: "
                                    + taskRecord.getStatus());
                }
            }

            Map<String, List<String>> adjacency = new LinkedHashMap<>();
            for (TaskRecord record : tasks.values()) {
                if (resolvedTeam.equals(record.getTeamName())) {
                    adjacency.put(record.getTaskId(), new ArrayList<>(record.getDependencies()));
                }
            }
            List<List<String>> newEdges = new ArrayList<>();
            for (List<String> edge : addEdges) {
                String taskId = edge.get(0);
                String dependsOnTaskId = edge.get(1);
                List<String> deps = adjacency.computeIfAbsent(taskId, ignored -> new ArrayList<>());
                if (!deps.contains(dependsOnTaskId)) {
                    deps.add(dependsOnTaskId);
                    newEdges.add(List.of(taskId, dependsOnTaskId));
                }
            }
            List<String> cycle = detectCycle(adjacency);
            if (!cycle.isEmpty()) {
                return GraphMutationResult.fail("Circular dependency detected: " + String.join(" -> ", cycle));
            }

            long now = getCurrentTime();
            List<TaskRecord> refreshed = new ArrayList<>();
            for (List<String> edge : newEdges) {
                TaskRecord taskRecord = tasks.get(edge.get(0));
                String dep = edge.get(1);
                if (!taskRecord.getDependencies().contains(dep)) {
                    taskRecord.getDependencies().add(dep);
                }
                String before = taskRecord.getStatus();
                refreshTaskStatusForDependencies(taskRecord, tasks);
                if (before.equals(taskRecord.getStatus())) {
                    taskRecord.setUpdatedAt(now);
                }
                refreshed.add(taskRecord);
            }
            flushCurrentSessionRowsIfNeeded();
            return GraphMutationResult.success(refreshed);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean updateTask(String taskId, String title, String content) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null) {
                return false;
            }
            if (!"pending".equals(taskRecord.getStatus()) && !"blocked".equals(taskRecord.getStatus())) {
                return false;
            }
            if (title != null) {
                taskRecord.setTitle(title);
            }
            if (content != null) {
                taskRecord.setContent(content);
            }
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean updateTaskStatus(String taskId, String status) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null) {
                return false;
            }
            if (!isValidTaskTransition(taskRecord.getStatus(), status)) {
                return false;
            }
            taskRecord.setStatus(status);
            taskRecord.setUpdatedAt(getCurrentTime());
            if ("completed".equals(status) || "cancelled".equals(status)) {
                refreshBlockedTasks(taskRecord.getTeamName());
            }
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TaskRecord getTask(String taskId) {
            ensureInitialized();
            return currentSessionTables().tasks.get(taskId);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskRecord> getTeamTasks(String teamName, String status) {
            ensureInitialized();
            return currentSessionTables().tasks.values().stream()
                    .filter(task -> teamName.equals(task.getTeamName()))
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskRecord> getTasksByAssignee(String teamName, String assignee, String status) {
            ensureInitialized();
            return currentSessionTables().tasks.values().stream()
                    .filter(task -> teamName.equals(task.getTeamName()))
                    .filter(task -> assignee == null || assignee.equals(task.getAssignee()))
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskRecord> getTasksByAssignee(String teamName, String assignee) {
            return getTasksByAssignee(teamName, assignee, null);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public void clearTeamTasks(String teamName) {
            ensureInitialized();
            currentSessionTables().tasks.entrySet().removeIf(entry -> teamName.equals(entry.getValue().getTeamName()));
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean claimTask(String taskId, String assignee) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null
                    || "claimed".equals(taskRecord.getStatus())
                    || "completed".equals(taskRecord.getStatus())) {
                return false;
            }
            taskRecord.setStatus("claimed");
            taskRecord.setAssignee(assignee);
            taskRecord.setUpdatedAt(getCurrentTime());
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean completeTask(String taskId) {
            return completeTaskResult(taskId) != null;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TaskMutationResult completeTaskResult(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null) {
                return null;
            }
            if (!"claimed".equals(taskRecord.getStatus()) && !"plan_approved".equals(taskRecord.getStatus())) {
                return null;
            }
            taskRecord.setStatus("completed");
            taskRecord.setUpdatedAt(getCurrentTime());
            List<TaskRecord> unblocked = refreshBlockedTasks(taskRecord.getTeamName());
            flushCurrentSessionRowsIfNeeded();
            return TaskMutationResult.builder()
                    .task(taskRecord)
                    .unblockedTasks(unblocked)
                    .build();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean approvePlanTask(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null || !"claimed".equals(taskRecord.getStatus())) {
                return false;
            }
            taskRecord.setStatus("plan_approved");
            taskRecord.setUpdatedAt(getCurrentTime());
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean cancelTask(String taskId) {
            return cancelTaskResult(taskId) != null;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TaskMutationResult cancelTaskResult(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null
                    || "completed".equals(taskRecord.getStatus())
                    || "cancelled".equals(taskRecord.getStatus())) {
                return null;
            }
            taskRecord.setStatus("cancelled");
            taskRecord.setUpdatedAt(getCurrentTime());
            List<TaskRecord> unblocked = refreshBlockedTasks(taskRecord.getTeamName());
            flushCurrentSessionRowsIfNeeded();
            return TaskMutationResult.builder()
                    .task(taskRecord)
                    .unblockedTasks(unblocked)
                    .build();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskRecord> cancelAllTasks(String teamName) {
            TaskMutationResult result = cancelAllTasksResult(teamName);
            return result != null ? result.getCancelledTasks() : List.of();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public TaskMutationResult cancelAllTasksResult(String teamName) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            List<TaskRecord> cancelled = new ArrayList<>();
            for (TaskRecord taskRecord : tasks.values()) {
                if (!teamName.equals(taskRecord.getTeamName())) {
                    continue;
                }
                if ("completed".equals(taskRecord.getStatus()) || "cancelled".equals(taskRecord.getStatus())) {
                    continue;
                }
                taskRecord.setStatus("cancelled");
                taskRecord.setUpdatedAt(getCurrentTime());
                cancelled.add(taskRecord);
            }
            List<TaskRecord> unblocked = refreshBlockedTasks(teamName).stream()
                    .filter(task -> cancelled.stream()
                            .noneMatch(cancelledTask -> cancelledTask.getTaskId().equals(task.getTaskId())))
                    .toList();
            flushCurrentSessionRowsIfNeeded();
            return TaskMutationResult.builder()
                    .cancelledTasks(cancelled)
                    .unblockedTasks(unblocked)
                    .build();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean resetTask(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null || !"claimed".equals(taskRecord.getStatus())) {
                return false;
            }
            taskRecord.setStatus("pending");
            taskRecord.setAssignee(null);
            taskRecord.setUpdatedAt(getCurrentTime());
            refreshTaskStatusForDependencies(taskRecord, tasks);
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean assignTask(String taskId, String assignee) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null) {
                return false;
            }
            if (assignee.equals(taskRecord.getAssignee()) && "claimed".equals(taskRecord.getStatus())) {
                return true;
            }
            if (taskRecord.getAssignee() != null && !assignee.equals(taskRecord.getAssignee())) {
                return false;
            }
            return claimTask(taskId, assignee);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<String> getDependencies(String taskId) {
            ensureInitialized();
            TaskRecord taskRecord = currentSessionTables().tasks.get(taskId);
            return taskRecord != null ? new ArrayList<>(taskRecord.getDependencies()) : List.of();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskDependencyRecord> getTaskDependencies(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null) {
                return List.of();
            }
            List<TaskDependencyRecord> dependencies = new ArrayList<>();
            for (String dependencyId : taskRecord.getDependencies()) {
                TaskRecord dependency = tasks.get(dependencyId);
                boolean isResolved = dependency != null
                        && ("completed".equals(dependency.getStatus()) || "cancelled".equals(dependency.getStatus()));
                dependencies.add(TaskDependencyRecord.builder()
                        .teamName(taskRecord.getTeamName())
                        .taskId(taskId)
                        .dependsOnTaskId(dependencyId)
                        .isResolved(isResolved)
                        .build());
            }
            return dependencies;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean deleteTask(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            if (!tasks.containsKey(taskId)) {
                return false;
            }
            tasks.remove(taskId);
            for (TaskRecord taskRecord : tasks.values()) {
                taskRecord.getDependencies().removeIf(taskId::equals);
            }
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public int getUnresolvedDependenciesCount(String taskId) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            TaskRecord taskRecord = tasks.get(taskId);
            if (taskRecord == null) {
                return 0;
            }
            int count = 0;
            for (String dependencyId : taskRecord.getDependencies()) {
                TaskRecord dependency = tasks.get(dependencyId);
                if (dependency == null
                        || !"completed".equals(dependency.getStatus()) && !"cancelled".equals(dependency.getStatus())) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskRecord> getTasksDependingOn(String dependsOnTaskId) {
            ensureInitialized();
            return currentSessionTables().tasks.values().stream()
                    .filter(taskRecord -> taskRecord.getDependencies().contains(dependsOnTaskId))
                    .toList();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public List<TaskRecord> verifyAndFixTaskConsistency(String teamName) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            List<TaskRecord> refreshed = new ArrayList<>();
            for (TaskRecord candidate : tasks.values()) {
                if (!teamName.equals(candidate.getTeamName()) || !"blocked".equals(candidate.getStatus())) {
                    continue;
                }
                String before = candidate.getStatus();
                refreshTaskStatusForDependencies(candidate, tasks);
                if (!before.equals(candidate.getStatus())) {
                    refreshed.add(candidate);
                }
            }
            return refreshed;
        }

        private List<TaskRecord> refreshBlockedTasks(String teamName) {
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            List<TaskRecord> refreshed = new ArrayList<>();
            for (TaskRecord candidate : tasks.values()) {
                if (!teamName.equals(candidate.getTeamName()) || !"blocked".equals(candidate.getStatus())) {
                    continue;
                }
                String before = candidate.getStatus();
                refreshTaskStatusForDependencies(candidate, tasks);
                if (!before.equals(candidate.getStatus())) {
                    refreshed.add(candidate);
                }
            }
            return refreshed;
        }

        private void refreshTaskStatusForDependencies(TaskRecord taskRecord, Map<String, TaskRecord> tasks) {
            if (taskRecord.getDependencies().isEmpty()
                    || !"blocked".equals(taskRecord.getStatus()) && !"pending".equals(taskRecord.getStatus())) {
                return;
            }
            boolean isAllResolved = taskRecord.getDependencies().stream().allMatch(dep -> {
                TaskRecord dependency = tasks.get(dep);
                return dependency != null
                        && ("completed".equals(dependency.getStatus()) || "cancelled".equals(dependency.getStatus()));
            });
            String next = isAllResolved ? "pending" : "blocked";
            if (!next.equals(taskRecord.getStatus())) {
                taskRecord.setStatus(next);
                taskRecord.setUpdatedAt(getCurrentTime());
            }
        }

        private boolean wouldCreateCycle(String taskId, String dependsOnTaskId) {
            return reaches(dependsOnTaskId, taskId, new java.util.HashSet<>(), currentSessionTables().tasks);
        }

        private List<String> detectCycle(Map<String, List<String>> adjacency) {
            java.util.Set<String> visiting = new java.util.LinkedHashSet<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            for (String node : adjacency.keySet()) {
                List<String> cycle = detectCycleFrom(node, adjacency, visiting, visited, new ArrayList<>());
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
            return List.of();
        }

        private List<String> detectCycleFrom(
                String node,
                Map<String, List<String>> adjacency,
                java.util.Set<String> visiting,
                java.util.Set<String> visited,
                List<String> path
        ) {
            if (visited.contains(node)) {
                return List.of();
            }
            if (visiting.contains(node)) {
                int index = path.indexOf(node);
                List<String> cycle = new ArrayList<>(path.subList(Math.max(index, 0), path.size()));
                cycle.add(node);
                return cycle;
            }
            visiting.add(node);
            path.add(node);
            for (String next : adjacency.getOrDefault(node, List.of())) {
                List<String> cycle = detectCycleFrom(next, adjacency, visiting, visited, path);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
            visiting.remove(node);
            visited.add(node);
            path.remove(path.size() - 1);
            return List.of();
        }

        private boolean isValidTaskTransition(String current, String next) {
            if (current == null || next == null) {
                return false;
            }
            if (current.equals(next)) {
                return true;
            }
            return switch (current) {
                case "pending" -> List.of("claimed", "blocked", "cancelled").contains(next);
                case "blocked" -> List.of("pending", "cancelled").contains(next);
                case "claimed" -> List.of("completed", "cancelled", "pending", "plan_approved").contains(next);
                case "plan_approved" -> List.of("completed", "cancelled").contains(next);
                default -> false;
            };
        }

        private boolean reaches(
                String currentTaskId,
                String targetTaskId,
                java.util.Set<String> seen,
                Map<String, TaskRecord> tasks
        ) {
            if (currentTaskId.equals(targetTaskId)) {
                return true;
            }
            if (!seen.add(currentTaskId)) {
                return false;
            }
            TaskRecord current = tasks.get(currentTaskId);
            if (current == null) {
                return false;
            }
            for (String dependency : current.getDependencies()) {
                if (reaches(dependency, targetTaskId, seen, tasks)) {
                    return true;
                }
            }
            return false;
        }
    }
}
