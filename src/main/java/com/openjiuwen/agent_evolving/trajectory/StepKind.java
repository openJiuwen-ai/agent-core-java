package com.openjiuwen.agent_evolving.trajectory;

/**
 * Step kind aligned with Python's trajectory StepKind literal values.
 */
public enum StepKind {
    LLM("llm"),
    TOOL("tool"),
    MEMORY("memory"),
    WORKFLOW("workflow"),
    AGENT("agent");

    private final String value;

    StepKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static StepKind fromValue(String value) {
        if (value == null || value.isBlank()) {
            return AGENT;
        }
        for (StepKind kind : values()) {
            if (kind.value.equalsIgnoreCase(value)) {
                return kind;
            }
        }
        return AGENT;
    }
}
