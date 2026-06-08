/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-backed per-team metadata helpers.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.runtime.metadata} in
 * {@code openjiuwen/agent_teams/runtime/metadata.py}.
 */
public final class TeamRuntimeMetadata {

    public static final String TEAMS_KEY = "teams";
    public static final String TEAM_DB_STATE_KEY = "db_state";
    public static final String TEAM_DB_STATE_PENDING_CREATE = "pending_create";
    public static final String TEAM_DB_STATE_CREATED = "created";
    public static final String TEAM_DB_STATE_CLEANED = "cleaned";

    private TeamRuntimeMetadata() {
    }

    /**
     * Minimal session contract required by the metadata helpers.
     */
    public interface SessionStateAccess {
        Object getState(String key);

        void updateState(Map<String, Object> state);
    }

    public static Map<String, Map<String, Object>> readTeamsBucket(SessionStateAccess session) {
        Object teams = session.getState(TEAMS_KEY);
        if (!(teams instanceof Map<?, ?> teamMap)) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, Map<String, Object>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : teamMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Map<?, ?> value)) {
                continue;
            }
            normalized.put(key, copyObjectMap(value));
        }
        return normalized;
    }

    public static Map<String, Object> readTeamNamespace(SessionStateAccess session, String teamName) {
        return readTeamsBucket(session).get(teamName);
    }

    public static List<String> readTeamNamesInSession(SessionStateAccess session) {
        return new ArrayList<>(readTeamsBucket(session).keySet());
    }

    public static void writeTeamNamespace(SessionStateAccess session, String teamName, Map<String, Object> payload) {
        LinkedHashMap<String, Map<String, Object>> teams = new LinkedHashMap<>(readTeamsBucket(session));
        teams.put(teamName, new LinkedHashMap<>(payload));
        session.updateState(Map.of(TEAMS_KEY, teams));
    }

    public static void mergeTeamNamespace(SessionStateAccess session, String teamName, Map<String, Object> partial) {
        LinkedHashMap<String, Map<String, Object>> teams = new LinkedHashMap<>(readTeamsBucket(session));
        LinkedHashMap<String, Object> bucket = new LinkedHashMap<>(teams.getOrDefault(teamName, Map.of()));
        bucket.putAll(partial);
        teams.put(teamName, bucket);
        session.updateState(Map.of(TEAMS_KEY, teams));
    }

    public static String readTeamDbState(SessionStateAccess session, String teamName) {
        Map<String, Object> bucket = readTeamNamespace(session, teamName);
        if (bucket == null) {
            return null;
        }
        Object value = bucket.get(TEAM_DB_STATE_KEY);
        return value instanceof String stringValue ? stringValue : null;
    }

    public static void mergeTeamDbState(SessionStateAccess session, String teamName, String state) {
        mergeTeamNamespace(session, teamName, Map.of(TEAM_DB_STATE_KEY, state));
    }

    public static boolean removeTeamNamespace(SessionStateAccess session, String teamName) {
        LinkedHashMap<String, Map<String, Object>> teams = new LinkedHashMap<>(readTeamsBucket(session));
        if (!teams.containsKey(teamName)) {
            return false;
        }
        teams.remove(teamName);
        session.updateState(Map.of(TEAMS_KEY, teams));
        return true;
    }

    private static LinkedHashMap<String, Object> copyObjectMap(Map<?, ?> value) {
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copied.put(key, entry.getValue());
            }
        }
        return copied;
    }
}
