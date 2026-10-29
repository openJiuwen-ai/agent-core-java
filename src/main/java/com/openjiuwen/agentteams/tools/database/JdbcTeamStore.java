/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * JDBC persistence adapter shared by PostgreSQL and MySQL team databases.
 *
 * @since 0.1.15
 */
final class JdbcTeamStore implements AutoCloseable {
    private static final Pattern SQL_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final String TEAM_TASK_PREFIX = "team_task_";
    private static final String TEAM_TASK_DEPENDENCY_PREFIX = "team_task_dependency_";
    private static final String TEAM_MESSAGE_PREFIX = "team_message_";
    private static final String MESSAGE_READ_STATUS_PREFIX = "message_read_status_";

    private final DatabaseType databaseType;
    private final Connection connection;
    private final Object transactionLock = new Object();

    private JdbcTeamStore(DatabaseType databaseType, Connection connection) {
        this.databaseType = Objects.requireNonNull(databaseType, "databaseType");
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    static JdbcTeamStore open(DatabaseConfig config) {
        JdbcConnectionSpec connectionSpec = JdbcConnectionSpec.from(config);
        Connection openedConnection = null;
        try {
            openedConnection = connectionSpec.openConnection();
            JdbcTeamStore store = new JdbcTeamStore(config.getDbType(), openedConnection);
            store.createStaticTables();
            return store;
        } catch (SQLException exception) {
            closeAfterFailedInitialization(openedConnection, exception);
            throw new IllegalStateException("Failed to initialize JDBC team database", exception);
        } catch (IllegalStateException exception) {
            closeAfterFailedInitialization(openedConnection, exception);
            throw exception;
        }
    }

    static JdbcTeamStore forConnection(DatabaseType databaseType, Connection connection) {
        JdbcTeamStore store = new JdbcTeamStore(databaseType, connection);
        try {
            store.createStaticTables();
            return store;
        } catch (IllegalStateException exception) {
            closeAfterFailedInitialization(connection, exception);
            throw exception;
        }
    }

    void loadStaticRows(Map<String, TeamRecord> teams, Map<String, MemberRecord> members) {
        synchronized (transactionLock) {
            teams.clear();
            members.clear();
            try {
                loadTeams(teams);
                loadMembers(members);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load JDBC team database rows", exception);
            }
        }
    }

    void replaceStaticRows(Collection<TeamRecord> teams, Collection<MemberRecord> members) {
        try {
            runInTransaction(() -> {
                upsertTeams(teams);
                upsertMembers(members);
                deleteMissingMembers(members);
                deleteMissingTeams(teams);
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to persist JDBC team database rows", exception);
        }
    }

    void createSessionTables(String sessionId) {
        String taskTable = taskTableName(sessionId);
        String dependencyTable = dependencyTableName(sessionId);
        String messageTable = messageTableName(sessionId);
        String readStatusTable = readStatusTableName(sessionId);
        synchronized (transactionLock) {
            try (Statement statement = connection.createStatement()) {
                createTaskTable(statement, taskTable);
                createDependencyTable(statement, dependencyTable, taskTable);
                createMessageTable(statement, messageTable);
                createReadStatusTable(statement, readStatusTable);
                ensureTaskColumns(statement, taskTable);
                ensureDependencyResolvedColumn(statement, dependencyTable);
                ensureMessageColumns(statement, messageTable);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to create JDBC session tables", exception);
            }
        }
    }

    void loadSessionRows(
            String sessionId,
            Map<String, TaskRecord> tasks,
            Map<String, MessageRecord> messages,
            Map<String, Long> readStatuses) {
        createSessionTables(sessionId);
        synchronized (transactionLock) {
            tasks.clear();
            messages.clear();
            readStatuses.clear();
            try {
                loadTasks(sessionId, tasks);
                loadDependencies(sessionId, tasks);
                loadMessages(sessionId, messages);
                loadReadStatuses(sessionId, readStatuses);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load JDBC session rows", exception);
            }
        }
    }

    void replaceSessionRows(
            String sessionId,
            Collection<TaskRecord> tasks,
            Collection<MessageRecord> messages,
            Map<String, Long> readStatuses) {
        createSessionTables(sessionId);
        try {
            runInTransaction(() -> {
                clearSessionRows(sessionId);
                insertTasks(sessionId, tasks);
                insertDependencies(sessionId, tasks);
                insertMessages(sessionId, messages);
                insertReadStatuses(sessionId, readStatuses);
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to persist JDBC session rows", exception);
        }
    }

    boolean dropSessionTables(List<String> tableNames) {
        List<String> orderedNames = tableNames.stream()
                .map(JdbcTeamStore::safeTableName)
                .sorted(Comparator.comparingInt(JdbcTeamStore::dropOrder))
                .toList();
        synchronized (transactionLock) {
            try {
                boolean hasExistingTable = hasExistingTable(orderedNames);
                dropTables(orderedNames);
                return hasExistingTable;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to drop JDBC session tables", exception);
            }
        }
    }

    List<String> cleanupAllRuntimeState() {
        synchronized (transactionLock) {
            try {
                List<String> dynamicTables = listDynamicTables();
                dropTables(dynamicTables.stream()
                        .sorted(Comparator.comparingInt(JdbcTeamStore::dropOrder))
                        .toList());
                runInTransaction(this::clearStaticRows);
                return dynamicTables;
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to cleanup JDBC runtime state", exception);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        synchronized (transactionLock) {
            try {
                connection.close();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to close JDBC team database", exception);
            }
        }
    }

    private void createStaticTables() {
        synchronized (transactionLock) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(teamTableSql());
                statement.executeUpdate(memberTableSql());
                ensureTeamCapabilityColumns(statement);
                ensureMemberRoleColumn(statement);
                ensureMemberOptionsColumn(statement);
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to create JDBC team tables", exception);
            }
        }
    }

    private String teamTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS team_info (
                    team_name VARCHAR(255) PRIMARY KEY,
                    display_name VARCHAR(1024),
                    leader_member_name VARCHAR(255),
                    %s TEXT,
                    prompt TEXT,
                    dispatch_mode VARCHAR(64) NOT NULL DEFAULT 'autonomous',
                    enable_task_verification BOOLEAN NOT NULL DEFAULT FALSE,
                    created BIGINT,
                    updated_at BIGINT
                )
                """.formatted(quoteIdentifier("desc"));
    }

    private String memberTableSql() {
        return """
                CREATE TABLE IF NOT EXISTS team_member (
                    member_name VARCHAR(255),
                    team_name VARCHAR(255),
                    display_name VARCHAR(1024),
                    agent_card TEXT,
                    status VARCHAR(64),
                    %s TEXT,
                    execution_status VARCHAR(64),
                    mode VARCHAR(64),
                    prompt TEXT,
                    options TEXT,
                    updated_at BIGINT,
                    role VARCHAR(64) NOT NULL DEFAULT 'teammate',
                    PRIMARY KEY (member_name, team_name),
                    FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                )
                """.formatted(quoteIdentifier("desc"));
    }

    private void ensureMemberRoleColumn(Statement statement) throws SQLException {
        if (hasColumn("team_member", "role")) {
            return;
        }
        try {
            statement.executeUpdate(
                    "ALTER TABLE team_member ADD COLUMN role VARCHAR(64) NOT NULL DEFAULT 'teammate'");
        } catch (SQLException exception) {
            if (!hasColumn("team_member", "role")) {
                throw exception;
            }
        }
    }

    private void ensureMemberOptionsColumn(Statement statement) throws SQLException {
        if (hasColumn("team_member", "options")) {
            return;
        }
        statement.executeUpdate("ALTER TABLE team_member ADD COLUMN options TEXT");
        if (hasColumn("team_member", "model_ref_json")) {
            statement.executeUpdate("UPDATE team_member SET options = model_ref_json WHERE options IS NULL");
        }
    }

    private void ensureTeamCapabilityColumns(Statement statement) throws SQLException {
        if (!hasColumn("team_info", "dispatch_mode")) {
            statement.executeUpdate("ALTER TABLE team_info ADD COLUMN dispatch_mode VARCHAR(64) "
                    + "NOT NULL DEFAULT 'autonomous'");
        }
        if (!hasColumn("team_info", "enable_task_verification")) {
            statement.executeUpdate("ALTER TABLE team_info ADD COLUMN enable_task_verification BOOLEAN "
                    + "NOT NULL DEFAULT FALSE");
        }
    }

    private boolean hasColumn(String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT * FROM " + safeTableName(tableName) + " WHERE 1=0")) {
            ResultSetMetaData metadata = result.getMetaData();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                if (columnName.equalsIgnoreCase(metadata.getColumnName(index))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void createTaskTable(Statement statement, String taskTable) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %s (
                    task_id VARCHAR(255) PRIMARY KEY,
                    team_name VARCHAR(255),
                    title TEXT,
                    content TEXT,
                    status VARCHAR(64),
                    assignee VARCHAR(255),
                    reviewer TEXT,
                    review_round INTEGER NOT NULL DEFAULT 0,
                    max_review_rounds INTEGER,
                    updated_at BIGINT,
                    FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                )
                """.formatted(safeTableName(taskTable)));
    }

    private void createDependencyTable(Statement statement, String dependencyTable, String taskTable)
            throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %s (
                    task_id VARCHAR(255),
                    depends_on_task_id VARCHAR(255),
                    team_name VARCHAR(255),
                    resolved BOOLEAN DEFAULT FALSE,
                    PRIMARY KEY (task_id, depends_on_task_id),
                    FOREIGN KEY (task_id) REFERENCES %s(task_id) ON DELETE CASCADE,
                    FOREIGN KEY (depends_on_task_id) REFERENCES %s(task_id) ON DELETE CASCADE
                )
                """.formatted(safeTableName(dependencyTable), safeTableName(taskTable), safeTableName(taskTable)));
    }

    private void createMessageTable(Statement statement, String messageTable) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %s (
                    message_id VARCHAR(255) PRIMARY KEY,
                    team_name VARCHAR(255),
                    from_member_name VARCHAR(255),
                    to_member_name VARCHAR(255),
                    content TEXT,
                    %s BIGINT,
                    broadcast BOOLEAN,
                    protocol VARCHAR(64) NOT NULL DEFAULT 'plain',
                    is_read BOOLEAN,
                    meta TEXT,
                    FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                )
                """.formatted(safeTableName(messageTable), quoteIdentifier("timestamp")));
    }

