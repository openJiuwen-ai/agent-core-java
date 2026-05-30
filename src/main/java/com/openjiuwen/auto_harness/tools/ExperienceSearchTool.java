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
import java.util.UUID;

/**
 * Mirrors Python's {@code ExperienceSearchTool} in {@code openjiuwen.auto_harness.tools.experience_search_tool}.
 */
public class ExperienceSearchTool extends Tool {

    private final String experienceDir;
    private final String language;

    public ExperienceSearchTool(String experienceDir) {
        this(experienceDir, "cn");
    }

    public ExperienceSearchTool(String experienceDir, String language) {
        this(experienceDir, null, language);
    }

    public ExperienceSearchTool(String experienceDir, String agentId, String language) {
        super(createCard(language, agentId));
        this.experienceDir = experienceDir;
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
        if (query.isEmpty()) {
            return new ToolOutput(false, null, "query \u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }
        try {
            ExperienceStore store = new ExperienceStore(experienceDir);
            List<Map<String, Object>> data = new ArrayList<>();
            for (Experience experience : store.search(query, limit)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", experience.getType().toString());
                item.put("topic", experience.getTopic());
                item.put("summary", experience.getSummary());
                item.put("outcome", experience.getOutcome());
                data.add(item);
            }
            return new ToolOutput(true, data, null);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return new ToolOutput(false, null, message.substring(0, Math.min(200, message.length())));
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.<Object>of(invoke(inputs, kwargs)).iterator();
    }

    public String getLanguage() {
        return language;
    }

    private static ToolCard createCard(String language, String agentId) {
        ToolCard card = new ToolCard();
        String suffix = agentId != null ? agentId : UUID.randomUUID().toString().replace("-", "");
        card.setId("ExperienceSearchTool_" + suffix);
        card.setName("experience_search");
        card.setDescription(description(language));
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

    private static String description(String language) {
        if ("en".equals(language)) {
            return "Search historical experiences by keyword and return relevant success/failure/insight entries.";
        }
        return "\u641c\u7d22\u5386\u53f2\u7ecf\u9a8c\u8bb0\u5f55\u3002\u8f93\u5165\u5173\u952e\u8bcd\uff0c"
                + "\u8fd4\u56de\u76f8\u5173\u7684\u6210\u529f/\u5931\u8d25/\u6d1e\u5bdf\u7ecf\u9a8c\u3002";
    }
}
