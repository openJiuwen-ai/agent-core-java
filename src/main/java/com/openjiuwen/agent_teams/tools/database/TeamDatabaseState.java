/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.MessageReadStatus;
import com.openjiuwen.agent_teams.tools.Team;
import com.openjiuwen.agent_teams.tools.TeamMember;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import com.openjiuwen.agent_teams.tools.TeamTaskDependency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Shared Java persistence state for the team database facade and DAOs.
 *
 * <p>Mirrors the SQLAlchemy session factory owned by Python's
 * {@code TeamDatabase}. The Java runtime keeps an in-memory representation
 * so tests and local agent-team workflows can exercise the same lifecycle
 * and table isolation semantics without an async SQLAlchemy dependency.</p>
 */
public final class TeamDatabaseState {

    static final List<String> DYNAMIC_PREFIXES = List.of(
            "team_task_dependency_",
            "team_task_",
            "team_message_",
            "message_read_status_"
    );

    static final List<String> STATIC_TABLES_TO_CLEAR = List.of("team_info", "team_member");

    private final DatabaseConfig config;
    private final ConcurrentMap<String, Team> teams = new ConcurrentHashMap<>();
    private final ConcurrentMap<MemberKey, TeamMember> members = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SessionData> sessions = new ConcurrentHashMap<>();
    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();

    public TeamDatabaseState(DatabaseConfig config) {
        this.config = config != null ? config : DatabaseConfig.inMemory();
    }

    public DatabaseConfig config() {
        return config;
    }

    ConcurrentMap<String, Team> teams() {
        return teams;
    }

    ConcurrentMap<MemberKey, TeamMember> members() {
        return members;
    }

    SessionData currentSession() {
        String sessionId = currentSessionId();
        if (sessionId.isEmpty() || !activeSessions.contains(sessionId)) {
            throw new IllegalStateException("no such table: team_task_" + sanitizeSessionIdForTable(sessionId));
        }
        return sessions.computeIfAbsent(sessionId, ignored -> new SessionData());
    }

    public synchronized void createCurrentSessionTables() {
        String sessionId = currentSessionId();
        if (sessionId.isEmpty()) {
            return;
        }
        sessions.computeIfAbsent(sessionId, ignored -> new SessionData());
        activeSessions.add(sessionId);
    }

    public synchronized void dropCurrentSessionTables() {
        String sessionId = currentSessionId();
        if (sessionId.isEmpty()) {
            return;
        }
        dropSessionTablesById(sessionId);
    }

    public synchronized List<String> dropSessionTablesById(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return List.of();
        }
        boolean existed = activeSessions.remove(sessionId);
        existed = sessions.remove(sessionId) != null || existed;
        return existed ? tableNamesForSession(sessionId) : List.of();
    }

    public synchronized RuntimeStateCleanup cleanupAllRuntimeState() {
        List<String> deletedTables = new ArrayList<>();
        for (String sessionId : sessions.keySet()) {
            deletedTables.addAll(tableNamesForSession(sessionId));
        }
        sessions.clear();
        activeSessions.clear();

        teams.clear();
        members.clear();
        return new RuntimeStateCleanup(deletedTables, STATIC_TABLES_TO_CLEAR);
    }

    public synchronized void close() {
        sessions.clear();
        activeSessions.clear();
        teams.clear();
        members.clear();
    }

    synchronized void deleteTeamCascade(String teamName) {
        members.keySet().removeIf(key -> key.teamName().equals(teamName));
        for (SessionData session : sessions.values()) {
            session.tasks().values().removeIf(task -> teamName.equals(task.getTeamName()));
            session.dependencies().values().removeIf(dep -> teamName.equals(dep.getTeamName()));
            session.messages().values().removeIf(message -> teamName.equals(message.getTeamName()));
            session.readStatuses().keySet().removeIf(key -> key.teamName().equals(teamName));
        }
    }

    static String currentSessionId() {
        String sessionId = SpawnContext.getSessionId();
        return sessionId != null ? sessionId : "";
    }

    static List<String> tableNamesForSession(String sessionId) {
        String suffix = sanitizeSessionIdForTable(sessionId);
        return List.of(
                "team_task_dependency_" + suffix,
                "team_task_" + suffix,
                "team_message_" + suffix,
                "message_read_status_" + suffix
        );
    }

    static String sanitizeSessionIdForTable(String sessionId) {
        String source = sessionId != null ? sessionId : "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(source.hashCode());
        }
    }

    public record RuntimeStateCleanup(List<String> deletedTables, List<String> clearedTables) {
    }

    record MemberKey(String memberName, String teamName) {
    }

    record DependencyKey(String taskId, String dependsOnTaskId) {
    }

    record ReadStatusKey(String memberName, String teamName) {
    }

    static final class SessionData {
        private final ConcurrentMap<String, TeamTask> tasks = new ConcurrentHashMap<>();
        private final ConcurrentMap<DependencyKey, TeamTaskDependency> dependencies = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, TeamMessage> messages = new ConcurrentHashMap<>();
        private final ConcurrentMap<ReadStatusKey, MessageReadStatus> readStatuses = new ConcurrentHashMap<>();

        ConcurrentMap<String, TeamTask> tasks() {
            return tasks;
        }

        ConcurrentMap<DependencyKey, TeamTaskDependency> dependencies() {
            return dependencies;
        }

        ConcurrentMap<String, TeamMessage> messages() {
            return messages;
        }

        ConcurrentMap<ReadStatusKey, MessageReadStatus> readStatuses() {
            return readStatuses;
        }
    }
}
