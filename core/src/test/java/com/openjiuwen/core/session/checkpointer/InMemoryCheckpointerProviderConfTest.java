/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

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
    private static final class ConfTestSession extends BaseSession {
        private final String sessionId;
        private final AgentStateCollection state = new AgentStateCollection();

        ConfTestSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public SessionConfigAccess config() {
            return new Config();
        }

        @Override
        public SessionStateAccess state() {
            return state;
        }

        @Override
        public String sessionId() {
            return sessionId;
        }
    }
}
