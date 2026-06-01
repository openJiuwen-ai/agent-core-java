/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refine-intention detector for LLM agent builder.
 * <p>
 * Mirrors Python's {@code IntentionDetector} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.llm_agent.intention_detector}.
 */
public class IntentionDetector {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(.*?)```", Pattern.DOTALL);

    private final Model llm;

    public IntentionDetector(Model llm) {
        this.llm = llm;
    }

    public Model getLlm() {
        return llm;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractIntent(String inputs) {
        String safeInputs = inputs == null ? "" : inputs;
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(safeInputs);
        String json = matcher.find() ? matcher.group(1) : safeInputs;
        Map<String, Object> parsed = JsonUtils.safeJsonLoads(json.trim(), Map.class, new LinkedHashMap<>());
        return parsed == null ? new LinkedHashMap<>() : parsed;
    }

    public boolean detectRefineIntent(String query, String agentConfigInfo) {
        try {
            if (query == null || query.isBlank()) {
                return false;
            }
            if (llm == null) {
                throw new IllegalStateException("LLM service is not configured");
            }

            List<BaseMessage> messages = List.of(
                    new SystemMessage(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT),
                    new UserMessage(LlmAgentPrompts.formatUserIntentionPrompt(query, agentConfigInfo))
            );
            AssistantMessage response = llm.invoke(
                    messages,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Collections.emptyMap()
            );
            Map<String, Object> jsonResponse = extractIntent(response != null ? response.getContentAsString() : "");
            Object refined = jsonResponse.get("need_refined");
            return refined instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(refined));
        } catch (ApplicationError e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationError(
                    StatusCode.ERROR,
                    "NL2LLM Agent意图检测出现异常: " + e.getMessage(),
                    Map.of("error", e.getMessage(), "error_code", "llm_agent_state_error"),
                    e,
                    Map.of()
            );
        }
    }
}
