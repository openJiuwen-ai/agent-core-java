/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import java.util.*;

/**
 * LLM agent template providing the base configuration structure.
 * <p>
 * Mirrors Python's {@code LLM_AGENT_TEMPLATE} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/template.py}.
 */
public final class LlmAgentTemplate {

    private LlmAgentTemplate() {
    }

    public static Map<String, Object> create() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("agent_id", "");
        template.put("agent_type", "react");
        template.put("agent_version", "");
        template.put("auto_generated_prompt", "");
        template.put("name", "");
        template.put("description", "");
        template.put("icon", "");

        Map<String, Object> configs = new LinkedHashMap<>();
        configs.put("system_prompt", "");
        template.put("configs", configs);

        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("max_iterations", 5);
        constraints.put("reserved_max_chat_rounds", 10);
        template.put("constraints", constraints);

        template.put("edit_mode", "manual");
        template.put("latest_publish_time", null);
        template.put("latest_publish_version", null);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("max_tokens", 1000);
        template.put("memory", memory);

        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("api_base", "");
        modelInfo.put("api_key", "");
        modelInfo.put("model_id", "");
        modelInfo.put("model_name", "");
        modelInfo.put("model_type", "");
        modelInfo.put("max_tokens", 2048);
        modelInfo.put("streaming", true);
        modelInfo.put("temperature", 2);
        modelInfo.put("top_p", 0.9);
        modelInfo.put("timeout", 1000);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("model_info", modelInfo);
        model.put("model_provider", "");
        template.put("model", model);

        template.put("prompt_template", new ArrayList<>());
        template.put("prompt_template_name", "");
        template.put("prompt_tuning", new LinkedHashMap<>());
        template.put("opening_remarks", "");
        template.put("space_id", "");
        template.put("triggers", new ArrayList<>());
        template.put("plugins", new ArrayList<>());
        template.put("knowledge", new ArrayList<>());
        template.put("workflows", new ArrayList<>());
        template.put("create_time", null);
        template.put("update_time", null);

        return template;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), new ArrayList<>((List<?>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }
}
