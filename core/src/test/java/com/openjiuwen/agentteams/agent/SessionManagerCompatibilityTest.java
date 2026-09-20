/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.spawn.SpawnContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python 0.1.15 {@code test_session_manager.py}.
 * Validates SpawnContext sessionId lifecycle.
 */
class SessionManagerCompatibilityTest {

    private SpawnContext.SessionToken outerToken;

    @BeforeEach
    void cleanSessionId() {
        outerToken = SpawnContext.setSessionId(null);
    }

    @AfterEach
    void restoreSessionId() {
        SpawnContext.resetSessionId(outerToken);
    }

    @Test
    void bindSession_setsSpawnContext() {
        SpawnContext.SessionToken token = SpawnContext.setSessionId("sess-A");
        try {
            assertThat(SpawnContext.getSessionId()).isEqualTo("sess-A");
        } finally {
            SpawnContext.resetSessionId(token);
        }
    }

    @Test
    void releaseSession_resetsSpawnContext() {
        SpawnContext.SessionToken token = SpawnContext.setSessionId("sess-A");
        SpawnContext.resetSessionId(token);
        assertThat(SpawnContext.getSessionId()).isEmpty();
    }

    @Test
    void rebind_resetsPrior_thenRelease_returnsToEmpty() {
        SpawnContext.SessionToken token1 = SpawnContext.setSessionId("sess-A");
        SpawnContext.SessionToken token2 = SpawnContext.setSessionId("sess-B");
        assertThat(SpawnContext.getSessionId()).isEqualTo("sess-B");

        SpawnContext.resetSessionId(token2);
        // After reset, should go back to "sess-A" (token1's previous)
        assertThat(SpawnContext.getSessionId()).isEqualTo("sess-A");

        SpawnContext.resetSessionId(token1);
        assertThat(SpawnContext.getSessionId()).isEmpty();
    }

    @Test
    void sessionId_nullTreatedAsEmpty() {
        SpawnContext.SessionToken token = SpawnContext.setSessionId(null);
        try {
            assertThat(SpawnContext.getSessionId()).isEmpty();
        } finally {
            SpawnContext.resetSessionId(token);
        }
    }

    @Test
    void sessionId_defaultIsEmpty() {
        assertThat(SpawnContext.getSessionId()).isEmpty();
    }
}
