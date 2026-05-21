/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_evolving.trajectory.test_store.
 * Tests for TrajectoryStore implementations.
 */
class TrajectoryStoreTest {

    private TrajectoryStep makeStep(String kind, Object detail, Object error, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind(kind)
                .error(error)
                .inputs(detail)
                .meta(meta != null ? meta : new HashMap<>())
                .build();
    }

    private TrajectoryStep makeLLMStep(String operatorId, List<Map<String, Object>> messages) {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("gpt-4")
                .messages(messages != null ? messages : List.of(Map.of("role", "user", "content", "hello")))
                .build();
        Map<String, Object> meta = new HashMap<>();
        meta.put("operator_id", operatorId);
        return makeStep("llm", detail, null, meta);
    }

    private TrajectoryStep makeToolStep(String toolName, Object callArgs, Object callResult) {
        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName(toolName)
                .callArgs(callArgs)
                .callResult(callResult)
                .build();
        Map<String, Object> meta = new HashMap<>();
        meta.put("operator_id", toolName);
        return makeStep("tool", detail, null, meta);
    }

    private Trajectory makeTrajectory(String execId, String sessionId, String source, String caseId, List<TrajectoryStep> steps) {
        return Trajectory.builder()
                .executionId(execId)
                .sessionId(sessionId)
                .source(source != null ? source : "offline")
                .caseId(caseId)
                .steps(steps != null ? steps : List.of(makeStep("llm", null, null, null)))
                .build();
    }

    @Test
    void inMemoryStoreSaveAndLoad() {
        com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore store = 
            new com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore();
        Trajectory traj = makeTrajectory("exec1", "session1", "offline", "case1", null);

        store.save(traj);
        Trajectory loaded = store.load("exec1");

        assertNotNull(loaded);
        assertEquals("exec1", loaded.getExecutionId());
        assertEquals("case1", loaded.getCaseId());
    }

    @Test
    void inMemoryStoreLoadNonexistent() {
        com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore store = 
            new com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore();

        Trajectory result = store.load("nonexistent");

        assertNull(result);
    }

    @Test
    void inMemoryStoreQueryAll() {
        com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore store = 
            new com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore();
        store.save(makeTrajectory("exec1", "session1", null, null, null));
        store.save(makeTrajectory("exec2", "session2", null, null, null));

        List<Trajectory> results = store.query();

        assertEquals(2, results.size());
    }

    @Test
    void inMemoryStoreOverwriteExisting() {
        com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore store = 
            new com.openjiuwen.agent_evolving.agent_rl.storage.InMemoryTrajectoryStore();
        Trajectory traj1 = makeTrajectory("exec1", "session1", null, "case1", null);
        Trajectory traj2 = makeTrajectory("exec1", "session1", null, "case2", null);

        store.save(traj1);
        store.save(traj2);

        Trajectory loaded = store.load("exec1");
        assertEquals("case2", loaded.getCaseId());
    }
}