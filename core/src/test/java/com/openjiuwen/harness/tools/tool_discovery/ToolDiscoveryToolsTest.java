/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.tool_discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.harness.tools.ToolOutput;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's progressive discovery tools in
 * {@code openjiuwen/harness/tools/tool_discovery/}.
 */
class ToolDiscoveryToolsTest {

    @Test
    @SuppressWarnings("unchecked")
    void searchToolsClampsLimitAndAppendsTrace() throws Exception {
        AtomicReference<Map<String, Object>> trace = new AtomicReference<>();
        SearchToolsTool tool = new SearchToolsTool(
                (query, limit, detailLevel) -> List.of(Map.of(
                        "name", "read_memory",
                        "query", query,
                        "limit", limit,
                        "detail_level", detailLevel
                )),
                (session, value) -> trace.set(value)
        );

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("query", "memory", "limit", 99, "detail_level", 3),
                Map.of("session", "session-1")
        );

        assertTrue(output.isSuccess());
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals(1, data.get("count"));
        assertEquals(20, trace.get().get("limit"));
        assertEquals(3, trace.get().get("detail_level"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadToolsPassesSessionNamesAndReplaceFlag() throws Exception {
        LoadToolsTool tool = new LoadToolsTool((session, names, replace) -> Map.of(
                "session", session,
                "tool_names", names,
                "replace", replace
        ));

        ToolOutput output = (ToolOutput) tool.invoke(
                Map.of("tool_names", List.of("read_memory", "write_memory"), "replace", true),
                Map.of("session", "session-1")
        );

        assertTrue(output.isSuccess());
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("session-1", data.get("session"));
        assertEquals(List.of("read_memory", "write_memory"), data.get("tool_names"));
        assertEquals(true, data.get("replace"));
    }
}
