/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's state parity tests in
 * {@code tests/unit_tests/agent_teams/cli/test_state.py}.
 */
class TeamCliStatePythonParityTest {

    private TeamCliState makeState() {
        return new TeamCliState(new SpecRegistry(), "console");
    }

    @Test
    void initialStateHasNoActiveRoutingTarget() {
        TeamCliState state = makeState();

        assertNull(state.getActiveTeamName());
        assertNull(state.getActiveSessionId());
        assertNull(state.getPendingTeamName());
        assertNull(state.getPendingSessionId());
        assertTrue(state.getStreamHandles().isEmpty());
        assertTrue(state.getWatchBindings().isEmpty());
        assertTrue(state.getHistorySessionIds().isEmpty());
    }

    @Test
    void setActivePromotesPendingToActive() {
        TeamCliState state = makeState();
        state.setPending("alpha", "s1");

        assertEquals("alpha", state.getPendingTeamName());

        state.setActive("alpha", "s1");

        assertEquals("alpha", state.getActiveTeamName());
        assertEquals("s1", state.getActiveSessionId());
        assertNull(state.getPendingTeamName());
        assertNull(state.getPendingSessionId());
    }

    @Test
    void setActiveWithNullClearsRoutingTarget() {
        TeamCliState state = makeState();
        state.setActive("alpha", "s1");
        state.setActive(null, null);

        assertNull(state.getActiveTeamName());
        assertNull(state.getActiveSessionId());
    }

    @Test
    void rememberSessionRecordsDistinctPairs() {
        TeamCliState state = makeState();
        state.rememberSession("alpha", "s1");
        state.rememberSession("alpha", "s2");
        state.rememberSession("beta", "s1");

        assertEquals(List.of("s1", "s2"), state.knownSessions("alpha"));
        assertEquals(List.of("s1"), state.knownSessions("beta"));
        assertEquals(List.of(), state.knownSessions("missing"));
    }

    @Test
    void rememberSessionDedupesRepeats() {
        TeamCliState state = makeState();
        state.rememberSession("alpha", "s1");
        state.rememberSession("alpha", "s1");

        assertEquals(List.of("s1"), state.knownSessions("alpha"));
    }
}
