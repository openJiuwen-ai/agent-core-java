package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code SearchToolsTool} in {@code openjiuwen.harness.tools.tool_discovery.search_tools}.
 */
public class SearchToolsTool extends AbstractHarnessTool {

    public SearchToolsTool() {
        super(toolCard("search_tools", "search_tools", "Search candidate tools for progressive discovery."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String query = stringValue(inputs.get("query"));
        int limit = clamp(intValue(inputs.get("limit"), 10), 1, 20);
        int detailLevel = intValue(inputs.get("detail_level"), 1);
        if (query.isBlank()) {
            return new ToolOutput(false, null, "query is required");
        }
        List<ToolInfo> infos = listAllToolInfos();
        List<Map<String, Object>> matches = new ArrayList<>();
        String normalized = query.toLowerCase();
        for (ToolInfo info : infos) {
            if (info == null) {
                continue;
            }
            String haystack = (info.getName() + " " + info.getDescription()).toLowerCase();
            if (!haystack.contains(normalized)) {
                continue;
            }
            matches.add(toMap(info, detailLevel));
            if (matches.size() >= limit) {
                break;
            }
        }
        return new ToolOutput(true, matches, null);
    }

    protected List<ToolInfo> listAllToolInfos() {
        return Runner.resourceMgr().getToolInfos(null, null, null, TagMatchStrategy.ALL);
    }

    protected Map<String, Object> toMap(ToolInfo info, int detailLevel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", info.getName());
        data.put("description", info.getDescription());
        if (detailLevel >= 2) {
            data.put("parameter_keys", info.getParameters() != null ? info.getParameters().keySet().stream().toList() : List.of());
        }
        if (detailLevel >= 3) {
            data.put("parameters", info.getParameters());
        }
        return data;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
