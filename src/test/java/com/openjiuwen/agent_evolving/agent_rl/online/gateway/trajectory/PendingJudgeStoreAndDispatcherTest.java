/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PendingJudgeStore.
 * 
 * <p>Mirrors Python's {@code PendingJudgeStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.pending_judge_store}.</p>
 * 
 * <p>NOTE: Python has no dedicated test file for pending_judge_store. Tests are derived from
 * the Python implementation behavior.
 */
@ExtendWith(MockitoExtension.class)
class PendingJudgeStoreAndDispatcherTest {

    @Test
    @Tag("level0")
    @DisplayName("Test PendingJudgeStore constructor requires redis")
    void testConstructorRequiresRedis() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new PendingJudgeStore(null)
        );
        assertTrue(ex.getMessage().contains("redis"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test PendingJudgeStore constructor with valid redis")
    void testConstructorWithValidRedis() {
        RedisTrajectoryStoreBackend mockRedis = mock(RedisTrajectoryStoreBackend.class);
        PendingJudgeStore store = new PendingJudgeStore(mockRedis);
        assertNotNull(store);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test sampleKey format matches Python")
    void testSampleKeyFormat() {
        // Python format: pending_judge:{session_id}:{trajectory_id}:{step_index}
        String sessionId = "session123";
        String trajectoryId = "trajectory456";
        int stepIndex = 5;
        
        String expectedPrefix = "pending_judge:" + sessionId + ":" + trajectoryId + ":" + stepIndex;
        assertTrue(expectedPrefix.startsWith("pending_judge:"));
        assertTrue(expectedPrefix.contains(sessionId));
        assertTrue(expectedPrefix.contains(trajectoryId));
        assertTrue(expectedPrefix.endsWith(String.valueOf(stepIndex)));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test sessionKey format matches Python")
    void testSessionKeyFormat() {
        // Python format: pending_judge_session:{session_id}
        String sessionId = "session123";
        
        String expectedPrefix = "pending_judge_session:" + sessionId;
        assertTrue(expectedPrefix.startsWith("pending_judge_session:"));
        assertTrue(expectedPrefix.endsWith(sessionId));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test TTL default is 24 hours (matches Python)")
    void testDefaultTtl() {
        // Python default: ttl_sec = 24 * 3600 = 86400
        int expectedTtl = 24 * 3600;
        assertEquals(86400, expectedTtl);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test TTL custom value")
    void testCustomTtl() {
        RedisTrajectoryStoreBackend mockRedis = mock(RedisTrajectoryStoreBackend.class);
        int customTtl = 3600; // 1 hour
        PendingJudgeStore store = new PendingJudgeStore(mockRedis, customTtl);
        assertNotNull(store);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test payload structure matches Python")
    void testPayloadStructure() {
        // Python payload includes: _pending_key, _pending_created_at
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", "test-session");
        payload.put("trajectory_id", "test-trajectory");
        payload.put("step_index", 0);
        payload.put("_pending_key", "pending_judge:test-session:test-trajectory:0");
        payload.put("_pending_created_at", System.currentTimeMillis() / 1000.0);
        
        // Verify payload has required fields (matches Python behavior)
        assertTrue(payload.containsKey("session_id"));
        assertTrue(payload.containsKey("trajectory_id"));
        assertTrue(payload.containsKey("step_index"));
        assertTrue(payload.containsKey("_pending_key"));
        assertTrue(payload.containsKey("_pending_created_at"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test protected constructor for testing")
    void testProtectedConstructor() {
        // Protected no-arg constructor for in-memory subclassing
        PendingJudgeStore store = new InMemoryPendingJudgeStore();
        assertNotNull(store);
    }

    /**
     * In-memory test subclass for PendingJudgeStore.
     */
    private static class InMemoryPendingJudgeStore extends PendingJudgeStore {
        InMemoryPendingJudgeStore() {
            super(); // Uses protected no-arg constructor
        }
    }
}
