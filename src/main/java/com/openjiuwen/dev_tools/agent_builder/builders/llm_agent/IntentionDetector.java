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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects whether an LLM-agent configuration query needs refinement.
 *
 * <p>Mirrors Python's {@code IntentionDetector} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/intention_detector.py}.</p>
 */
public class IntentionDetector {
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL);

    private final Model llm;

    public IntentionDetector(Model llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public Model getLlm() {
        return llm;
    }

    public static Map<String, Object> extractIntent(String inputs) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(inputs == null ? "" : inputs);
        String result = matcher.find() ? matcher.group(1) : inputs;
        Object parsed = JsonUtils.safeJsonLoads(result);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("intent JSON must be an object");
        }
        Map<String, Object> intent = new LinkedHashMap<>();
        map.forEach((key, value) -> intent.put(String.valueOf(key), value));
        return intent;
    }

    public boolean detectRefineIntent(String query, String agentConfigInfo) {
        try {
            if (query == null || query.isEmpty()) {
                return false;
            }

            List<BaseMessage> userMessages = LlmAgentPrompts.USER_INTENTION_PROMPT_TEMPLATE.format(Map.of(
                    "query", query,
                    "agent_config_info", agentConfigInfo == null ? "" : agentConfigInfo
            )).toMessages();
            List<BaseMessage> messages = new ArrayList<>();
            messages.add(new SystemMessage(LlmAgentPrompts.REFINE_INTENTION_SYSTEM_PROMPT));
            messages.addAll(userMessages);

            AssistantMessage response = llm.invoke(messages).toCompletableFuture().join();
            Map<String, Object> jsonResponse = extractIntent(
                    Objects.toString(response == null ? null : response.getContent(), "")
            );
            return pythonTruth(jsonResponse.getOrDefault("need_refined", Boolean.FALSE));
        } catch (Exception exception) {
            String message = "NL2LLM Agent意图检测出现异常: " + exceptionMessage(exception);
            throw new ApplicationError(
                    StatusCode.ERROR,
                    message,
                    Map.of("error", exceptionMessage(exception),
                            "error_code", StatusCode.LLM_AGENT_STATE_ERROR.getCode()),
                    exception,
                    Map.of("error_msg", message)
            );
        }
    }

    private static boolean pythonTruth(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof java.util.concurrent.CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }
}
