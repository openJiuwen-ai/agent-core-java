/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test integration runner functionality.
 *
 * <p>Mirrors the Redis-backed recovery path from Python's
 * {@code tests_unit_tests.extensions.checkpointer.test_integration_runner}.
 */
class TestIntegrationRunner {

    @Nested
    class TestRunnerOperations {

        @Test
        void testRunIntegration() {
            RedisCheckpointer checkpointer = checkpointer();
            AgentSession session = session("session-1", "agent-1", checkpointer);

            assertDoesNotThrow(() -> checkpointer.preAgentExecute(session, null));
            assertFalse(checkpointer.sessionExists("session-1"));
        }

        @Test
        void testRunWithCheckpointer() {
            RedisCheckpointer checkpointer = checkpointer();
            AgentSession interrupted = session("session-1", "agent-1", checkpointer);
            interrupted.state().update(Map.of("step", "waiting"));
            checkpointer.interruptAgentExecute(interrupted);

            AgentSession resumed = session("session-1", "agent-1", checkpointer);
            checkpointer.preAgentExecute(resumed, "continue");

            assertEquals("waiting", resumed.state().get("step"));
            assertEquals(List.of("continue"), resumed.state().get(Constant.INTERACTIVE_INPUT));
        }
    }

    private RedisCheckpointer checkpointer() {
        return new RedisCheckpointer(new RedisStore(new TestAgentStorage.FakeRedisClient()), null);
    }

    private AgentSession session(String sessionId, String agentId, RedisCheckpointer checkpointer) {
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike(agentId, "agent", "invoke"));
        return new AgentSession(sessionId, config, checkpointer);
    }
}
