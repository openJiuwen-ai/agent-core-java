/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link InMemoryCheckpointerProvider} configuration parsing: max-sessions,
 * ttl-seconds, default-instance fallback, and string-typed values.
 */
class InMemoryCheckpointerProviderConfTest {

    @Test
    void emptyConfReturnsSharedDefaultInstance() {
        InMemoryCheckpointerProvider provider = new InMemoryCheckpointerProvider();

        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), provider.create(Map.of()));
        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), provider.create(null));
    }

    @Test
    void maxSessionsConfCreatesDedicatedInstance() {
        InMemoryCheckpointerProvider provider = new InMemoryCheckpointerProvider();

        Checkpointer checkpointer = provider.create(Map.of(InMemoryCheckpointerProvider.CONF_MAX_SESSIONS, 5));
        assertNotNull(checkpointer);
        assertNotEquals(CheckpointerFactory.defaultInMemoryCheckpointer(), checkpointer);

        for (int i = 1; i <= 7; i++) {
            checkpointer.preAgentExecute(new ConfTestSession("session-" + i), null);
            checkpointer.postAgentExecute(new ConfTestSession("session-" + i));
        }
        assertFalse(checkpointer.sessionExists("session-1"));
        assertFalse(checkpointer.sessionExists("session-2"));
        assertTrue(checkpointer.sessionExists("session-6"));
        assertTrue(checkpointer.sessionExists("session-7"));
    }

    @Test
    void stringTypedConfValuesAreAccepted() {
        InMemoryCheckpointerProvider provider = new InMemoryCheckpointerProvider();
        Map<String, Object> conf = new HashMap<>();
        conf.put(InMemoryCheckpointerProvider.CONF_MAX_SESSIONS, "5");
        conf.put(InMemoryCheckpointerProvider.CONF_TTL_SECONDS, "86400");

        Checkpointer checkpointer = provider.create(conf);
        assertNotNull(checkpointer);
        assertNotEquals(CheckpointerFactory.defaultInMemoryCheckpointer(), checkpointer);
    }

    @Test
    void invalidConfValuesFallBackToDefaults() {
        InMemoryCheckpointerProvider provider = new InMemoryCheckpointerProvider();
        Map<String, Object> conf = new HashMap<>();
        conf.put(InMemoryCheckpointerProvider.CONF_MAX_SESSIONS, "not-a-number");

        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), provider.create(conf));
    }

    @Test
    void defaultInstanceKeepsDefaultCapacity() {
        assertEquals(1000, InMemoryCheckpointer.DEFAULT_MAX_SESSIONS);
    }

    /**
     * Minimal agent-flavoured session stub for provider-level tests.
     */
    private static final class ConfTestSession extends com.openjiuwen.core.session.BaseSession {
        private final String sessionId;
        private final com.openjiuwen.core.session.state.AgentStateCollection state =
                new com.openjiuwen.core.session.state.AgentStateCollection();

        ConfTestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public com.openjiuwen.core.session.config.SessionConfigAccess config() {
            return new com.openjiuwen.core.session.config.Config();
        }

        @Override
        public com.openjiuwen.core.session.state.SessionStateAccess state() {
            return state;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }
    }
}
