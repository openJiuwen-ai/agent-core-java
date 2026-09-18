/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's helper behavior in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reme/utils.py}.
 */
class ReMeRetrieveUtilsTest {

    @Test
    void parsesRankedIndicesFromJsonCodeFence() {
        String response = """
                ```json
                {
                  "ranked_indices": [2, 0, 4]
                }
                ```
                """;

        assertEquals(List.of(2, 0, 4), ReMeRetrieveUtils.parseJsonListResponse(response));
    }

    @Test
    void fallsBackToNumbersInPlainText() {
        String response = "ranking: 3, 101, 1, 0";

        assertEquals(List.of(3, 1, 0), ReMeRetrieveUtils.parseJsonListResponse(response));
    }

    @Test
    void parsesTextFieldFromCodeFence() {
        String response = """
                ```json
                {
                  "rewritten_context": "Use the retrieved experience carefully."
                }
                ```
                """;

        assertEquals(
                Optional.of("Use the retrieved experience carefully."),
                ReMeRetrieveUtils.parseJsonField(response, "rewritten_context")
        );
    }

    @Test
    void invalidJsonReturnsEmptyField() {
        assertTrue(ReMeRetrieveUtils.parseJsonField("not valid json", "rewritten_context").isEmpty());
    }
}
