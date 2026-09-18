/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full mutable state held by a single TeamCli instance.
 *
 * <p>Mirrors Python's {@code TeamCliState} in
 * {@code openjiuwen/agent_teams/cli/state.py}.</p>
 */
public class TeamCliState {

    private final SpecRegistry specRegistry;
    private final Object console;
    private String activeTeamName;
    private String activeSessionId;
    private String pendingTeamName;
    private String pendingSessionId;
    private final Map<String, StreamHandle> streamHandles;
    private final Map<WatchBindingKey, WatchBinding> watchBindings;
    private final Map<String, Set<String>> historySessionIds;

    public TeamCliState(SpecRegistry specRegistry, Object console) {
        this.specRegistry = specRegistry;
        this.console = console;
        this.streamHandles = new LinkedHashMap<>();
        this.watchBindings = new LinkedHashMap<>();
        this.historySessionIds = new LinkedHashMap<>();
    }

    public void rememberSession(String teamName, String sessionId) {
        historySessionIds.computeIfAbsent(teamName, ignored -> new LinkedHashSet<>()).add(sessionId);
    }

    public List<String> knownSessions(String teamName) {
        Set<String> sessions = historySessionIds.get(teamName);
        if (sessions == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(sessions);
        result.sort(String::compareTo);
        return result;
    }

    public void setActive(String teamName, String sessionId) {
        this.activeTeamName = teamName;
        this.activeSessionId = sessionId;
        this.pendingTeamName = null;
        this.pendingSessionId = null;
    }

    public void setPending(String teamName, String sessionId) {
        this.pendingTeamName = teamName;
        this.pendingSessionId = sessionId;
    }

    public SpecRegistry getSpecRegistry() {
        return specRegistry;
    }

    public Object getConsole() {
        return console;
    }

    public String getActiveTeamName() {
        return activeTeamName;
    }

    public void setActiveTeamName(String activeTeamName) {
        this.activeTeamName = activeTeamName;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }

    public void setActiveSessionId(String activeSessionId) {
        this.activeSessionId = activeSessionId;
    }

    public String getPendingTeamName() {
        return pendingTeamName;
    }

    public void setPendingTeamName(String pendingTeamName) {
        this.pendingTeamName = pendingTeamName;
    }

    public String getPendingSessionId() {
        return pendingSessionId;
    }

    public void setPendingSessionId(String pendingSessionId) {
        this.pendingSessionId = pendingSessionId;
    }

    public Map<String, StreamHandle> getStreamHandles() {
        return streamHandles;
    }

    public Map<WatchBindingKey, WatchBinding> getWatchBindings() {
        return watchBindings;
    }

    public Map<String, Set<String>> getHistorySessionIds() {
        return historySessionIds;
    }
}
