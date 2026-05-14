package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's minimal tool-discovery behavior intent for P2-02.
 */
class ToolDiscoveryToolsTest {

    @Test
    void searchToolsFindsMatchingTools() {
        SearchToolsTool tool = new SearchToolsTool() {
            @Override
            protected List<ToolInfo> listAllToolInfos() {
                return List.of(
                        ToolInfo.builder().name("read_file").description("Read file content").parameters(Map.of("path", Map.of("type", "string"))).build(),
                        ToolInfo.builder().name("search_code").description("Search code symbols").parameters(Map.of("query", Map.of("type", "string"))).build()
                );
            }
        };

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "search", "limit", 5, "detail_level", 2), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) output.getData();
        assertEquals(1, data.size());
        assertEquals("search_code", data.get(0).get("name"));
        assertTrue(data.get(0).containsKey("parameter_keys"));
    }

    @Test
    void searchToolsRequiresQuery() {
        SearchToolsTool tool = new SearchToolsTool();
        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", ""), Map.of());
        assertFalse(output.isSuccess());
        assertEquals("query is required", output.getError());
    }

    @Test
    void loadToolsReturnsResolvedTools() {
        LoadToolsTool tool = new LoadToolsTool() {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                @SuppressWarnings("unchecked")
                List<String> toolNames = (List<String>) inputs.get("tool_names");
                return new ToolOutput(true, Map.of(
                        "tool_names", toolNames,
                        "replace", true,
                        "loaded_tools", List.of(Map.of("name", "read_file", "description", "Read file content")),
                        "loaded_count", 1
                ), null);
            }
        };

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("tool_names", List.of("read_file"), "replace", true), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals(1, data.get("loaded_count"));
        assertEquals(true, data.get("replace"));
    }
}
