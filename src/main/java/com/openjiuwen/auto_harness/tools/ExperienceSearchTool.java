package com.openjiuwen.auto_harness.tools;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ExperienceSearchTool} in {@code openjiuwen.auto_harness.tools.experience_search_tool}.
 */
public class ExperienceSearchTool {

    private final ExperienceStore store;

    public ExperienceSearchTool(String experienceDir) {
        this.store = new ExperienceStore(experienceDir);
    }

    public ToolOutput invoke(Map<String, Object> inputs) {
        String query = inputs.get("query") != null ? String.valueOf(inputs.get("query")) : "";
        int limit = inputs.get("limit") instanceof Number number ? number.intValue() : 5;
        if (query.isBlank()) {
            return new ToolOutput(false, null, "查询不能为空");
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Experience experience : store.search(query, limit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", experience.getId());
            item.put("topic", experience.getTopic());
            item.put("summary", experience.getSummary());
            item.put("outcome", experience.getOutcome());
            item.put("type", experience.getType().toString());
            data.add(item);
        }
        return new ToolOutput(true, data, null);
    }

    public List<ToolOutput> stream(Map<String, Object> inputs) {
        return List.of(invoke(inputs));
    }
}