    private void createReadStatusTable(Statement statement, String readStatusTable) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %s (
                    member_name VARCHAR(255),
                    team_name VARCHAR(255),
                    read_at BIGINT,
                    PRIMARY KEY (member_name, team_name),
                    FOREIGN KEY (team_name) REFERENCES team_info(team_name) ON DELETE CASCADE
                )
                """.formatted(safeTableName(readStatusTable)));
    }

    private void ensureTaskColumns(Statement statement, String taskTable) throws SQLException {
        String safeTaskTable = safeTableName(taskTable);
        if (!hasColumn(taskTable, "reviewer")) {
            statement.executeUpdate("ALTER TABLE " + safeTaskTable + " ADD COLUMN reviewer TEXT");
        }
        if (!hasColumn(taskTable, "review_round")) {
            statement.executeUpdate("ALTER TABLE " + safeTaskTable
                    + " ADD COLUMN review_round INTEGER NOT NULL DEFAULT 0");
        }
        if (!hasColumn(taskTable, "max_review_rounds")) {
            statement.executeUpdate("ALTER TABLE " + safeTaskTable + " ADD COLUMN max_review_rounds INTEGER");
        }
    }

    private void ensureDependencyResolvedColumn(Statement statement, String dependencyTable) throws SQLException {
        if (hasColumn(dependencyTable, "resolved")) {
            return;
        }
        String safeDependencyTable = safeTableName(dependencyTable);
        statement.executeUpdate("ALTER TABLE " + safeDependencyTable
                + " ADD COLUMN resolved BOOLEAN DEFAULT FALSE");
        if (hasColumn(dependencyTable, "isResolved")) {
            statement.executeUpdate("UPDATE " + safeDependencyTable + " SET resolved = isResolved");
        }
    }

    private void ensureMessageColumns(Statement statement, String messageTable) throws SQLException {
        String safeMessageTable = safeTableName(messageTable);
        if (!hasColumn(messageTable, "protocol")) {
            statement.executeUpdate("ALTER TABLE " + safeMessageTable
                    + " ADD COLUMN protocol VARCHAR(64) NOT NULL DEFAULT 'plain'");
        }
        if (!hasColumn(messageTable, "meta")) {
            statement.executeUpdate("ALTER TABLE " + safeMessageTable + " ADD COLUMN meta TEXT");
        }
    }

    private void loadTeams(Map<String, TeamRecord> teams) throws SQLException {
        String sql = """
                SELECT team_name, display_name, leader_member_name, %s AS team_desc, prompt, created, updated_at
                FROM team_info
                """.formatted(quoteIdentifier("desc"));
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                TeamRecord record = TeamRecord.builder()
                        .teamName(result.getString("team_name"))
                        .displayName(result.getString("display_name"))
                        .leaderMemberName(result.getString("leader_member_name"))
                        .desc(result.getString("team_desc"))
                        .prompt(result.getString("prompt"))
                        .created(result.getLong("created"))
                        .updatedAt(result.getLong("updated_at"))
                        .build();
                teams.put(record.getTeamName(), record);
            }
        }
    }

    private void loadMembers(Map<String, MemberRecord> members) throws SQLException {
        String sql = """
                SELECT member_name, team_name, display_name, agent_card, status, %s AS member_desc,
                       execution_status, mode, prompt, options, updated_at, role
                FROM team_member
                """.formatted(quoteIdentifier("desc"));
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                MemberRecord record = memberRecord(result);
                members.put(record.getTeamName() + "::" + record.getMemberName(), record);
            }
        }
    }

    private static MemberRecord memberRecord(ResultSet result) throws SQLException {
        return MemberRecord.builder()
                .memberName(result.getString("member_name"))
                .teamName(result.getString("team_name"))
                .displayName(result.getString("display_name"))
                .agentCard(result.getString("agent_card"))
                .status(result.getString("status"))
                .desc(result.getString("member_desc"))
                .executionStatus(result.getString("execution_status"))
                .mode(result.getString("mode"))
                .prompt(result.getString("prompt"))
                .modelRefJson(result.getString("options"))
                .updatedAt(result.getLong("updated_at"))
                .role(result.getString("role"))
                .build();
    }

    private void loadTasks(String sessionId, Map<String, TaskRecord> tasks) throws SQLException {
        String sql = "SELECT task_id, team_name, title, content, status, assignee, updated_at FROM "
                + safeTableName(taskTableName(sessionId));
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                TaskRecord record = TaskRecord.builder()
                        .taskId(result.getString("task_id"))
                        .teamName(result.getString("team_name"))
                        .title(result.getString("title"))
                        .content(result.getString("content"))
                        .status(result.getString("status"))
                        .assignee(result.getString("assignee"))
                        .updatedAt(result.getLong("updated_at"))
                        .build();
                tasks.put(record.getTaskId(), record);
            }
        }
    }

    private void loadDependencies(String sessionId, Map<String, TaskRecord> tasks) throws SQLException {
        String sql = "SELECT task_id, depends_on_task_id FROM " + safeTableName(dependencyTableName(sessionId))
                + " ORDER BY task_id, depends_on_task_id";
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                TaskRecord taskRecord = tasks.get(result.getString("task_id"));
                if (taskRecord != null) {
                    taskRecord.getDependencies().add(result.getString("depends_on_task_id"));
                }
            }
        }
    }

    private void loadMessages(String sessionId, Map<String, MessageRecord> messages) throws SQLException {
        String sql = """
                SELECT message_id, team_name, from_member_name, to_member_name, content,
                       %s AS message_timestamp, broadcast, is_read
                FROM %s
                """.formatted(quoteIdentifier("timestamp"), safeTableName(messageTableName(sessionId)));
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                MessageRecord record = MessageRecord.builder()
                        .messageId(result.getString("message_id"))
                        .teamName(result.getString("team_name"))
                        .fromMemberName(result.getString("from_member_name"))
                        .toMemberName(result.getString("to_member_name"))
                        .content(result.getString("content"))
                        .timestamp(result.getLong("message_timestamp"))
                        .broadcast(result.getBoolean("broadcast"))
                        .isRead(result.getBoolean("is_read"))
                        .build();
                messages.put(record.getMessageId(), record);
            }
        }
    }

    private void loadReadStatuses(String sessionId, Map<String, Long> readStatuses) throws SQLException {
        String sql = "SELECT member_name, team_name, read_at FROM " + safeTableName(readStatusTableName(sessionId));
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                String key = result.getString("team_name") + "::" + result.getString("member_name");
                readStatuses.put(key, result.getLong("read_at"));
            }
        }
    }

    private void clearStaticRows() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM team_member");
            statement.executeUpdate("DELETE FROM team_info");
        }
    }

    private void upsertTeams(Collection<TeamRecord> teams) throws SQLException {
        String updateSql = """
                UPDATE team_info
                SET display_name = ?, leader_member_name = ?, %s = ?, prompt = ?, created = ?, updated_at = ?
                WHERE team_name = ?
                """.formatted(quoteIdentifier("desc"));
        String insertSql = """
                INSERT INTO team_info
                    (team_name, display_name, leader_member_name, %s, prompt, created, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.formatted(quoteIdentifier("desc"));
        Set<String> persistedTeams = loadPersistedTeamNames();
        try (PreparedStatement update = connection.prepareStatement(updateSql);
                PreparedStatement insert = connection.prepareStatement(insertSql)) {
            for (TeamRecord teamRecord : teams) {
                if (persistedTeams.contains(teamRecord.getTeamName())) {
                    setTeamUpdateParameters(update, teamRecord);
                    update.executeUpdate();
                } else {
                    setTeamInsertParameters(insert, teamRecord);
                    insert.executeUpdate();
                }
            }
        }
    }

