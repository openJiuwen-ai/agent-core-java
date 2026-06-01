/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.tools.TeamMember;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Member table data access object.
 *
 * <p>Mirrors Python's {@code MemberDao} in
 * {@code openjiuwen.agent_teams.tools.database.member_dao}.</p>
 */
public class MemberDao {

    private static final Logger teamLogger = Logger.getLogger(MemberDao.class.getName());
    private static final String BUILD_MODE = "build_mode";

    private static final Map<String, Set<String>> MEMBER_TRANSITIONS = Map.of(
            "unstarted", Set.of("ready", "shut_down", "error"),
            "ready", Set.of("ready", "busy", "shutdown_requested", "shut_down", "error"),
            "busy", Set.of("ready", "shutdown_requested", "error"),
            "restarting", Set.of("ready", "error", "shut_down"),
            "shutdown_requested", Set.of("shut_down", "error"),
            "shut_down", Set.of("restarting"),
            "error", Set.of("restarting", "ready", "shutdown_requested", "shut_down")
    );

    private static final Map<String, Set<String>> EXECUTION_TRANSITIONS = Map.of(
            "idle", Set.of("starting"),
            "starting", Set.of("running", "cancel_requested", "cancelling", "failed", "timed_out"),
            "running", Set.of("cancel_requested", "cancelling", "completing", "failed", "timed_out"),
            "cancel_requested", Set.of("cancelling", "cancelled", "failed", "timed_out"),
            "cancelling", Set.of("cancelled", "failed", "timed_out"),
            "cancelled", Set.of("idle"),
            "completing", Set.of("completed", "failed", "timed_out"),
            "completed", Set.of("idle"),
            "failed", Set.of("idle"),
            "timed_out", Set.of("idle")
    );

    private final TeamDatabaseState state;

    public MemberDao() {
        this(new TeamDatabaseState(DatabaseConfig.inMemory()));
        this.state.createCurrentSessionTables();
    }

    public MemberDao(TeamDatabaseState state) {
        this.state = state;
    }

    public CompletableFuture<Boolean> createMember(
            String memberName,
            String teamName,
            String displayName,
            String agentCard,
            String status) {
        return createMember(memberName, teamName, displayName, agentCard, status,
                null, null, BUILD_MODE, null, null);
    }

    public CompletableFuture<Boolean> createMember(
            String memberName,
            String teamName,
            String displayName,
            String agentCard,
            String status,
            String desc,
            String executionStatus,
            String mode,
            String prompt,
            String modelRefJson) {
        TeamDatabaseState.MemberKey key = new TeamDatabaseState.MemberKey(memberName, teamName);
        if (state.members().containsKey(key)) {
            teamLogger.severe(String.format("Member %s already exists", memberName));
            return CompletableFuture.completedFuture(false);
        }
        TeamMember member = new TeamMember(
                memberName,
                teamName,
                displayName,
                desc,
                agentCard,
                status,
                executionStatus,
                mode != null ? mode : BUILD_MODE,
                prompt,
                modelRefJson,
                DatabaseEngine.getCurrentTime());
        boolean created = state.members().putIfAbsent(key, member) == null;
        if (created) {
            teamLogger.info(String.format("Member %s created", memberName));
        }
        return CompletableFuture.completedFuture(created);
    }

    public CompletableFuture<Optional<TeamMember>> getMember(String memberName, String teamName) {
        TeamDatabaseState.MemberKey key = new TeamDatabaseState.MemberKey(memberName, teamName);
        return CompletableFuture.completedFuture(Optional.ofNullable(state.members().get(key)));
    }

    public CompletableFuture<List<TeamMember>> getTeamMembers(String teamName, String status) {
        String normalizedStatus = normalize(status);
        List<TeamMember> members = state.members().values().stream()
                .filter(member -> teamName.equals(member.getTeamName()))
                .filter(member -> normalizedStatus == null || normalizedStatus.equals(normalize(member.getStatus())))
                .sorted(Comparator.comparing(TeamMember::getMemberName))
                .toList();
        return CompletableFuture.completedFuture(members);
    }

    public CompletableFuture<Long> getMembersMaxUpdatedAt(String teamName) {
        long max = state.members().values().stream()
                .filter(member -> teamName.equals(member.getTeamName()))
                .map(TeamMember::getUpdatedAt)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        return CompletableFuture.completedFuture(max);
    }

    public CompletableFuture<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
        TeamMember member = state.members().get(new TeamDatabaseState.MemberKey(memberName, teamName));
        if (member == null) {
            teamLogger.severe(String.format("Member %s not found in team %s", memberName, teamName));
            return CompletableFuture.completedFuture(false);
        }
        if (!isValidTransition(member.getStatus(), status, MEMBER_TRANSITIONS)) {
            teamLogger.severe(String.format(
                    "Invalid state transition for member %s: %s -> %s", memberName, member.getStatus(), status));
            return CompletableFuture.completedFuture(false);
        }
        member.setStatus(status);
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> updateMemberExecutionStatus(
            String memberName,
            String teamName,
            String executionStatus) {
        TeamMember member = state.members().get(new TeamDatabaseState.MemberKey(memberName, teamName));
        if (member == null) {
            teamLogger.severe(String.format("Member %s not found in team %s", memberName, teamName));
            return CompletableFuture.completedFuture(false);
        }
        if (!isValidTransition(member.getExecutionStatus(), executionStatus, EXECUTION_TRANSITIONS)) {
            teamLogger.severe(String.format(
                    "Invalid execution transition for member %s: %s -> %s",
                    memberName, member.getExecutionStatus(), executionStatus));
            return CompletableFuture.completedFuture(false);
        }
        member.setExecutionStatus(executionStatus);
        return CompletableFuture.completedFuture(true);
    }

    private static boolean isValidTransition(String current, String next, Map<String, Set<String>> transitions) {
        String currentValue = normalize(current);
        String nextValue = normalize(next);
        return currentValue != null && nextValue != null
                && transitions.getOrDefault(currentValue, Set.of()).contains(nextValue);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
