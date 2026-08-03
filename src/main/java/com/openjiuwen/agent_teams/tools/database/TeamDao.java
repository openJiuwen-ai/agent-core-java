/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.tools.Team;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Team table data access object.
 *
 * <p>Mirrors Python's {@code TeamDao} in
 * {@code openjiuwen/agent_teams/tools/database/team_dao.py}.</p>
 */
public class TeamDao {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final DatabaseEngine engine;

    public TeamDao() {
        this(createDefaultEngine());
    }

    public TeamDao(DatabaseEngine engine) {
        this.engine = engine;
        ensureInitialized();
    }

    public CompletableFuture<Boolean> createTeam(
            String teamName,
            String displayName,
            String leaderMemberName,
            String desc,
            String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                long timestamp = DatabaseEngine.getCurrentTime();
                try (PreparedStatement statement = connection().prepareStatement(
                        "INSERT INTO team_info (team_name, display_name, leader_member_name, \"desc\", prompt, "
                                + "created, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, teamName);
                    statement.setString(2, displayName);
                    statement.setString(3, leaderMemberName);
                    statement.setString(4, desc);
                    statement.setString(5, prompt);
                    statement.setLong(6, timestamp);
                    statement.setLong(7, timestamp);
                    statement.executeUpdate();
                    TEAM_LOGGER.info("Team %s created", teamName);
                    return true;
                } catch (SQLException exception) {
                    if (isIntegrityViolation(exception)) {
                        TEAM_LOGGER.error("Team %s already exists: %s", teamName, exception.getMessage());
                        return false;
                    }
                    throw new RuntimeException("Failed to create team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Optional<Team>> getTeam(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT team_name, display_name, leader_member_name, \"desc\", prompt, created, updated_at "
                                + "FROM team_info WHERE team_name = ?")) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(mapTeam(resultSet));
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to get team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> teamExists(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT team_name FROM team_info WHERE team_name = ?")) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        return resultSet.next();
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to check team existence for " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Boolean> deleteTeam(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "DELETE FROM team_info WHERE team_name = ?")) {
                    statement.setString(1, teamName);
                    int affected = statement.executeUpdate();
                    if (affected == 0) {
                        TEAM_LOGGER.debug("Team %s not found for deletion", teamName);
                        return false;
                    }
                    TEAM_LOGGER.info("Team %s deleted", teamName);
                    return true;
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to delete team " + teamName, exception);
                }
            }
        });
    }

    public CompletableFuture<Long> getTeamUpdatedAt(String teamName) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (engine) {
                try (PreparedStatement statement = connection().prepareStatement(
                        "SELECT updated_at FROM team_info WHERE team_name = ?")) {
                    statement.setString(1, teamName);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return 0L;
                        }
                        Long updatedAt = getNullableLong(resultSet, "updated_at");
                        return updatedAt == null ? 0L : updatedAt;
                    }
                } catch (SQLException exception) {
                    throw new RuntimeException("Failed to read updated_at for team " + teamName, exception);
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

    private boolean isIntegrityViolation(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("23")) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase().contains("unique");
    }

    private Team mapTeam(ResultSet resultSet) throws SQLException {
        return new Team(
                resultSet.getString("team_name"),
                resultSet.getString("display_name"),
                resultSet.getString("leader_member_name"),
                resultSet.getString("desc"),
                resultSet.getString("prompt"),
                resultSet.getLong("created"),
                getNullableLong(resultSet, "updated_at")
        );
    }

    private Long getNullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
