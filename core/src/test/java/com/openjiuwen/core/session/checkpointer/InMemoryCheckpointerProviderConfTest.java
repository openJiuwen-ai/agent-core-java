/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.multitenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void clearTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void restoreTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

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

        checkpointer.preAgentExecute(new InMemoryCheckpointerTestAgentSession("session-1"), null);
        checkpointer.postAgentExecute(new InMemoryCheckpointerTestAgentSession("session-1"));
        for (int i = 2; i <= 7; i++) {
            checkpointer.preAgentExecute(new InMemoryCheckpointerTestAgentSession("session-" + i), null);
            checkpointer.postAgentExecute(new InMemoryCheckpointerTestAgentSession("session-" + i));
        }
        assertFalse(checkpointer.sessionExists("session-1"));
        assertFalse(checkpointer.sessionExists("session-2"));
        assertTrue(checkpointer.sessionExists("session-6"));
        assertTrue(checkpointer.sessionExists("session-7"));
    }

    @Test
    void ttlSecondsConfCreatesDedicatedInstance() {
        InMemoryCheckpointerProvider provider = new InMemoryCheckpointerProvider();

        Checkpointer checkpointer = provider.create(Map.of(InMemoryCheckpointerProvider.CONF_TTL_SECONDS, 1));
        checkpointer.preAgentExecute(new InMemoryCheckpointerTestAgentSession("session-ttl"), null);
        checkpointer.postAgentExecute(new InMemoryCheckpointerTestAgentSession("session-ttl"));
        assertTrue(checkpointer.sessionExists("session-ttl"));

        try {
            Thread.sleep(1100L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        checkpointer.preAgentExecute(new InMemoryCheckpointerTestAgentSession("session-other"), null);
        checkpointer.postAgentExecute(new InMemoryCheckpointerTestAgentSession("session-other"));
        ((InMemoryCheckpointer) checkpointer).cleanUpRegistry();
        assertFalse(checkpointer.sessionExists("session-ttl"));
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
}