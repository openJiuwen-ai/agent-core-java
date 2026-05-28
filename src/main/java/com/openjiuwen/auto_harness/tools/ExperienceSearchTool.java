package com.openjiuwen.auto_harness.tools;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.Experience;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ExperienceSearchTool} in {@code openjiuwen.auto_harness.tools.experience_search_tool}.
 */
public class ExperienceSearchTool extends Tool {

    private final ExperienceStore store;
    private final String language;

    public ExperienceSearchTool(String experienceDir) {
        this(experienceDir, "cn");
    }

    public ExperienceSearchTool(String experienceDir, String language) {
        super(createCard());
        this.store = new ExperienceStore(experienceDir);
        this.language = language;
    }

    @Override
    public ToolOutput invoke(Map<String, Object> inputs) {
        return invoke(inputs, Map.of());
    }

    @Override
    public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String query = inputs.get("query") != null ? String.valueOf(inputs.get("query")) : "";
        int limit = inputs.get("limit") instanceof Number number ? number.intValue() : 5;
        if (query.isBlank()) {
            return new ToolOutput(false, null, "query cannot be empty");
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

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.<Object>of(invoke(inputs, kwargs)).iterator();
    }

    public String getLanguage() {
        return language;
    }

    private static ToolCard createCard() {
        ToolCard card = new ToolCard();
        card.setId("experience_search");
        card.setName("experience_search");
        card.setDescription("Search auto-harness experience records.");
        card.setInputParams(Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string"),
                        "limit", Map.of("type", "integer", "default", 5)
                ),
                "required", List.of("query")
        ));
        return card;
    }
}
