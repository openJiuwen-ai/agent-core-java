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
import java.util.concurrent.ConcurrentHashMap;

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

    // X.CON.05: shared mutable maps must be thread-safe — concurrent
    // spawn_member tool calls hit createMember + getTeamMembers in
    // parallel; a LinkedHashMap here can lose an entry or corrupt the
    // internal hash chain under concurrent put, after which
    // getTeamMembers(UNSTARTED) silently drops a member and startup()
    // never auto-launches it (member stuck UNSTARTED, task never runs).
    private final Map<String, TeamRecord> teams = new ConcurrentHashMap<>();
    private final Map<String, MemberRecord> members = new ConcurrentHashMap<>();
    private final Map<String, SessionTables> sessions = new ConcurrentHashMap<>();
    private final Set<String> droppedSessionIds = new HashSet<>();
    private Connection sqliteConnection;
    private boolean isInitialized;

    /** DAO for team record operations (create, query, update, delete). */
    public final TeamDao team = new TeamDao();

    /** DAO for member record operations (register, query, update mode, remove). */
    public final MemberDao member = new MemberDao();

    /** DAO for message record operations (send, query, mark read). */
    public final MessageDao message = new MessageDao();

    /** DAO for task record operations (create, claim, complete, cancel, dependency graph). */
    public final TaskDao task = new TaskDao();

    /** Team-level session id pinned by TeamBackend at construction or via setTeamSessionId. */
    private String teamSessionId;

    /**
     * Constructs a TeamDatabase with the given configuration.
     *
     * @param config database configuration; defaults to a built-in config if null
     */
    public TeamDatabase(DatabaseConfig config) {
        this.config = config != null ? config : DatabaseConfig.builder().build();
    }

    /**
     * Initializes the database, creating SQLite tables and loading existing data.
     *
     * @throws IllegalStateException if SQLite initialization or row loading fails
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
     * Closes the database connection and clears all in-memory state.
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
     * Returns the database configuration.
     *
     * @return the active DatabaseConfig
     */
    public DatabaseConfig getConfig() {
        return config;
    }

    /**
     * Latch the team-level session id so all threads resolve the same session.
     *
     * <p>Mirrors the pinned {@code teamSessionId} field on {@link com.openjiuwen.agentteams.tools.TeamBackend}
     * and the message/task managers. Without this, {@link #currentSessionId()}
     * reads the thread-local {@link SpawnContext}, which diverges between the
     * leader's ReAct stream thread and teammate executor threads — splitting the
     * shared {@code TeamDatabase} instance into per-thread {@code SessionTables}
     * so tasks/messages posted by one thread are invisible to the other.</p>
     *
     * @param sessionId team-level session id; {@code null} or blank is ignored
     * @since 0.1.13
     */
    public void setTeamSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        this.teamSessionId = sessionId;
    }

    /**
     * Returns the current wall-clock time in milliseconds.
     *
     * @return current time in epoch milliseconds
     */
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }

    /**
     * Retrieves all messages for a team in the current session.
     *
     * @param teamName team identifier
     * @return list of message records for the team
     * @throws IllegalStateException if the database is not initialized
     */
    public List<MessageRecord> getTeamMessages(String teamName) {
        ensureInitialized();
        return message.getTeamMessages(teamName);
    }

    /**
     * Retrieves all tasks for a team in the current session.
     *
     * @param teamName team identifier
     * @return list of task records for the team
     */
    public List<TaskRecord> getTeamTasks(String teamName) {
        return getTeamTasks(teamName, null);
    }

    /**
     * Retrieves tasks for a team filtered by status in the current session.
     *
     * @param teamName team identifier
     * @param status task status filter; null returns all statuses
     * @return list of matching task records
     * @throws IllegalStateException if the database is not initialized
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
     * Normalizes the JDBC connection string based on the configured database type.
     *
     * @return normalized JDBC connection string
     * @throws IllegalArgumentException if the connection string is blank or has an invalid scheme
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
            // Long-lived SQLite connection — intentionally not in try-with-resources;
            // lifecycle managed by closeSqliteIfNeeded() at dispose time (G.PRM.07 exception).
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
     * Creates session-scoped tables for the current session.
     *
     * @return true if tables were created; false if no active session
     * @throws IllegalStateException if the database is not initialized
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
     * Drops session-scoped tables for the current session.
     *
     * @return list of dropped table names, or empty list if nothing was dropped
     * @throws IllegalStateException if the database is not initialized
     */
    public List<String> dropCurSessionTables() {
        ensureInitialized();
        String sessionId = currentSessionId();
        return dropSessionTablesById(sessionId);
    }

    /**
     * Drops session-scoped tables for the given session id.
     *
     * @param sessionId session identifier
     * @return list of dropped table names, or empty list if nothing was dropped
     * @throws IllegalStateException if the database is not initialized
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
     * Cleans up all runtime state including session tables, teams, and members.
     *
     * @return cleanup result with deleted and cleared table names
     * @throws IllegalStateException if the database is not initialized
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
     * Lists all active dynamic (session-scoped) table names.
     *
     * @return list of active dynamic table names
     * @throws IllegalStateException if the database is not initialized
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
        }

        // Insert dependencies after all task rows so FK targets exist
        // regardless of HashMap iteration order.
        for (TaskRecord taskRecord : tables.tasks.values()) {
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
     * Returns the dynamic table names for the given session.
     *
     * @param sessionId session identifier
     * @return list of table names for the session, or empty list if sessionId is blank
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
     * Sanitizes a session id into a table-safe suffix using SHA-256.
     *
     * @param sessionId session identifier to sanitize
     * @return hex-encoded 8-byte hash suffix
     * @throws IllegalStateException if SHA-256 digest is unavailable
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
        // Prefer the pinned team session id so leader and teammates agree on
        // the same SessionTables even when their executor threads carry
        // different SpawnContext thread-locals. Fall back to SpawnContext
        // for non-team callers (tests, single-session flows) that drive
        // session scope via thread-local — matching Python's get_session_id().
        if (teamSessionId != null && !teamSessionId.isBlank()) {
            return teamSessionId;
        }
        String sessionId = SpawnContext.getSessionId();
        return sessionId.isBlank() ? "_global_" : sessionId;
    }

    private SessionTables currentSessionTables() {
        String sessionId = currentSessionId();
        if (sessionId.isBlank()) {
            throw new IllegalStateException("Session tables are not created: no session id in context");
        }
        if (droppedSessionIds.contains(sessionId)) {
            throw new IllegalStateException("Session tables are not created for session: " + sessionId);
        }
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
        // X.CON.05: same concurrent-access reason as the outer maps.
        private final Map<String, MessageRecord> messages = new ConcurrentHashMap<>();
        private final Map<String, Long> broadcastReadAt = new ConcurrentHashMap<>();
        private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    }

    /**
     * Data access object for team records.
     */
    public final class TeamDao {
        /**
         * Creates a team with the basic required fields.
         *
         * @param teamName team identifier
         * @param displayName display name for the team
         * @param leaderMemberName name of the leader member
         * @return true if the team was created; false if it already exists
         */
        public boolean createTeam(String teamName, String displayName, String leaderMemberName) {
            return createTeam(teamName, displayName, leaderMemberName, null, null);
        }

        /**
         * Creates a team with all fields including description and prompt.
         *
         * @param teamName team identifier
         * @param displayName display name for the team
         * @param leaderMemberName name of the leader member
         * @param desc team description
         * @param prompt team prompt
         * @return true if the team was created; false if it already exists
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves a team record by name.
         *
         * @param teamName team identifier
         * @return the team record, or null if not found
         * @throws IllegalStateException if the database is not initialized
         */
        public TeamRecord getTeam(String teamName) {
            ensureInitialized();
            return teams.get(teamName);
        }

        /**
         * Returns the last-updated timestamp for a team.
         *
         * @param teamName team identifier
         * @return updated-at timestamp, or 0 if the team is not found
         * @throws IllegalStateException if the database is not initialized
         */
        public long getTeamUpdatedAt(String teamName) {
            ensureInitialized();
            TeamRecord teamRecord = teams.get(teamName);
            return teamRecord != null ? teamRecord.getUpdatedAt() : 0L;
        }

        /**
         * Deletes a team and all its associated members and dynamic data.
         *
         * @param teamName team identifier
         * @return true if the team was removed; false if not found
         * @throws IllegalStateException if the database is not initialized
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
     * Parameters for creating a task with bidirectional dependencies.
     *
     * @since 0.1.15
     */
    public static final class TaskDependencyParams {
        final String taskId;
        final String teamName;
        final String title;
        final String content;
        final String status;
        final List<String> dependencies;
        final List<String> dependentTaskIds;

        private TaskDependencyParams(Builder builder) {
            this.taskId = builder.taskId;
            this.teamName = builder.teamName;
            this.title = builder.title;
            this.content = builder.content;
            this.status = builder.status;
            this.dependencies = builder.dependencies;
            this.dependentTaskIds = builder.dependentTaskIds;
        }

        /**
         * Create a new builder.
         *
         * @return a new Builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for TaskDependencyParams.
         */
        public static final class Builder {
            private String taskId;
            private String teamName;
            private String title;
            private String content;
            private String status;
            private List<String> dependencies;
            private List<String> dependentTaskIds;

            /**
             * Set task id.
             *
             * @param val the task id
             * @return this builder
             */
            public Builder taskId(String val) {
                this.taskId = val;
                return this;
            }

            /**
             * Set team name.
             *
             * @param val the team name
             * @return this builder
             */
            public Builder teamName(String val) {
                this.teamName = val;
                return this;
            }

            /**
             * Set title.
             *
             * @param val the title
             * @return this builder
             */
            public Builder title(String val) {
                this.title = val;
                return this;
            }

            /**
             * Set content.
             *
             * @param val the content
             * @return this builder
             */
            public Builder content(String val) {
                this.content = val;
                return this;
            }

            /**
             * Set status.
             *
             * @param val the status
             * @return this builder
             */
            public Builder status(String val) {
                this.status = val;
                return this;
            }

            /**
             * Set dependencies.
             *
             * @param val the dependencies
             * @return this builder
             */
            public Builder dependencies(List<String> val) {
                this.dependencies = val;
                return this;
            }

            /**
             * Set dependent task ids.
             *
             * @param val the dependent task ids
             * @return this builder
             */
            public Builder dependentTaskIds(List<String> val) {
                this.dependentTaskIds = val;
                return this;
            }

            /**
             * Build the params.
             *
             * @return the constructed TaskDependencyParams
             */
            public TaskDependencyParams build() {
                return new TaskDependencyParams(this);
            }
        }
    }

    /**
     * Parameters for creating a member record.
     *
     * @since 0.1.15
     */
    public static final class MemberCreateParams {
        final String memberName;
        final String teamName;
        final String displayName;
        final String agentCard;
        final String status;
        final String desc;
        final String executionStatus;
        final String mode;
        final String prompt;
        final String modelRefJson;
        final String role;

        private MemberCreateParams(Builder builder) {
            this.memberName = builder.memberName;
            this.teamName = builder.teamName;
            this.displayName = builder.displayName;
            this.agentCard = builder.agentCard;
            this.status = builder.status;
            this.desc = builder.desc;
            this.executionStatus = builder.executionStatus;
            this.mode = builder.mode;
            this.prompt = builder.prompt;
            this.modelRefJson = builder.modelRefJson;
            this.role = builder.role;
        }

        /**
         * Create a new builder.
         *
         * @return a new Builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for MemberCreateParams.
         */
        public static final class Builder {
            private String memberName;
            private String teamName;
            private String displayName;
            private String agentCard;
            private String status;
            private String desc;
            private String executionStatus;
            private String mode;
            private String prompt;
            private String modelRefJson;
            private String role;

            /**
             * Set member name.
             *
             * @param val the member name
             * @return this builder
             */
            public Builder memberName(String val) {
                this.memberName = val;
                return this;
            }

            /**
             * Set team name.
             *
             * @param val the team name
             * @return this builder
             */
            public Builder teamName(String val) {
                this.teamName = val;
                return this;
            }

            /**
             * Set display name.
             *
             * @param val the display name
             * @return this builder
             */
            public Builder displayName(String val) {
                this.displayName = val;
                return this;
            }

            /**
             * Set agent card JSON.
             *
             * @param val the agent card JSON
             * @return this builder
             */
            public Builder agentCard(String val) {
                this.agentCard = val;
                return this;
            }

            /**
             * Set status.
             *
             * @param val the status
             * @return this builder
             */
            public Builder status(String val) {
                this.status = val;
                return this;
            }

            /**
             * Set description.
             *
             * @param val the description
             * @return this builder
             */
            public Builder desc(String val) {
                this.desc = val;
                return this;
            }

            /**
             * Set execution status.
             *
             * @param val the execution status
             * @return this builder
             */
            public Builder executionStatus(String val) {
                this.executionStatus = val;
                return this;
            }

            /**
             * Set mode.
             *
             * @param val the mode
             * @return this builder
             */
            public Builder mode(String val) {
                this.mode = val;
                return this;
            }

            /**
             * Set prompt.
             *
             * @param val the prompt
             * @return this builder
             */
            public Builder prompt(String val) {
                this.prompt = val;
                return this;
            }

            /**
             * Set model ref JSON.
             *
             * @param val the model ref JSON
             * @return this builder
             */
            public Builder modelRefJson(String val) {
                this.modelRefJson = val;
                return this;
            }

            /**
             * Set role.
             *
             * @param val the role
             * @return this builder
             */
            public Builder role(String val) {
                this.role = val;
                return this;
            }

            /**
             * Build the params.
             *
             * @return the constructed MemberCreateParams
             */
            public MemberCreateParams build() {
                return new MemberCreateParams(this);
            }
        }
    }

    /**
     * Data access object for member records.
     */
    public final class MemberDao {
        /**
         * Create a member row from a parameter object.
         *
         * @param params member creation parameters
         * @return true if the member was created; false if it already exists
         * @throws IllegalStateException if the database is not initialized
         */
        public boolean createMember(MemberCreateParams params) {
            ensureInitialized();
            String key = params.teamName + "::" + params.memberName;
            if (members.containsKey(key)) {
                return false;
            }
            long updatedAt = nextMemberUpdatedAt(params.teamName);
            members.put(key, MemberRecord.builder()
                    .memberName(params.memberName)
                    .teamName(params.teamName)
                    .displayName(params.displayName)
                    .agentCard(params.agentCard)
                    .status(params.status)
                    .desc(params.desc)
                    .executionStatus(params.executionStatus)
                    .mode(params.mode)
                    .prompt(params.prompt)
                    .modelRefJson(params.modelRefJson)
                    .role(params.role)
                    .updatedAt(updatedAt)
                    .build());
            flushStaticRowsIfNeeded();
            return true;
        }

        /**
         * Retrieves a member record by name and team.
         *
         * @param memberName member identifier
         * @param teamName team the member belongs to
         * @return the member record, or null if not found
         * @throws IllegalStateException if the database is not initialized
         */
        public MemberRecord getMember(String memberName, String teamName) {
            ensureInitialized();
            return members.get(teamName + "::" + memberName);
        }

        /**
         * Lists members of a team, optionally filtered by status.
         *
         * @param teamName team identifier
         * @param status member status filter; null returns all statuses
         * @return list of matching member records
         * @throws IllegalStateException if the database is not initialized
         */
        public List<MemberRecord> getTeamMembers(String teamName, String status) {
            ensureInitialized();
            return members.values().stream()
                    .filter(member -> teamName.equals(member.getTeamName()))
                    .filter(member -> status == null || status.equals(member.getStatus()))
                    .toList();
        }

        /**
         * Lists all members of a team regardless of status.
         *
         * @param teamName team identifier
         * @return list of all member records for the team
         */
        public List<MemberRecord> getTeamMembers(String teamName) {
            return getTeamMembers(teamName, null);
        }

        /**
         * Returns the maximum updated-at timestamp among all members of a team.
         *
         * @param teamName team identifier
         * @return maximum updated-at timestamp, or 0 if no members exist
         * @throws IllegalStateException if the database is not initialized
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
         * Updates the status of a member.
         *
         * @param memberName member identifier
         * @param teamName team the member belongs to
         * @param status new status value
         * @return true if the member was updated; false if not found
         * @throws IllegalStateException if the database is not initialized
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
         * Updates the execution status of a member.
         *
         * @param memberName member identifier
         * @param teamName team the member belongs to
         * @param executionStatus new execution status value
         * @return true if the member was updated; false if not found
         * @throws IllegalStateException if the database is not initialized
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

        /**
         * Atomic compare-and-swap status transition.
         *
         * <p>Mirrors Python {@code member_dao.try_transition_member_status}.
         * Succeeds only when the row's current status equals
         * {@code fromStatus}; the row is then flipped to {@code toStatus}.
         * Returns {@code false} when the member is missing or another
         * caller already moved it off {@code fromStatus} -- the caller
         * treats that as "a concurrent path owns the transition" and backs
         * off. Used by {@code TeamBackend.startupMember} as the
         * UNSTARTED&rarr;STARTING spawn CAS guard.</p>
         *
         * @param memberName member identifier
         * @param teamName team the member belongs to
         * @param fromStatus expected current status
         * @param toStatus target status to transition to
         * @return true if the transition succeeded; false if the member is missing or status mismatch
         * @throws IllegalStateException if the database is not initialized
         */
        public boolean tryTransitionMemberStatus(
                String memberName, String teamName, String fromStatus, String toStatus) {
            ensureInitialized();
            MemberRecord memberRecord = getMember(memberName, teamName);
            if (memberRecord == null) {
                return false;
            }
            if (fromStatus == null || !fromStatus.equals(memberRecord.getStatus())) {
                return false;
            }
            memberRecord.setStatus(toStatus);
            memberRecord.setUpdatedAt(nextMemberUpdatedAt(teamName));
            flushStaticRowsIfNeeded();
            return true;
        }

        /**
         * Probe {@code team_member.role} for a single member.
         *
         * <p>Mirrors Python {@code member_dao.is_human_agent}. Queries the
         * DB row on every call (no in-memory cache) so the answer is always
         * current regardless of when the member was spawned.</p>
         *
         * @param memberName member identifier
         * @param teamName team the member belongs to
         * @return true if the member's role is "human_agent"
         */
        public boolean isHumanAgent(String memberName, String teamName) {
            MemberRecord memberRecord = getMember(memberName, teamName);
            if (memberRecord == null || memberRecord.getRole() == null) {
                return false;
            }
            return "human_agent".equals(memberRecord.getRole());
        }

        /**
         * Snapshot of every human-agent member name in the team.
         *
         * <p>Mirrors Python {@code member_dao.list_human_agent_names}.
         * Probes the DB on every call so newly registered or shut-down
         * humans are reflected without a cache refresh.</p>
         *
         * @param teamName team identifier
         * @return list of member names whose role is "human_agent"
         * @throws IllegalStateException if the database is not initialized
         */
        public java.util.List<String> listHumanAgentNames(String teamName) {
            ensureInitialized();
            return members.values().stream()
                    .filter(member -> teamName.equals(member.getTeamName()))
                    .filter(member -> member.getRole() != null
                            && "human_agent".equals(member.getRole()))
                    .map(MemberRecord::getMemberName)
                    .toList();
        }

        private long nextMemberUpdatedAt(String teamName) {
            long now = getCurrentTime();
            long maxUpdatedAt = getMembersMaxUpdatedAt(teamName);
            return now > maxUpdatedAt ? now : maxUpdatedAt + 1L;
        }
    }

    /**
     * Data access object for message records.
     */
    public final class MessageDao {
        /**
         * Retrieves a message by its identifier.
         *
         * @param messageId message identifier
         * @return the message record, or null if not found
         * @throws IllegalStateException if the database is not initialized
         */
        public MessageRecord getMessage(String messageId) {
            ensureInitialized();
            return currentSessionTables().messages.get(messageId);
        }

        /**
         * Creates a message with auto-generated timestamp.
         *
         * @param messageId message identifier
         * @param teamName team identifier
         * @param fromMemberName sender member name
         * @param content message content
         * @param toMemberName recipient member name
         * @param broadcast whether the message is a broadcast
         * @param isRead whether the message is already read
         * @return true if the message was created; false if it already exists
         */
        public boolean createMessage(String messageId, String teamName, String fromMemberName, String content,
                                     String toMemberName, boolean broadcast, boolean isRead) {
            return createMessage(messageId, teamName, fromMemberName, content, toMemberName, broadcast, isRead, null);
        }

        /**
         * Creates a message with an explicit timestamp.
         *
         * @param messageId message identifier
         * @param teamName team identifier
         * @param fromMemberName sender member name
         * @param content message content
         * @param toMemberName recipient member name
         * @param broadcast whether the message is a broadcast
         * @param isRead whether the message is already read
         * @param timestamp explicit timestamp, or null to use current time
         * @return true if the message was created; false if it already exists
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves directed (non-broadcast) messages for a team member.
         *
         * @param teamName team identifier
         * @param toMemberName recipient member name
         * @param isUnreadOnly whether to filter for unread messages only
         * @param fromMemberName sender filter; null returns messages from all senders
         * @return list of matching message records
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves broadcast messages for a team member.
         *
         * @param teamName team identifier
         * @param memberName member name to retrieve broadcasts for
         * @param isUnreadOnly whether to filter for unread messages only
         * @param fromMemberName sender filter; null returns messages from all senders
         * @return list of matching broadcast message records
         * @throws IllegalStateException if the database is not initialized
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
         * Marks a directed message as read.
         *
         * @param messageId message identifier
         * @return true if the message was marked read; false if not found
         */
        public boolean markMessageRead(String messageId) {
            return markMessageRead(messageId, null);
        }

        /**
         * Marks a message as read, handling broadcast read-tracking for the given member.
         *
         * @param messageId message identifier
         * @param memberName member name for broadcast read-tracking; null or blank for directed messages
         * @return true if the message was marked read; false if not found or invalid member
         * @throws IllegalStateException if the database is not initialized
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
         * Batch mark a list of messages read for one member.
         *
         * <p>Mirrors Python {@code message_dao.mark_messages_read}. Single
         * transaction in Python (fsync once); the in-memory Map backing
         * has no fsync cost so the loop is equivalent. Returns the count
         * of messages successfully marked (missing message ids contribute
         * {@code false} and are not counted).</p>
         *
         * @param messageIds list of message identifiers to mark read
         * @param memberName member name for broadcast read-tracking
         * @return count of messages successfully marked read
         */
        public int markMessagesRead(List<String> messageIds, String memberName) {
            if (messageIds == null || messageIds.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (String messageId : messageIds) {
                if (markMessageRead(messageId, memberName)) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Retrieves all messages for a team regardless of broadcast flag.
         *
         * @param teamName team identifier
         * @return list of message records for the team sorted by timestamp
         */
        public List<MessageRecord> getTeamMessages(String teamName) {
            return getTeamMessages(teamName, null);
        }

        /**
         * Retrieves messages for a team, optionally filtered by broadcast flag.
         *
         * @param teamName team identifier
         * @param broadcast broadcast filter; null returns both broadcast and directed messages
         * @return list of matching message records sorted by timestamp
         * @throws IllegalStateException if the database is not initialized
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
         * Removes all messages for a team in the current session.
         *
         * @param teamName team identifier
         * @throws IllegalStateException if the database is not initialized
         */
        public void clearTeamMessages(String teamName) {
            ensureInitialized();
            currentSessionTables().messages.entrySet()
                    .removeIf(entry -> teamName.equals(entry.getValue().getTeamName()));
            flushCurrentSessionRowsIfNeeded();
        }
    }

    /**
     * Data access object for task records.
     */
    public final class TaskDao {
        /**
         * Creates a task in the current session.
         *
         * @param taskId task identifier
         * @param teamName team identifier
         * @param title task title
         * @param content task content
         * @param status initial task status
         * @return true if the task was created; false if it already exists
         * @throws IllegalStateException if the database is not initialized
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
         * Adds a single dependency edge between two tasks.
         *
         * @param taskId task that depends on another
         * @param dependsOnTaskId task being depended on
         * @return true if the dependency was added; false if the mutation failed
         */
        public boolean addDependency(String taskId, String dependsOnTaskId) {
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            return mutateDependencyGraph(tasks.get(taskId) != null ? tasks.get(taskId).getTeamName() : null,
                    List.of(List.of(taskId, dependsOnTaskId))).isOk();
        }

        /**
         * Creates a task with bidirectional dependency edges from a parameter object.
         *
         * @param params task dependency parameters
         * @return true if the task and dependencies were created; false on validation failure
         * @throws IllegalStateException if the database is not initialized
         */
        public boolean addTaskWithBidirectionalDependencies(TaskDependencyParams params) {
            ensureInitialized();
            Map<String, TaskRecord> tasks = currentSessionTables().tasks;
            if (tasks.containsKey(params.taskId)) {
                return false;
            }
            if (params.teamName == null || params.teamName.isBlank()) {
                return false;
            }
            if (!validateDependencyTeamMembership(tasks, params.teamName, params.dependencies)
                    || !validateDependencyTeamMembership(tasks, params.teamName, params.dependentTaskIds)) {
                return false;
            }
            return insertTaskWithDependencies(tasks, params);
        }

        /**
         * Validate that all task IDs in the list belong to the given team.
         *
         * @param tasks map of task records keyed by task id
         * @param teamName team name to match against each task's teamName
         * @param taskIds list of task ids to validate; null is treated as empty
         * @return true if every task id exists in the map and belongs to the team; true for null/empty list
         */
        private boolean validateDependencyTeamMembership(
                Map<String, TaskRecord> tasks, String teamName, List<String> taskIds) {
            for (String id : taskIds != null ? taskIds : List.<String>of()) {
                TaskRecord target = tasks.get(id);
                if (target == null || !teamName.equals(target.getTeamName())) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Insert a task record and apply bidirectional dependency edges,
         * rolling back on graph mutation failure.
         *
         * @param tasks map of task records keyed by task id
         * @param params task dependency parameters
         * @return true if the task and dependencies were inserted; false on graph mutation failure
         */
        private boolean insertTaskWithDependencies(
                Map<String, TaskRecord> tasks, TaskDependencyParams params) {
            Map<String, List<String>> previousDependencies = new LinkedHashMap<>();
            for (TaskRecord taskRecord : tasks.values()) {
                if (params.teamName.equals(taskRecord.getTeamName())) {
                    previousDependencies.put(taskRecord.getTaskId(),
                            new ArrayList<>(taskRecord.getDependencies()));
                }
            }
            TaskRecord staged = TaskRecord.builder()
                    .taskId(params.taskId)
                    .teamName(params.teamName)
                    .title(params.title)
                    .content(params.content)
                    .status(params.status)
                    .updatedAt(getCurrentTime())
                    .build();
            tasks.put(params.taskId, staged);
            List<List<String>> edges = new ArrayList<>();
            for (String dependency : params.dependencies != null
                    ? params.dependencies : List.<String>of()) {
                edges.add(List.of(params.taskId, dependency));
            }
            for (String dependent : params.dependentTaskIds != null
                    ? params.dependentTaskIds : List.<String>of()) {
                edges.add(List.of(dependent, params.taskId));
            }
            GraphMutationResult result = mutateDependencyGraph(params.teamName, edges);
            if (!result.isOk()) {
                rollbackTaskInsert(tasks, params.taskId, previousDependencies);
                return false;
            }
            refreshTaskStatusForDependencies(staged, tasks);
            flushCurrentSessionRowsIfNeeded();
            return true;
        }

        /**
         * Roll back a failed task insertion by removing the task and restoring
         * previous dependency lists.
         *
         * @param tasks map of task records keyed by task id
         * @param taskId the task id to remove
         * @param previousDependencies the dependency lists to restore for existing tasks
         */
        private void rollbackTaskInsert(
                Map<String, TaskRecord> tasks, String taskId,
                Map<String, List<String>> previousDependencies) {
            tasks.remove(taskId);
            for (Map.Entry<String, List<String>> entry : previousDependencies.entrySet()) {
                TaskRecord taskRecord = tasks.get(entry.getKey());
                if (taskRecord != null) {
                    taskRecord.setDependencies(new ArrayList<>(entry.getValue()));
                    refreshTaskStatusForDependencies(taskRecord, tasks);
                }
            }
        }

        /**
         * Mutates the dependency graph by adding edges, with cycle detection.
         *
         * @param teamName team identifier; null infers from the first edge
         * @param addEdges list of [taskId, dependsOnTaskId] pairs to add
         * @return mutation result indicating success or failure with a reason
         * @throws IllegalStateException if the database is not initialized
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
         * Updates the title and content of a pending or blocked task.
         *
         * @param taskId task identifier
         * @param title new title; null leaves unchanged
         * @param content new content; null leaves unchanged
         * @return true if the task was updated; false if not found or not in an editable status
         * @throws IllegalStateException if the database is not initialized
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
         * Transitions a task to a new status if the transition is valid.
         *
         * @param taskId task identifier
         * @param status target status
         * @return true if the status was updated; false if not found or invalid transition
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves a task by its identifier.
         *
         * @param taskId task identifier
         * @return the task record, or null if not found
         * @throws IllegalStateException if the database is not initialized
         */
        public TaskRecord getTask(String taskId) {
            ensureInitialized();
            return currentSessionTables().tasks.get(taskId);
        }

        /**
         * Retrieves tasks for a team, optionally filtered by status.
         *
         * @param teamName team identifier
         * @param status task status filter; null returns all statuses
         * @return list of matching task records
         * @throws IllegalStateException if the database is not initialized
         */
        public List<TaskRecord> getTeamTasks(String teamName, String status) {
            ensureInitialized();
            return currentSessionTables().tasks.values().stream()
                    .filter(task -> teamName.equals(task.getTeamName()))
                    .filter(task -> status == null || status.equals(task.getStatus()))
                    .toList();
        }

        /**
         * Retrieves tasks for a team filtered by assignee and optionally by status.
         *
         * @param teamName team identifier
         * @param assignee assignee filter; null returns tasks with any assignee
         * @param status task status filter; null returns all statuses
         * @return list of matching task records
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves tasks for a team filtered by assignee regardless of status.
         *
         * @param teamName team identifier
         * @param assignee assignee filter; null returns tasks with any assignee
         * @return list of matching task records
         */
        public List<TaskRecord> getTasksByAssignee(String teamName, String assignee) {
            return getTasksByAssignee(teamName, assignee, null);
        }

        /**
         * Claims a task for an assignee, transitioning it to "claimed" status.
         *
         * @param taskId task identifier
         * @param assignee member name claiming the task
         * @return true if the task was claimed; false if not found or already claimed/completed
         * @throws IllegalStateException if the database is not initialized
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
         * Completes a task, returning only success or failure.
         *
         * @param taskId task identifier
         * @return true if the task was completed; false if not found or not in a completable status
         */
        public boolean completeTask(String taskId) {
            return completeTaskResult(taskId) != null;
        }

        /**
         * Completes a task and returns the full mutation result including unblocked tasks.
         *
         * @param taskId task identifier
         * @return mutation result with the completed task and unblocked tasks, or null if not found or not completable
         * @throws IllegalStateException if the database is not initialized
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
         * Approves a claimed task, transitioning it to "plan_approved" status.
         *
         * @param taskId task identifier
         * @return true if the task was approved; false if not found or not in "claimed" status
         * @throws IllegalStateException if the database is not initialized
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
         * Cancels a task, returning only success or failure.
         *
         * @param taskId task identifier
         * @return true if the task was cancelled; false if not found or already completed/cancelled
         */
        public boolean cancelTask(String taskId) {
            return cancelTaskResult(taskId) != null;
        }

        /**
         * Cancels a task and returns the full mutation result including unblocked tasks.
         *
         * @param taskId task identifier
         * @return mutation result with the cancelled task and unblocked tasks, or null if not found or already terminal
         * @throws IllegalStateException if the database is not initialized
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
         * Cancels all non-terminal tasks for a team, returning only the cancelled list.
         *
         * @param teamName team identifier
         * @return list of cancelled task records, or empty list if none were cancellable
         */
        public List<TaskRecord> cancelAllTasks(String teamName) {
            TaskMutationResult result = cancelAllTasksResult(teamName);
            return result != null ? result.getCancelledTasks() : List.of();
        }

        /**
         * Cancels all non-terminal tasks for a team and returns the full mutation result.
         *
         * @param teamName team identifier
         * @return mutation result with cancelled and unblocked task lists
         * @throws IllegalStateException if the database is not initialized
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
         * Resets a claimed task back to "pending" status and clears its assignee.
         *
         * @param taskId task identifier
         * @return true if the task was reset; false if not found or not in "claimed" status
         * @throws IllegalStateException if the database is not initialized
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
         * Assigns a task to a member, claiming it if not already claimed.
         *
         * @param taskId task identifier
         * @param assignee member name to assign the task to
         * @return true if the task was assigned or already assigned to the same member; false if not assignable
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves the dependency list for a task.
         *
         * @param taskId task identifier
         * @return list of task ids this task depends on, or empty list if not found
         * @throws IllegalStateException if the database is not initialized
         */
        public List<String> getDependencies(String taskId) {
            ensureInitialized();
            TaskRecord taskRecord = currentSessionTables().tasks.get(taskId);
            return taskRecord != null ? new ArrayList<>(taskRecord.getDependencies()) : List.of();
        }

        /**
         * Retrieves detailed dependency records for a task, including resolved status.
         *
         * @param taskId task identifier
         * @return list of task dependency records, or empty list if the task is not found
         * @throws IllegalStateException if the database is not initialized
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
         * Deletes a task and removes it from all dependency lists.
         *
         * @param taskId task identifier
         * @return true if the task was deleted; false if not found
         * @throws IllegalStateException if the database is not initialized
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
         * Returns the count of unresolved dependencies for a task.
         *
         * @param taskId task identifier
         * @return number of unresolved dependencies, or 0 if the task is not found
         * @throws IllegalStateException if the database is not initialized
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
         * Retrieves all tasks that depend on a given task.
         *
         * @param dependsOnTaskId the task id being depended on
         * @return list of task records that list the given task as a dependency
         * @throws IllegalStateException if the database is not initialized
         */
        public List<TaskRecord> getTasksDependingOn(String dependsOnTaskId) {
            ensureInitialized();
            return currentSessionTables().tasks.values().stream()
                    .filter(taskRecord -> taskRecord.getDependencies().contains(dependsOnTaskId))
                    .toList();
        }

        /**
         * Verifies and fixes task consistency for blocked tasks whose dependencies are all resolved.
         *
         * @param teamName team identifier
         * @return list of task records whose status was refreshed
         * @throws IllegalStateException if the database is not initialized
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

        /**
         * Re-evaluate the status of a task whose dependencies may have changed.
         * If the task is blocked or pending and all its dependencies are completed
         * or cancelled, transitions it to pending; otherwise remains blocked.
         *
         * @param taskRecord the task record to re-evaluate
         * @param tasks map of task records keyed by task id, used to look up dependencies
         */
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
