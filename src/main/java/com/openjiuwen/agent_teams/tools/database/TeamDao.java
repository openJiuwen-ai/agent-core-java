/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.database;

import com.openjiuwen.agent_teams.tools.Team;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Team table data access object.
 *
 * <p>Mirrors Python's {@code TeamDao} in
 * {@code openjiuwen.agent_teams.tools.database.team_dao}.</p>
 */
public class TeamDao {

    private static final Logger teamLogger = Logger.getLogger(TeamDao.class.getName());

    private final TeamDatabaseState state;

    public TeamDao() {
        this(new TeamDatabaseState(DatabaseConfig.inMemory()));
        this.state.createCurrentSessionTables();
    }

    public TeamDao(TeamDatabaseState state) {
        this.state = state;
    }

    public CompletableFuture<Boolean> createTeam(
            String teamName,
            String displayName,
            String leaderMemberName,
            String desc,
            String prompt) {
        if (state.teams().containsKey(teamName)) {
            teamLogger.warning(String.format("Team %s already exists", teamName));
            return CompletableFuture.completedFuture(false);
        }
        long ts = getCurrentTime();
        Team team = new Team(teamName, displayName, leaderMemberName, desc, prompt, ts, ts);
        Team previous = state.teams().putIfAbsent(teamName, team);
        boolean created = previous == null;
        if (created) {
            teamLogger.info(String.format("Team %s created", teamName));
        }
        return CompletableFuture.completedFuture(created);
    }

    public CompletableFuture<Optional<Team>> getTeam(String teamName) {
        return CompletableFuture.completedFuture(Optional.ofNullable(state.teams().get(teamName)));
    }

    public CompletableFuture<Boolean> deleteTeam(String teamName) {
        Team removed = state.teams().remove(teamName);
        if (removed == null) {
            teamLogger.fine(String.format("Team %s not found for deletion", teamName));
            return CompletableFuture.completedFuture(false);
        }
        state.deleteTeamCascade(teamName);
        teamLogger.info(String.format("Team %s deleted", teamName));
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Long> getTeamUpdatedAt(String teamName) {
        Team team = state.teams().get(teamName);
        Long updatedAt = team != null ? team.getUpdatedAt() : null;
        return CompletableFuture.completedFuture(updatedAt != null ? updatedAt : 0L);
    }

    public CompletableFuture<Boolean> updateTeam(String teamName, String displayName, String desc, String prompt) {
        Team team = state.teams().get(teamName);
        if (team == null) {
            teamLogger.warning(String.format("Team %s not found for update", teamName));
            return CompletableFuture.completedFuture(false);
        }
        team.setDisplayName(displayName);
        team.setDesc(desc);
        team.setPrompt(prompt);
        team.setUpdatedAt(getCurrentTime());
        return CompletableFuture.completedFuture(true);
    }

    private long getCurrentTime() {
        return DatabaseEngine.getCurrentTime();
    }
}
