/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Member table data access object.
 *
 * <p>Mirrors Python's {@code MemberDao} in
 * {@code openjiuwen/agent_teams/tools/database/member_dao.py}.</p>
 */
public class MemberDao {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final DatabaseEngine engine;

    public MemberDao() {
        this(createDefaultEngine());
    }

    public MemberDao(DatabaseEngine engine) {
        this.engine = engine;
        ensureInitialized();
    }

    public CompletableFuture<Boolean> createMember(
            String memberName,
            String teamName,
            String displayName,
            String agentCard,
            String status) {
        return createMember(
                memberName,
                teamName,
                displayName,
                agentCard,
                status,
                TeamRole.TEAMMATE.value(),
                null,
                null,
                MemberMode.BUILD_MODE.value(),
                null,
                null);
    }

    public CompletableFuture<Boolean> createMember(
            String memberName,
            String teamName,
            String displayName,
            String agentCard,
            String status,
            String role,
            String desc,
            String executionStatus,
            String mode,
            String prompt,
            String modelRefJson) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                long timestamp = DatabaseEngine.getCurrentTime();
                try (PreparedStatement statement = connection().prepareStatement(
                        "INSERT INTO team_member (member_name, team_name, display_name, \"desc\", agent_card, "
                                + "status, execution_status, mode, role, prompt, model_ref_json, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, memberName);
                    statement.setString(2, teamName);
                    statement.setString(3, displayName);
                    statement.setString(4, desc);
                    statement.setString(5, agentCard);
                    statement.setString(6, status);
                    statement.setString(7, executionStatus);
                    statement.setString(8, mode == null ? MemberMode.BUILD_MODE.value() : mode);
                    statement.setString(9, role == null ? TeamRole.TEAMMATE.value() : role);
                    statement.setString(10, prompt);
                    statement.setString(11, modelRefJson);
                    statement.setLong(12, timestamp);
                    statement.executeUpdate();
                    TEAM_LOGGER.info("Member %s created", memberName);
                    return true;
                } catch (SQLException exception) {
                    if (isIntegrityViolation(exception)) {
                        TEAM_LOGGER.error("Member %s already exists", memberName);
                        return false;
                    }
                    throw new RuntimeException("Failed to create member " + memberName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> isHumanAgent(String teamName, String memberName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT member_name FROM team_member WHERE team_name = ? AND member_name = ? AND role = ?")) {
                    statement.setString(1, teamName);
                    statement.setString(2, memberName);
                    statement.setString(3, TeamRole.HUMAN_AGENT.value());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next();
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to check human-agent role for " + memberName, exception);
                }
            }
        });
    }

    public CompletableFuture<List<String>> listHumanAgentNames(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT member_name FROM team_member WHERE team_name = ? AND role = ? ORDER BY member_name")) {
                    statement.setString(1, teamName);
                    statement.setString(2, TeamRole.HUMAN_AGENT.value());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<String> names = new ArrayList<>();
                        while (resultSet.next()) {
                            names.add(resultSet.getString("member_name"));
                        }
                        return names;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to list human-agent members for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Optional<TeamMember>> getMember(String memberName, String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT member_name, team_name, display_name, \"desc\", agent_card, status, "
                                + "execution_status, mode, role, prompt, model_ref_json, updated_at "
                                + "FROM team_member WHERE member_name = ? AND team_name = ?")) {
                    statement.setString(1, memberName);
                    statement.setString(2, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(mapMember(resultSet));
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get member " + memberName, exception);
                }
            }
        });
    }

    public CompletableFuture<List<TeamMember>> getTeamMembers(String teamName) {
        return getTeamMembers(teamName, null);
    }

    public CompletableFuture<List<TeamMember>> getTeamMembers(String teamName, String status) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                String sql = "SELECT member_name, team_name, display_name, \"desc\", agent_card, status, "
                        + "execution_status, mode, role, prompt, model_ref_json, updated_at "
                        + "FROM team_member WHERE team_name = ?"
                        + (status == null ? "" : " AND status = ?")
                        + " ORDER BY member_name";
                try (PreparedStatement statement = connection().prepareStatement(sql)) {
                    statement.setString(1, teamName);
                    if (status != null) {
                        statement.setString(2, status);
                    }
                    try (ResultSet resultSet = statement.executeQuery()) {
                        List<TeamMember> members = new ArrayList<>();
                        while (resultSet.next()) {
                            members.add(mapMember(resultSet));
                        }
                        return members;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to list members for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Long> getMembersMaxUpdatedAt(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT MAX(updated_at) AS max_updated_at FROM team_member WHERE team_name = ?")) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return 0L;
                        }
                        Long value = getNullableLong(resultSet, "max_updated_at");
                        return value == null ? 0L : value;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to read members max updated_at for team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> updateMemberStatus(
            String memberName,
            String teamName,
            String status) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try {
                    TeamMember member = fetchMember(connection(), memberName, teamName);
                    if (member == null) {
                        TEAM_LOGGER.error("Member %s not found in team %s", memberName, teamName);
                        return false;
                    }
                    MemberStatus current = MemberStatus.fromValue(member.getStatus());
                    MemberStatus next = MemberStatus.fromValue(status);
                    if (!StatusTransitions.isValidTransition(current, next, StatusTransitions.MEMBER_TRANSITIONS)) {
                        TEAM_LOGGER.error(
                                "Invalid state transition for member %s: %s -> %s",
                                memberName,
                                member.getStatus(),
                                status
                        );
                        return false;
                    }
                    try (PreparedStatement statement = connection().prepareStatement(
                            "UPDATE team_member SET status = ?, updated_at = ? WHERE member_name = ? AND team_name = ?")) {
                        statement.setString(1, status);
                        statement.setLong(2, DatabaseEngine.getCurrentTime());
                        statement.setString(3, memberName);
                        statement.setString(4, teamName);
                        statement.executeUpdate();
                    }
                    TEAM_LOGGER.debug("Member %s status updated to %s", memberName, status);
                    return true;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to update member status for " + memberName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> tryTransitionMemberStatus(
            String memberName,
            String teamName,
            MemberStatus fromStatus,
            MemberStatus toStatus) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "UPDATE team_member SET status = ?, updated_at = ? "
                                + "WHERE member_name = ? AND team_name = ? AND status = ?")) {
                    statement.setString(1, toStatus.value());
                    statement.setLong(2, DatabaseEngine.getCurrentTime());
                    statement.setString(3, memberName);
                    statement.setString(4, teamName);
                    statement.setString(5, fromStatus.value());
                    boolean transitioned = statement.executeUpdate() == 1;
                    if (!transitioned) {
                        TEAM_LOGGER.debug(
                                "CAS %s -> %s for member %s failed",
                                fromStatus.value(),
                                toStatus.value(),
                                memberName
                        );
                    }
                    return transitioned;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to CAS member status for " + memberName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> updateMemberExecutionStatus(
            String memberName,
            String teamName,
            String executionStatus) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try {
                    TeamMember member = fetchMember(connection(), memberName, teamName);
                    if (member == null) {
                        TEAM_LOGGER.error("Member %s not found in team %s", memberName, teamName);
                        return false;
                    }
                    ExecutionStatus current = ExecutionStatus.fromValue(member.getExecutionStatus());
                    ExecutionStatus next = ExecutionStatus.fromValue(executionStatus);
                    if (!StatusTransitions.isValidTransition(current, next, StatusTransitions.EXECUTION_TRANSITIONS)) {
                        TEAM_LOGGER.error(
                                "Invalid execution transition for member %s: %s -> %s",
                                memberName,
                                member.getExecutionStatus(),
                                executionStatus
                        );
                        return false;
                    }
                    try (PreparedStatement statement = connection().prepareStatement(
                            "UPDATE team_member SET execution_status = ?, updated_at = ? "
                                    + "WHERE member_name = ? AND team_name = ?")) {
                        statement.setString(1, executionStatus);
                        statement.setLong(2, DatabaseEngine.getCurrentTime());
                        statement.setString(3, memberName);
                        statement.setString(4, teamName);
                        statement.executeUpdate();
                    }
                    TEAM_LOGGER.debug("Member %s execution status updated to %s", memberName, executionStatus);
                    return true;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to update member execution status for " + memberName, exception);
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
    }

    private Connection connection() {
        Connection connection = engine.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Database engine is not initialized");
        }
        return connection;
    }

    private TeamMember fetchMember(Connection connection, String memberName, String teamName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT member_name, team_name, display_name, \"desc\", agent_card, status, execution_status, "
                        + "mode, role, prompt, model_ref_json, updated_at "
                        + "FROM team_member WHERE member_name = ? AND team_name = ?")) {
            statement.setString(1, memberName);
            statement.setString(2, teamName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapMember(resultSet);
            }
        }
    }

    private TeamMember mapMember(ResultSet resultSet) throws SQLException {
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

    private boolean isIntegrityViolation(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("23")) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("unique");
    }

    private Long getNullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
