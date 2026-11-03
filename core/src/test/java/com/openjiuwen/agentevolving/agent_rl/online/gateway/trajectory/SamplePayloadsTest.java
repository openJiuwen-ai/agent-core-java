/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SamplePayloadsTest {

    @Test
    void coerceLogprobsFiltersInvalidValuesAndPadsToExpectedLength() {
        assertEquals(List.of(1.5, 2.0, 0.0), SamplePayloads.coerceLogprobs(List.of("1.5", "bad", 2), 3));
    }

    @SuppressWarnings("unchecked")
    @Test
    void buildSampleCreatesNormalizedTrajectoryPayload() {
        Map<String, Object> sample = SamplePayloads.buildSample(
                "u1",
                "s1",
                2,
                "online",
                "chat",
                "m1",
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                Map.of("role", "assistant", "content", "pong"),
                Map.of("total_tokens", 3),
                "stop",
                "hello",
                List.of(1, 2),
                "pong",
                List.of(3),
                List.of(0.1),
                List.of(),
                Map.of("extra", true),
                "sample-1",
                "2026-06-07T00:00:00+00:00",
                Map.of("rail", "v1")
        );

        assertEquals("sample-1", sample.get("sample_id"));
        assertEquals("2026-06-07T00:00:00+00:00", sample.get("created_at"));
        assertEquals(List.of(1, 2, 3), ((Map<String, Object>) sample.get("trajectory")).get("input_ids"));
        assertEquals(List.of(0, 0, 1), ((Map<String, Object>) sample.get("trajectory")).get("response_mask"));
        assertEquals(true, ((Map<String, Object>) sample.get("request")).get("extra"));
        assertEquals("v1", sample.get("rail"));
    }
}
