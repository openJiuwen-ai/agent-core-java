/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.runner.Runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * System tests for the runner module.
 */
@Tag("system-test")
class RunnerModuleSystemTest extends SystemTestSupport {
    @Test
    @DisplayName("Runner executes registered remote ReActAgent by resource id")
    void testRunnerRunManagedRemoteAgentById() {
        assumeRemoteModelAvailable();

        String agentId = uniqueId("runner-react-agent");
        String sessionId = trackSessionId("runner-agent-session");
        var agent = newRemoteReActAgent(agentId,
                "Reply briefly in English. If the user asks for an exact token, return that token.");
        registerAgent(agent);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) Runner.runAgent(agentId,
                Map.of("query", "Reply with the exact token RUNNER_READY.", "conversation_id", sessionId), null, null);

        assertEquals("answer", result.get("result_type"));
        assertTrue(containsIgnoreCase(flattenText(result), "RUNNER_READY"),
                () -> "Expected RUNNER_READY in output but got: " + flattenText(result));
    }
}
