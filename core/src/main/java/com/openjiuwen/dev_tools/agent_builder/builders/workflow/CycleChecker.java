/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.dev_tools.agent_builder.utils.AgentBuilderUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Checks Mermaid workflow cycles through an LLM prompt.
 *
 * <p>Mirrors Python's {@code CycleChecker} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/cycle_checker.py}.</p>
 */
public class CycleChecker {

    private final Model llm;

    public CycleChecker(Model llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public String checkMermaidCycle(String mermaidCode) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("mermaid_code", mermaidCode);
        List<BaseMessage> userMessages = WorkflowPrompts.CHECK_CYCLE_USER_PROMPT_TEMPLATE
                .format(values)
                .toMessages();
        String userPrompt = userMessages.isEmpty() ? "" : userMessages.get(0).getContentAsString();

        AssistantMessage response = llm.invoke(List.of(
                new SystemMessage(WorkflowPrompts.CHECK_CYCLE_SYSTEM_PROMPT),
                new SystemMessage(userPrompt)
        )).toCompletableFuture().join();
        return response.getContentAsString();
    }

    public static CycleResult parseCycleResultJson(String inputs) {
        String json = AgentBuilderUtils.extractJsonFromText(inputs);
        Object parsed = JsonUtils.safeJsonLoads(json);
        if (!(parsed instanceof Map<?, ?> resultMap)) {
            throw new IllegalArgumentException("cycle result JSON must be an object");
        }

        Object needRefined = resultMap.containsKey("need_refined")
                ? resultMap.get("need_refined")
                : Boolean.FALSE;
        Object loopDesc = resultMap.containsKey("loop_desc") ? resultMap.get("loop_desc") : "";
        return new CycleResult(pythonTruth(needRefined), pythonString(loopDesc));
    }

    public CycleResult checkAndParse(String mermaidCode) {
        return parseCycleResultJson(checkMermaidCycle(mermaidCode));
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
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String pythonString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }

    /**
     * Tuple-style cycle check result.
     *
     * <p>Mirrors Python's {@code Tuple[bool, str]} returned by
     * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/cycle_checker.py}.</p>
     */
    public record CycleResult(boolean needRefined, String loopDesc) {
    }
}
