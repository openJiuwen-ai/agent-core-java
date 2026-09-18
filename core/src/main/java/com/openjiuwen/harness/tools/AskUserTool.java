/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * Structured user-question tool shell.
 *
 * <p>Mirrors Python's {@code AskUserTool} in
 * {@code openjiuwen/harness/tools/ask_user.py}.</p>
 */
public class AskUserTool extends AbstractHarnessTool {

    private final String language;
    private final String agentId;

    public AskUserTool() {
        this("cn", null);
    }

    public AskUserTool(String language, String agentId) {
        super(toolCard("ask_user", "ask_user", "Ask the user a structured follow-up question."));
        this.language = language == null || language.isBlank() ? "cn" : language;
        this.agentId = agentId;
    }

    public String getLanguage() {
        return language;
    }

    public String getAgentId() {
        return agentId;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return Map.of();
    }
}
