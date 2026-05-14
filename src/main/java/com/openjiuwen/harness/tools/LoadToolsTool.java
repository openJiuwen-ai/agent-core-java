package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code LoadToolsTool} in {@code openjiuwen.harness.tools.tool_discovery.load_tools}.
 */
public class LoadToolsTool extends AbstractHarnessTool {

    public LoadToolsTool() {
        super(toolCard("load_tools", "load_tools", "Resolve and load selected tools for the current session."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        @SuppressWarnings("unchecked")
        List<String> toolNames = inputs.get("tool_names") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        boolean replace = Boolean.TRUE.equals(inputs.get("replace"));
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (String toolName : toolNames) {
            List<ToolInfo> infos = Runner.resourceMgr().getToolInfos(toolName, null, null, TagMatchStrategy.ALL);
            ToolInfo match = infos.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
            if (match != null) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("name", match.getName());
                data.put("description", match.getDescription());
                resolved.add(data);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool_names", toolNames);
        result.put("replace", replace);
        result.put("loaded_tools", resolved);
        result.put("loaded_count", resolved.size());
        return new ToolOutput(true, result, null);
    }
}
