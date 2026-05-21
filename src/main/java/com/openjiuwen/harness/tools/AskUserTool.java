package com.openjiuwen.harness.tools;

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
        return new ToolOutput(true, Map.of(), null);
    }
}
