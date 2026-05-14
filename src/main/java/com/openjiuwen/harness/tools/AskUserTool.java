package com.openjiuwen.harness.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code AskUserTool} in {@code openjiuwen.harness.tools.ask_user}.
 */
public class AskUserTool extends AbstractHarnessTool {

    public AskUserTool() {
        super(toolCard("ask_user", "ask_user", "Ask the user a structured follow-up question."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question", inputs.get("question"));
        data.put("questions", inputs.get("questions") instanceof List<?> list ? list : List.of());
        data.put("header", inputs.get("header"));
        data.put("multiple", Boolean.TRUE.equals(inputs.get("multiple")));
        data.put("options", inputs.get("options") instanceof List<?> list ? list : List.of());
        return new ToolOutput(true, data, null);
    }
}
