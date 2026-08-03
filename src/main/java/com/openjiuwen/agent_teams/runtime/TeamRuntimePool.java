/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Process-local pool of active team-agent runtimes keyed by team name.
 *
 * <p>Mirrors Python's {@code TeamRuntimePool} in
 * {@code openjiuwen/agent_teams/runtime/pool.py}.</p>
 */
public class TeamRuntimePool {

    private final Map<String, ActiveTeam> teams = new LinkedHashMap<>();

    public synchronized ActiveTeam get(String teamName) {
        return teams.get(teamName);
    }

    public synchronized boolean hasActive(String teamName) {
        return teams.containsKey(teamName);
    }

    public synchronized void add(ActiveTeam entry) {
        ActiveTeam activeTeam = Objects.requireNonNull(entry, "entry");
        teams.put(activeTeam.teamName(), activeTeam);
    }

    public synchronized ActiveTeam remove(String teamName) {
        return teams.remove(teamName);
    }

    public synchronized List<String> listTeamNames() {
        return new ArrayList<>(teams.keySet());
    }

    public synchronized List<ActiveTeam> teamsForSession(String sessionId) {
        return teams.values().stream()
                .filter(entry -> Objects.equals(entry.currentSessionId(), sessionId))
                .toList();
    }

    public synchronized List<ActiveTeamInfo> listAllInfo() {
        return teams.values().stream()
                .map(entry -> new ActiveTeamInfo(
                        entry.teamName(),
                        entry.currentSessionId(),
                        entry.state(),
                        entry.interactGate().isClosed()
                ))
                .toList();
    }
}
