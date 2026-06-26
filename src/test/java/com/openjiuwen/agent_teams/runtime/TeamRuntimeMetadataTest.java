/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.runtime.test_metadata} in
 * {@code tests/unit_tests/agent_teams/runtime/test_metadata.py}.
 */
class TeamRuntimeMetadataTest {

    @Test
    void readTeamsBucketReturnsEmptyMapForMissingOrInvalidState() {
        FakeSession session = new FakeSession();
        assertTrue(TeamRuntimeMetadata.readTeamsBucket(session).isEmpty());

        session.rawState.put(TeamRuntimeMetadata.TEAMS_KEY, "not-a-map");
        assertTrue(TeamRuntimeMetadata.readTeamsBucket(session).isEmpty());
    }

    @Test
    void writeAndMergeNamespacePreserveOtherTeams() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", "v1", "lifecycle", "running"));
        TeamRuntimeMetadata.writeTeamNamespace(session, "beta", Map.of("spec", "v2"));

        TeamRuntimeMetadata.mergeTeamNamespace(session, "alpha", Map.of("db_state", "created"));

        assertEquals(Map.of("spec", "v1", "lifecycle", "running", "db_state", "created"),
                TeamRuntimeMetadata.readTeamNamespace(session, "alpha"));
        assertEquals(Map.of("spec", "v2"), TeamRuntimeMetadata.readTeamNamespace(session, "beta"));
        assertEquals(2, TeamRuntimeMetadata.readTeamNamesInSession(session).size());
    }

    @Test
    void readAndMergeDbStateFollowPythonContract() {
        FakeSession session = new FakeSession();

        assertNull(TeamRuntimeMetadata.readTeamDbState(session, "alpha"));

        TeamRuntimeMetadata.mergeTeamDbState(session, "alpha", TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE);
        assertEquals(TeamRuntimeMetadata.TEAM_DB_STATE_PENDING_CREATE,
                TeamRuntimeMetadata.readTeamDbState(session, "alpha"));

        TeamRuntimeMetadata.mergeTeamDbState(session, "alpha", TeamRuntimeMetadata.TEAM_DB_STATE_CREATED);
        assertEquals(TeamRuntimeMetadata.TEAM_DB_STATE_CREATED,
                TeamRuntimeMetadata.readTeamDbState(session, "alpha"));
    }

    @Test
    void removeTeamNamespaceReturnsBooleanOutcome() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", "v1"));

        assertTrue(TeamRuntimeMetadata.removeTeamNamespace(session, "alpha"));
        assertNull(TeamRuntimeMetadata.readTeamNamespace(session, "alpha"));
        assertFalse(TeamRuntimeMetadata.removeTeamNamespace(session, "alpha"));
    }

    @Test
    void readTeamsBucketReturnsEmptyDictWhenAbsent() {
        FakeSession session = new FakeSession();
        assertEquals(Map.of(), TeamRuntimeMetadata.readTeamsBucket(session));
    }

    @Test
    void writeTeamNamespaceCreatesBucket() {
        FakeSession session = new FakeSession();

        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of("team_name", "alpha")));

        assertTrue(session.rawState.containsKey(TeamRuntimeMetadata.TEAMS_KEY));
        assertEquals(
                Map.of("alpha", Map.of("spec", Map.of("team_name", "alpha"))),
                session.rawState.get(TeamRuntimeMetadata.TEAMS_KEY)
        );
    }

    @Test
    void readTeamNamespaceReturnsBucketWhenPresent() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of("team_name", "alpha")));

        assertEquals(
                Map.of("spec", Map.of("team_name", "alpha")),
                TeamRuntimeMetadata.readTeamNamespace(session, "alpha")
        );
    }

    @Test
    void readTeamNamespaceReturnsNullWhenMissing() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of()));

        assertNull(TeamRuntimeMetadata.readTeamNamespace(session, "beta"));
    }

    @Test
    void mergeTeamNamespacePreservesExistingKeys() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of("team_name", "alpha")));

        TeamRuntimeMetadata.mergeTeamNamespace(session, "alpha", Map.of("lifecycle", "paused"));

        assertEquals(
                Map.of("spec", Map.of("team_name", "alpha"), "lifecycle", "paused"),
                TeamRuntimeMetadata.readTeamNamespace(session, "alpha")
        );
    }

    @Test
    void mergeTeamNamespaceCreatesBucketIfAbsent() {
        FakeSession session = new FakeSession();

        TeamRuntimeMetadata.mergeTeamNamespace(session, "alpha", Map.of("lifecycle", "running"));

        assertEquals(Map.of("lifecycle", "running"), TeamRuntimeMetadata.readTeamNamespace(session, "alpha"));
    }

    @Test
    void mergeTeamDbStatePreservesExistingBucket() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of("team_name", "alpha")));

        TeamRuntimeMetadata.mergeTeamDbState(session, "alpha", TeamRuntimeMetadata.TEAM_DB_STATE_CREATED);

        assertEquals(
                Map.of(
                        "spec", Map.of("team_name", "alpha"),
                        TeamRuntimeMetadata.TEAM_DB_STATE_KEY, TeamRuntimeMetadata.TEAM_DB_STATE_CREATED
                ),
                TeamRuntimeMetadata.readTeamNamespace(session, "alpha")
        );
        assertEquals(TeamRuntimeMetadata.TEAM_DB_STATE_CREATED, TeamRuntimeMetadata.readTeamDbState(session, "alpha"));
    }

    @Test
    void readTeamDbStateReturnsNullWhenAbsentOrNotString() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of(TeamRuntimeMetadata.TEAM_DB_STATE_KEY, 1));

        assertNull(TeamRuntimeMetadata.readTeamDbState(session, "alpha"));
        assertNull(TeamRuntimeMetadata.readTeamDbState(session, "beta"));
    }

    @Test
    void multiTeamBucketsAreIndependent() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of("team_name", "alpha")));
        TeamRuntimeMetadata.writeTeamNamespace(session, "beta", Map.of("spec", Map.of("team_name", "beta")));

        assertEquals(List.of("alpha", "beta"), TeamRuntimeMetadata.readTeamNamesInSession(session));
        assertFalse(TeamRuntimeMetadata.readTeamNamespace(session, "alpha")
                .equals(TeamRuntimeMetadata.readTeamNamespace(session, "beta")));
    }

    @Test
    void mergeOneTeamDoesNotTouchAnother() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of("team_name", "alpha")));
        TeamRuntimeMetadata.writeTeamNamespace(session, "beta", Map.of("spec", Map.of("team_name", "beta")));

        TeamRuntimeMetadata.mergeTeamNamespace(session, "alpha", Map.of("lifecycle", "paused"));

        assertEquals(
                Map.of("spec", Map.of("team_name", "beta")),
                TeamRuntimeMetadata.readTeamNamespace(session, "beta")
        );
    }

    @Test
    void removeTeamNamespaceDropsBucket() {
        FakeSession session = new FakeSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "alpha", Map.of("spec", Map.of()));
        TeamRuntimeMetadata.writeTeamNamespace(session, "beta", Map.of("spec", Map.of()));

        assertTrue(TeamRuntimeMetadata.removeTeamNamespace(session, "alpha"));

        assertEquals(List.of("beta"), TeamRuntimeMetadata.readTeamNamesInSession(session));
    }

    @Test
    void removeTeamNamespaceReturnsFalseWhenAbsent() {
        FakeSession session = new FakeSession();
        assertFalse(TeamRuntimeMetadata.removeTeamNamespace(session, "alpha"));
    }

    private static final class FakeSession implements TeamRuntimeMetadata.SessionStateAccess {
        private final LinkedHashMap<String, Object> rawState = new LinkedHashMap<>();

        @Override
        public Object getState(String key) {
            return rawState.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            rawState.putAll(state);
        }
    }
}
