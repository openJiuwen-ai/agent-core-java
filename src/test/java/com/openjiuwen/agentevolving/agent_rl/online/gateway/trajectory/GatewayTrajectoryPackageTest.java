/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GatewayTrajectoryPackageTest {

    @SuppressWarnings("unchecked")
    @Test
    void exposesPythonPackageBridgeAndFunctionDelegates() {
        assertEquals(
                "openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/__init__.py",
                GatewayTrajectoryPackage.PYTHON_MODULE
        );
        assertEquals(
                "Trajectory ingestion and storage helpers for the online-RL gateway.",
                GatewayTrajectoryPackage.DESCRIPTION
        );
        assertEquals(
                List.of(
                        "GatewayTrajectoryRuntime",
                        "SampleRecorder",
                        "JudgeDispatcher",
                        "PendingJudgeStore",
                        "RailBatchIngestor",
                        "build_sample",
                        "coerce_logprobs"
                ),
                GatewayTrajectoryPackage.EXPORTED_SYMBOLS
        );
        assertSame(GatewayTrajectoryRuntime.class, GatewayTrajectoryPackage.GATEWAY_TRAJECTORY_RUNTIME);
        assertSame(SampleRecorder.class, GatewayTrajectoryPackage.SAMPLE_RECORDER);
        assertSame(JudgeDispatcher.class, GatewayTrajectoryPackage.JUDGE_DISPATCHER);
        assertSame(PendingJudgeStore.class, GatewayTrajectoryPackage.PENDING_JUDGE_STORE);
        assertSame(RailBatchIngestor.class, GatewayTrajectoryPackage.RAIL_BATCH_INGESTOR);
        assertEquals(List.of(1.5, 0.0), GatewayTrajectoryPackage.coerceLogprobs(List.of("1.5"), 2));

        Map<String, Object> sample = GatewayTrajectoryPackage.buildSample(
                "u1",
                "s1",
                1,
                "online",
                "chat",
                "m1",
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                Map.of("role", "assistant", "content", "pong"),
                Map.of("total_tokens", 3),
                "stop",
                "hello",
                List.of(1),
                "pong",
                List.of(2),
                List.of(0.1),
                List.of(),
                Map.of(),
                "sample-1",
                "2026-06-09T00:00:00+00:00",
                Map.of("rail", "v1")
        );

        assertEquals("sample-1", sample.get("sample_id"));
        assertEquals("v1", sample.get("rail"));
        assertEquals(List.of(1, 2), ((Map<String, Object>) sample.get("trajectory")).get("input_ids"));
    }
}
