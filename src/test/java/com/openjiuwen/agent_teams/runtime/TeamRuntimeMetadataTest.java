/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
