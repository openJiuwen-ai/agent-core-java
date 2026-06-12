/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen/core/operator/tool_call/base.py}.
 */
class ToolCallOperatorTest {

    @Test
    void onlyExposesTunableWhenDescriptionsExist() {
        ToolCallOperator empty = new ToolCallOperator("tool_call");
        ToolCallOperator populated = new ToolCallOperator("tool_call", Map.of("grep", "Search text"));

        assertTrue(empty.getTunables().isEmpty());
        assertTrue(populated.getTunables().containsKey("tool_description"));
    }

    @Test
    void setParameterReplacesDescriptionsAndNotifiesCallback() {
        List<Object> updates = new ArrayList<>();
        ToolCallOperator operator = new ToolCallOperator("tool_call", null, (target, value) -> updates.add(value));

        operator.setParameter("tool_description", Map.of("grep", "Search", "open", "Open file"));

        assertEquals(Map.of("grep", "Search", "open", "Open file"), operator.getDescriptions());
        assertEquals(1, updates.size());
        assertEquals(Map.of("grep", "Search", "open", "Open file"), updates.get(0));
    }

    @Test
    void loadStateRestoresDescriptionCache() {
        ToolCallOperator operator = new ToolCallOperator("tool_call");

        operator.loadState(Map.of("tool_description", Map.of("grep", "Search")));

        assertEquals(Map.of("grep", "Search"), operator.getDescriptions());
        assertEquals(Map.of("tool_description", Map.of("grep", "Search")), operator.getState());
    }
}
