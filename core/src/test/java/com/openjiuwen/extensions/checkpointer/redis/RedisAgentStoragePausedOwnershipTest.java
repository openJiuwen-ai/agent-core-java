/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.session.checkpointer.step.StepCursor;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.extensions.checkpointer.redis.storage.AgentStorage;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for by-id paused lookups on the Redis backend.
 *
 * <p>The by-id entry points locate blobs with a {@code sessionId + ":"} prefix,
 * which also matches colon-delimited child sessions. Every fixture below stores
 * the paused evidence of {@code chat:sub} only, so an unscoped lookup for
 * {@code chat} would previously read or clear the child session's state.</p>
 */
class RedisAgentStoragePausedOwnershipTest {

    @Test
    void lastPausedCursorIgnoresChildSessionBlobs() {
        RedisCheckpointerStorageTest.FakeRedisClient client = new RedisCheckpointerStorageTest.FakeRedisClient();
        AgentStorage storage = storage(client);
        writePausedBlob(client, "chat:sub:agent:child-agent",
                new StepCursor(3L, "PHASE", "pause", StepStatus.PAUSED, 3L,
                        "", "chat:sub", "child-agent"));

        assertNull(storage.lastPausedCursor("chat").join());

        StepCursor childPaused = storage.lastPausedCursor("chat:sub").join();
        assertNotNull(childPaused);
        assertEquals("chat:sub", childPaused.getSessionId());
        assertEquals("child-agent", childPaused.getAgentId());
    }

    @Test
    void resumePausedBySessionIdDoesNotRewriteChildSessionBlobs() {
        RedisCheckpointerStorageTest.FakeRedisClient client = new RedisCheckpointerStorageTest.FakeRedisClient();
        AgentStorage storage = storage(client);
        writePausedBlob(client, "chat:agent:parent-agent",
                new StepCursor(2L, "PHASE", "pause", StepStatus.PAUSED, 2L,
                        "", "chat", "parent-agent"));
        writePausedBlob(client, "chat:sub:agent:child-agent",
                new StepCursor(3L, "PHASE", "pause", StepStatus.PAUSED, 3L,
                        "", "chat:sub", "child-agent"));

        storage.resumePausedBySessionId("chat").join();

        assertNull(storage.lastPausedCursor("chat").join());
        StepCursor childPaused = storage.lastPausedCursor("chat:sub").join();
        assertNotNull(childPaused);
        assertEquals(3L, childPaused.getSeq());
    }

    @Test
    void resumePausedBySessionIdRejectsSessionThatOnlyHasChildEvidence() {
        RedisCheckpointerStorageTest.FakeRedisClient client = new RedisCheckpointerStorageTest.FakeRedisClient();
        AgentStorage storage = storage(client);
        writePausedBlob(client, "chat:sub:agent:child-agent",
                new StepCursor(3L, "PHASE", "pause", StepStatus.PAUSED, 3L,
                        "", "chat:sub", "child-agent"));

        CompletionException error = assertThrows(CompletionException.class,
                () -> storage.resumePausedBySessionId("chat").join());

        assertTrue(String.valueOf(error.getCause().getMessage()).contains("No paused session found"));
        assertNotNull(storage.lastPausedCursor("chat:sub").join());
    }

    private static AgentStorage storage(RedisCheckpointerStorageTest.FakeRedisClient client) {
        return new AgentStorage(new RedisStore(client), null);
    }

    private static void writePausedBlob(RedisCheckpointerStorageTest.FakeRedisClient client, String ownerKey,
            StepCursor cursor) {
        AgentStateCollection state = new AgentStateCollection();
        state.update(Map.of(StepCursor.PAUSED_STATE_KEY, cursor.toMap()));
        Serializer.TypedBytes bytes = Serializer.create("java").dumpsTyped(state.getState());
        client.set(ownerKey + ":agent_state_blobs_dump_type", bytes.type());
        client.set(ownerKey + ":agent_state_blobs", bytes.data());
    }
}
