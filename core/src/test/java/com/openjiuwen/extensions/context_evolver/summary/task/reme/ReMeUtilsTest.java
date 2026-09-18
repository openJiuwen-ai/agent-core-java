/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's helper behavior in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/utils.py}.
 */
class ReMeUtilsTest {

    @Test
    void codeFenceListFiltersInvalidExperienceEntries() {
        String response = """
                ```json
                [
                  {"experience": "keep", "when_to_use": "now"},
                  {"experience": "drop"},
                  {"condition": "later"}
                ]
                ```
                """;

        assertEquals(
                List.of(Map.of("experience", "keep", "when_to_use", "now")),
                ReMeUtils.parseJsonExperienceResponse(response)
        );
    }

    @Test
    void plainJsonListReturnsMapsWithoutExtraFiltering() {
        String response = "[{\"experience\": \"keep\", \"condition\": \"soon\"}]";

        assertEquals(
                List.of(Map.of("experience", "keep", "condition", "soon")),
                ReMeUtils.parseJsonExperienceResponse(response)
        );
    }

    @Test
    void invalidJsonReturnsEmptyList() {
        assertTrue(ReMeUtils.parseJsonExperienceResponse("not-json").isEmpty());
    }

    @Test
    void calculatesCosineSimilarity() {
        assertEquals(1.0, ReMeUtils.calculateCosineSimilarity(List.of(1.0, 0.0), List.of(1.0, 0.0)));
        assertEquals(0.0, ReMeUtils.calculateCosineSimilarity(List.of(0.0, 0.0), List.of(1.0, 2.0)));
    }
}
