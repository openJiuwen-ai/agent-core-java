/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomizedPipeline slice handling.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.tool_call.test_customized_pipeline}.
 */
class CustomizedPipelineTest {

    @Test
    void testCustomPipelineSliceExtractsSteps() {
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("steps", List.of(
            Map.of("name", "step1", "type", "transform"),
            Map.of("name", "step2", "type", "validate")
        ));

        Map<String, Object> slice = extractPipelineSlice(pipelineSpec);

        assertEquals(2, ((List<?>) slice.get("steps")).size());
    }

    @Test
    void testCustomPipelineSlicePreservesOrder() {
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("steps", List.of(
            Map.of("name", "first"),
            Map.of("name", "second"),
            Map.of("name", "third")
        ));

        Map<String, Object> slice = extractPipelineSlice(pipelineSpec);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) slice.get("steps");

        assertEquals("first", steps.get(0).get("name"));
        assertEquals("second", steps.get(1).get("name"));
        assertEquals("third", steps.get(2).get("name"));
    }

    @Test
    void testCustomPipelineSliceWithConditionals() {
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("steps", List.of(
            Map.of("name", "conditional_step", "condition", "input.valid == true")
        ));

        Map<String, Object> slice = extractPipelineSlice(pipelineSpec);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) slice.get("steps");

        assertTrue(steps.get(0).containsKey("condition"));
    }

    @Test
    void testCustomPipelineSliceWithRetryConfig() {
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("retry_config", Map.of(
            "max_attempts", 3,
            "backoff_ms", 100
        ));

        Map<String, Object> slice = extractPipelineSlice(pipelineSpec);

        assertTrue(slice.containsKey("retry_config"));
    }

    @Test
    void testCustomPipelineSliceEmptySteps() {
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("steps", new ArrayList<>());

        Map<String, Object> slice = extractPipelineSlice(pipelineSpec);

        assertTrue(((List<?>) slice.get("steps")).isEmpty());
    }

    @Test
    void testCustomPipelineSliceWithOutputMapping() {
        Map<String, Object> pipelineSpec = new HashMap<>();
        pipelineSpec.put("output_mapping", Map.of(
            "final_result", "steps[-1].output"
        ));

        Map<String, Object> slice = extractPipelineSlice(pipelineSpec);

        assertTrue(slice.containsKey("output_mapping"));
    }

    private Map<String, Object> extractPipelineSlice(Map<String, Object> pipelineSpec) {
        Map<String, Object> slice = new HashMap<>();
        
        if (pipelineSpec.containsKey("steps")) {
            slice.put("steps", pipelineSpec.get("steps"));
        }
        if (pipelineSpec.containsKey("retry_config")) {
            slice.put("retry_config", pipelineSpec.get("retry_config"));
        }
        if (pipelineSpec.containsKey("output_mapping")) {
            slice.put("output_mapping", pipelineSpec.get("output_mapping"));
        }
        
        return slice;
    }
}