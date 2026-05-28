/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

/**
 * Centralized section name constants used by PromptSection and Rails.
 * <p>
 * Mirrors Python's {@code SectionName} in
 * {@code openjiuwen.harness.prompts.sections}.
 */
public final class SectionName {

    private SectionName() {
    }

    public static final String IDENTITY = "identity";
    public static final String SAFETY = "safety";
    public static final String SKILLS = "skills";
    public static final String TOOLS = "tools";
    public static final String TODO = "todo";
    public static final String TASK_TOOL = "task_tool";
    public static final String TOOL_NAVIGATION = "tool_navigation";
    public static final String PROGRESSIVE_TOOL_RULES = "progressive_tool_rules";
    public static final String RUNTIME = "runtime";
    public static final String MEMORY = "memory";
    public static final String SESSION_TOOLS = "session_tools";
    public static final String MODE_INSTRUCTIONS = "mode_instructions";
    public static final String WORKSPACE = "workspace";
    public static final String HEARTBEAT = "heartbeat";
    public static final String CONTEXT = "context";
    public static final String EXTERNAL_MEMORY = "external_memory";
    public static final String COMPLETION_SIGNAL = "completion_signal";
    public static final String VERIFICATION_CONTRACT = "verification_contract";
}
