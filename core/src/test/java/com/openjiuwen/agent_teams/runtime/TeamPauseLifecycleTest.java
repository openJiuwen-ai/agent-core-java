/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's pause lifecycle tests in
 * {@code tests/unit_tests/agent_teams/runtime/test_pause_lifecycle.py}.
 */
class TeamPauseLifecycleTest {

    @Test
    void pausedToRestartingTransitionIsValid() {
        assertTrue(StatusTransitions.isValidTransition(
                MemberStatus.PAUSED,
                MemberStatus.RESTARTING,
                StatusTransitions.MEMBER_TRANSITIONS
        ));
    }

    @Test
    void pausedToReadyTransitionIsValid() {
        assertTrue(StatusTransitions.isValidTransition(
                MemberStatus.PAUSED,
                MemberStatus.READY,
                StatusTransitions.MEMBER_TRANSITIONS
        ));
    }

    @Test
    void readyAndBusyCanEnterPaused() {
        assertTrue(StatusTransitions.isValidTransition(
                MemberStatus.READY,
                MemberStatus.PAUSED,
                StatusTransitions.MEMBER_TRANSITIONS
        ));
        assertTrue(StatusTransitions.isValidTransition(
                MemberStatus.BUSY,
                MemberStatus.PAUSED,
                StatusTransitions.MEMBER_TRANSITIONS
        ));
    }

    @Test
    void pausedCannotJumpBackToBusyDirectly() {
        assertFalse(StatusTransitions.isValidTransition(
                MemberStatus.PAUSED,
                MemberStatus.BUSY,
                StatusTransitions.MEMBER_TRANSITIONS
        ));
    }

    @Test
    void lifecycleHintRoundTripsThroughTeamNamespace() {
        FakeSession session = new FakeSession();

        TeamRuntimeMetadata.writeTeamNamespace(session, "t1", Map.of("spec", Map.of("team_name", "t1")));
        TeamRuntimeMetadata.mergeTeamNamespace(session, "t1", Map.of("lifecycle", "paused"));

        assertEquals("paused", TeamRuntimeMetadata.readTeamNamespace(session, "t1").get("lifecycle"));
    }

    @Test
    void lifecycleHintOverridesPreviousValue() {
        FakeSession session = new FakeSession();

        TeamRuntimeMetadata.writeTeamNamespace(session, "t1", Map.of("lifecycle", "running"));
        TeamRuntimeMetadata.mergeTeamNamespace(session, "t1", Map.of("lifecycle", "paused"));

        assertEquals("paused", TeamRuntimeMetadata.readTeamNamespace(session, "t1").get("lifecycle"));
    }

    private static final class FakeSession implements TeamRuntimeMetadata.SessionStateAccess {

        private final LinkedHashMap<String, Object> state = new LinkedHashMap<>();

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }
    }
}