    private static void setTeamUpdateParameters(PreparedStatement statement, TeamRecord record)
            throws SQLException {
        statement.setString(1, record.getDisplayName());
        statement.setString(2, record.getLeaderMemberName());
        statement.setString(3, record.getDesc());
        statement.setString(4, record.getPrompt());
        statement.setLong(5, record.getCreated());
        statement.setLong(6, record.getUpdatedAt());
        statement.setString(7, record.getTeamName());
    }

    private static void setTeamInsertParameters(PreparedStatement statement, TeamRecord record)
            throws SQLException {
        statement.setString(1, record.getTeamName());
        statement.setString(2, record.getDisplayName());
        statement.setString(3, record.getLeaderMemberName());
        statement.setString(4, record.getDesc());
        statement.setString(5, record.getPrompt());
        statement.setLong(6, record.getCreated());
        statement.setLong(7, record.getUpdatedAt());
    }

    private void upsertMembers(Collection<MemberRecord> members) throws SQLException {
        String updateSql = """
                UPDATE team_member
                SET display_name = ?, agent_card = ?, status = ?, %s = ?, execution_status = ?, mode = ?,
                    prompt = ?, options = ?, updated_at = ?, role = ?
                WHERE member_name = ? AND team_name = ?
                """.formatted(quoteIdentifier("desc"));
        String insertSql = """
                INSERT INTO team_member
                    (member_name, team_name, display_name, agent_card, status, %s, execution_status,
                     mode, prompt, options, updated_at, role)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(quoteIdentifier("desc"));
        Set<MemberKey> persistedMembers = loadPersistedMemberKeys();
        try (PreparedStatement update = connection.prepareStatement(updateSql);
                PreparedStatement insert = connection.prepareStatement(insertSql)) {
            for (MemberRecord memberRecord : members) {
                MemberKey memberKey = new MemberKey(memberRecord.getMemberName(), memberRecord.getTeamName());
                if (persistedMembers.contains(memberKey)) {
                    setMemberUpdateParameters(update, memberRecord);
                    update.executeUpdate();
                } else {
                    setMemberInsertParameters(insert, memberRecord);
                    insert.executeUpdate();
                }
            }
        }
    }

    private static void setMemberUpdateParameters(PreparedStatement statement, MemberRecord record)
            throws SQLException {
        statement.setString(1, record.getDisplayName());
        statement.setString(2, record.getAgentCard());
        statement.setString(3, record.getStatus());
        statement.setString(4, record.getDesc());
        statement.setString(5, record.getExecutionStatus());
        statement.setString(6, record.getMode());
        statement.setString(7, record.getPrompt());
        statement.setString(8, record.getModelRefJson());
        statement.setLong(9, record.getUpdatedAt());
        statement.setString(10, normalizedRole(record.getRole()));
        statement.setString(11, record.getMemberName());
        statement.setString(12, record.getTeamName());
    }

    private static void setMemberInsertParameters(PreparedStatement statement, MemberRecord record)
            throws SQLException {
        statement.setString(1, record.getMemberName());
        statement.setString(2, record.getTeamName());
        statement.setString(3, record.getDisplayName());
        statement.setString(4, record.getAgentCard());
        statement.setString(5, record.getStatus());
        statement.setString(6, record.getDesc());
        statement.setString(7, record.getExecutionStatus());
        statement.setString(8, record.getMode());
        statement.setString(9, record.getPrompt());
        statement.setString(10, record.getModelRefJson());
        statement.setLong(11, record.getUpdatedAt());
        statement.setString(12, normalizedRole(record.getRole()));
    }

    private void deleteMissingMembers(Collection<MemberRecord> members) throws SQLException {
        Set<MemberKey> retainedMembers = new HashSet<>();
        for (MemberRecord member : members) {
            retainedMembers.add(new MemberKey(member.getMemberName(), member.getTeamName()));
        }
        Set<MemberKey> persistedMembers = loadPersistedMemberKeys();
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM team_member WHERE member_name = ? AND team_name = ?")) {
            for (MemberKey member : persistedMembers) {
                if (!retainedMembers.contains(member)) {
                    delete.setString(1, member.memberName());
                    delete.setString(2, member.teamName());
                    delete.addBatch();
                }
            }
            delete.executeBatch();
        }
    }

    private void deleteMissingTeams(Collection<TeamRecord> teams) throws SQLException {
        Set<String> retainedTeams = new HashSet<>();
        for (TeamRecord team : teams) {
            retainedTeams.add(team.getTeamName());
        }
        Set<String> persistedTeams = loadPersistedTeamNames();
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM team_info WHERE team_name = ?")) {
            for (String teamName : persistedTeams) {
                if (!retainedTeams.contains(teamName)) {
                    delete.setString(1, teamName);
                    delete.addBatch();
                }
            }
            delete.executeBatch();
        }
    }

    private Set<String> loadPersistedTeamNames() throws SQLException {
        Set<String> persistedTeams = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT team_name FROM team_info")) {
            while (result.next()) {
                persistedTeams.add(result.getString("team_name"));
            }
        }
        return persistedTeams;
    }

    private Set<MemberKey> loadPersistedMemberKeys() throws SQLException {
        Set<MemberKey> persistedMembers = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT member_name, team_name FROM team_member")) {
            while (result.next()) {
                persistedMembers.add(new MemberKey(
                        result.getString("member_name"), result.getString("team_name")));
            }
        }
        return persistedMembers;
    }

    private static String normalizedRole(String role) {
        return role == null || role.isBlank() ? "teammate" : role;
    }

    private void clearSessionRows(String sessionId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM " + safeTableName(dependencyTableName(sessionId)));
            statement.executeUpdate("DELETE FROM " + safeTableName(taskTableName(sessionId)));
            statement.executeUpdate("DELETE FROM " + safeTableName(messageTableName(sessionId)));
            statement.executeUpdate("DELETE FROM " + safeTableName(readStatusTableName(sessionId)));
        }
    }

    private void insertTasks(String sessionId, Collection<TaskRecord> tasks) throws SQLException {
        String sql = "INSERT INTO " + safeTableName(taskTableName(sessionId))
                + " (task_id, team_name, title, content, status, assignee, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TaskRecord taskRecord : tasks) {
                statement.setString(1, taskRecord.getTaskId());
                statement.setString(2, taskRecord.getTeamName());
                statement.setString(3, taskRecord.getTitle());
                statement.setString(4, taskRecord.getContent());
                statement.setString(5, taskRecord.getStatus());
                statement.setString(6, taskRecord.getAssignee());
                statement.setLong(7, taskRecord.getUpdatedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertDependencies(String sessionId, Collection<TaskRecord> tasks) throws SQLException {
        Map<String, TaskRecord> tasksById = tasks.stream()
                .collect(java.util.stream.Collectors.toMap(TaskRecord::getTaskId, record -> record));
        String sql = "INSERT INTO " + safeTableName(dependencyTableName(sessionId))
                + " (task_id, depends_on_task_id, team_name, resolved) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TaskRecord taskRecord : tasks) {
                for (String dependencyId : taskRecord.getDependencies()) {
                    statement.setString(1, taskRecord.getTaskId());
                    statement.setString(2, dependencyId);
                    statement.setString(3, taskRecord.getTeamName());
                    statement.setBoolean(4, isResolved(tasksById.get(dependencyId)));
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private static boolean isResolved(TaskRecord taskRecord) {
        return taskRecord != null
                && ("completed".equals(taskRecord.getStatus()) || "cancelled".equals(taskRecord.getStatus()));
    }

    private void insertMessages(String sessionId, Collection<MessageRecord> messages) throws SQLException {
        String sql = "INSERT INTO " + safeTableName(messageTableName(sessionId))
                + " (message_id, team_name, from_member_name, to_member_name, content, "
                + quoteIdentifier("timestamp") + ", broadcast, is_read) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (MessageRecord messageRecord : messages) {
                statement.setString(1, messageRecord.getMessageId());
                statement.setString(2, messageRecord.getTeamName());
                statement.setString(3, messageRecord.getFromMemberName());
                statement.setString(4, messageRecord.getToMemberName());
                statement.setString(5, messageRecord.getContent());
                statement.setLong(6, messageRecord.getTimestamp());
                statement.setBoolean(7, messageRecord.isBroadcast());
                statement.setBoolean(8, messageRecord.isRead());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertReadStatuses(String sessionId, Map<String, Long> readStatuses) throws SQLException {
        String sql = "INSERT INTO " + safeTableName(readStatusTableName(sessionId))
                + " (team_name, member_name, read_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, Long> entry : readStatuses.entrySet()) {
                String[] parts = entry.getKey().split("::", 2);
                if (parts.length != 2) {
                    continue;
                }
                statement.setString(1, parts[0]);
                statement.setString(2, parts[1]);
                statement.setLong(3, entry.getValue() == null ? 0L : entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private boolean hasExistingTable(List<String> tableNames) throws SQLException {
        for (String tableName : tableNames) {
            if (tableExists(tableName)) {
                return true;
            }
        }
        return false;
    }

    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, null, new String[]{"TABLE"})) {
            while (result.next()) {
                if (tableName.equalsIgnoreCase(result.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private List<String> listDynamicTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getTables(connection.getCatalog(), null, null, new String[]{"TABLE"})) {
            while (result.next()) {
                String tableName = result.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                if (isDynamicTable(tableName)) {
                    tables.add(safeTableName(tableName));
                }
            }
        }
        return tables;
    }

    private void dropTables(List<String> tableNames) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String tableName : tableNames) {
                statement.executeUpdate("DROP TABLE IF EXISTS " + safeTableName(tableName));
            }
        }
    }

    private static boolean isDynamicTable(String tableName) {
        return tableName.startsWith(TEAM_TASK_DEPENDENCY_PREFIX)
                || tableName.startsWith(TEAM_TASK_PREFIX)
                || tableName.startsWith(TEAM_MESSAGE_PREFIX)
                || tableName.startsWith(MESSAGE_READ_STATUS_PREFIX);
    }

    private static int dropOrder(String tableName) {
        return tableName.startsWith(TEAM_TASK_DEPENDENCY_PREFIX) ? 0 : 1;
    }

    private void runInTransaction(SqlOperation operation) throws SQLException {
        synchronized (transactionLock) {
            try (TransactionScope transaction = new TransactionScope()) {
                operation.execute();
                transaction.commit();
            }
        }
    }

    private String quoteIdentifier(String identifier) {
        String safeIdentifier = safeTableName(identifier);
        if (databaseType == DatabaseType.MYSQL) {
            return "`" + safeIdentifier + "`";
        }
        return "\"" + safeIdentifier + "\"";
    }

    private static String taskTableName(String sessionId) {
        return TEAM_TASK_PREFIX + TeamDatabase.sanitizeSessionIdForTable(sessionId);
    }

    private static String dependencyTableName(String sessionId) {
        return TEAM_TASK_DEPENDENCY_PREFIX + TeamDatabase.sanitizeSessionIdForTable(sessionId);
    }

    private static String messageTableName(String sessionId) {
        return TEAM_MESSAGE_PREFIX + TeamDatabase.sanitizeSessionIdForTable(sessionId);
    }

    private static String readStatusTableName(String sessionId) {
        return MESSAGE_READ_STATUS_PREFIX + TeamDatabase.sanitizeSessionIdForTable(sessionId);
    }

    private static String safeTableName(String tableName) {
        if (tableName == null || !SQL_IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid JDBC table identifier");
        }
        return tableName;
    }

    private static void closeAfterFailedInitialization(Connection connection, Throwable originalException) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException closeException) {
            originalException.addSuppressed(closeException);
        }
    }

    private final class TransactionScope implements AutoCloseable {
        private final boolean isPreviousAutoCommit;
        private boolean isCommitted;

        private TransactionScope() throws SQLException {
            isPreviousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        }

        private void commit() throws SQLException {
            connection.commit();
            isCommitted = true;
        }

        /** {@inheritDoc} */
        @Override
        public void close() throws SQLException {
            SQLException cleanupFailure = null;
            if (!isCommitted) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    cleanupFailure = rollbackFailure;
                }
            }
            try {
                connection.setAutoCommit(isPreviousAutoCommit);
            } catch (SQLException restoreFailure) {
                if (cleanupFailure == null) {
                    cleanupFailure = restoreFailure;
                } else {
                    cleanupFailure.addSuppressed(restoreFailure);
                }
            }
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        /**
         * Executes a database operation in the active transaction.
         *
         * @throws SQLException if the database operation fails
         */
        void execute() throws SQLException;
    }

    private record MemberKey(String memberName, String teamName) {
    }
}
