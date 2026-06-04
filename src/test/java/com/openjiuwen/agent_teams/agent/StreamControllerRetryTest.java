/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_team_agent_retry.py}.
 */
class StreamControllerRetryTest {

    @Test
    void testRetryOn181001ThenSucceed() {
        List<List<Object>> rounds = new ArrayList<>();
        rounds.add(List.of(failedChunk(181001, "model call failed, reason: timeout")));
        rounds.add(List.of(failedChunk(181001, "model call failed, reason: timeout")));
        rounds.add(List.of(failedChunk(181001, "model call failed, reason: timeout")));
        rounds.add(List.of(answerChunk("final answer")));
        ExecutionHarness harness = harness(rounds);

        harness.controller.executeRound("initial query");

        assertEquals(4, harness.callInputs.size());
        assertEquals(Map.of("query", "initial query"), harness.callInputs.get(0));
        assertEquals(Map.of("query", StreamController.RETRY_QUERY), harness.callInputs.get(1));
        assertEquals(Map.of("query", StreamController.RETRY_QUERY), harness.callInputs.get(2));
        assertEquals(Map.of("query", StreamController.RETRY_QUERY), harness.callInputs.get(3));
        List<Object> chunks = harness.controller.drainStreamQueue();
        assertEquals(1, chunks.size());
        assertEquals("final answer", ((Map<?, ?>) ((Map<?, ?>) chunks.get(0)).get("payload")).get("output"));
        assertTrue(harness.executionLog.contains(ExecutionStatus.COMPLETED));
        assertFalse(harness.executionLog.contains(ExecutionStatus.FAILED));
    }

    @Test
    void testRetriesExhaustedRaises() {
        List<List<Object>> rounds = new ArrayList<>();
        for (int i = 0; i < StreamController.MAX_RETRY_ATTEMPTS + 1; i++) {
            rounds.add(List.of(failedChunk(181001, "timeout #" + i)));
        }
        ExecutionHarness harness = harness(rounds);

        BaseError error = assertThrows(BaseError.class, () -> harness.controller.executeRound("initial query"));

        assertEquals(StatusCode.AGENT_TEAM_EXECUTION_ERROR, error.getStatus());
        assertTrue(error.toString().contains("181001"));
        assertEquals(StreamController.MAX_RETRY_ATTEMPTS + 1, harness.callInputs.size());
        assertTrue(harness.executionLog.contains(ExecutionStatus.FAILED));
        assertFalse(harness.executionLog.contains(ExecutionStatus.COMPLETED));
    }

    @Test
    void testNonRetryableCodeRaisesImmediately() {
        ExecutionHarness harness = harness(List.of(
                List.of(failedChunk(182012, "tool execution error, card=X, reason=Y"))
        ));

        BaseError error = assertThrows(BaseError.class, () -> harness.controller.executeRound("initial query"));

        assertTrue(error.toString().contains("182012"));
        assertEquals(1, harness.callInputs.size());
        assertTrue(harness.executionLog.contains(ExecutionStatus.FAILED));
    }

    @Test
    void testMissingCodePrefixIsNonRetryable() {
        ExecutionHarness harness = harness(List.of(
                List.of(failedChunkRaw("unexpected error without code"))
        ));

        assertThrows(BaseError.class, () -> harness.controller.executeRound("initial query"));

        assertEquals(1, harness.callInputs.size());
        assertTrue(harness.executionLog.contains(ExecutionStatus.FAILED));
    }

    @Test
    void testTrailingFramesAfterErrorAreSwallowed() {
        ExecutionHarness harness = harness(List.of(
                List.of(
                        failedChunk(181001, "model call failed, reason: boom"),
                        answerChunk("should NOT reach downstream"),
                        answerChunk("also should NOT reach downstream")
                ),
                List.of(answerChunk("final"))
        ));

        harness.controller.executeRound("initial query");

        assertEquals(2, harness.callInputs.size());
        List<Object> chunks = harness.controller.drainStreamQueue();
        assertEquals(1, chunks.size());
        assertEquals("final", ((Map<?, ?>) ((Map<?, ?>) chunks.get(0)).get("payload")).get("output"));
    }

    @Test
    void testDetectTaskFailedParsesCodeAndText() {
        Object chunk = failedChunk(181001, "model call failed, reason: timeout");

        StreamController.TaskFailed result = StreamController.detectTaskFailed(chunk);

        assertNotNull(result);
        assertEquals(181001, result.code());
        assertEquals("[181001] model call failed, reason: timeout", result.text());
    }

    @Test
    void testDetectTaskFailedReturnsNoneForNormalChunk() {
        assertNull(StreamController.detectTaskFailed(answerChunk("hello")));
    }

    @Test
    void testDetectTaskFailedNoneCodeWhenNoPrefix() {
        StreamController.TaskFailed result = StreamController.detectTaskFailed(failedChunkRaw("no prefix here"));

        assertNotNull(result);
        assertNull(result.code());
        assertEquals("no prefix here", result.text());
    }

    @Test
    void testDetectTaskFailedHandlesEmptyData() {
        Object chunk = Map.of(
                "payload", Map.of(
                        "type", "task_failed",
                        "data", List.of(),
                        "metadata", Map.of()
                )
        );

        StreamController.TaskFailed result = StreamController.detectTaskFailed(chunk);

        assertEquals(new StreamController.TaskFailed(null, ""), result);
    }

    private static ExecutionHarness harness(List<List<Object>> rounds) {
        List<Map<String, Object>> callInputs = new ArrayList<>();
        List<ExecutionStatus> executionLog = new ArrayList<>();
        int[] index = {0};
        StreamController controller = new StreamController("team", inputs -> {
            callInputs.add(Map.copyOf(inputs));
            if (index[0] >= rounds.size()) {
                return List.of();
            }
            return rounds.get(index[0]++);
        }, executionLog::add);
        return new ExecutionHarness(controller, callInputs, executionLog);
    }

    private static Object failedChunk(int code, String message) {
        return failedChunkRaw("[" + code + "] " + message);
    }

    private static Object failedChunkRaw(String text) {
        return Map.of(
                "payload", Map.of(
                        "type", "task_failed",
                        "data", List.of(Map.of("text", text)),
                        "metadata", Map.of("task_id", "t1")
                )
        );
    }

    private static Object answerChunk(String text) {
        return Map.of(
                "type", "answer",
                "payload", Map.of("output", text, "result_type", "answer")
        );
    }

    private record ExecutionHarness(
            StreamController controller,
            List<Map<String, Object>> callInputs,
            List<ExecutionStatus> executionLog
    ) {
    }
}
