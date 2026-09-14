/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * LLM prompt parameter handle for self-evolution.
 *
 * <p>Mirrors Python's {@code LLMCallOperator} in
 * {@code openjiuwen/core/operator/llm_call/base.py}.</p>
 */
public class LLMCallOperator extends Operator {

    public static final String DEFAULT_USER_PROMPT = "{{query}}";

    private PromptTemplate systemPrompt;
    private PromptTemplate userPrompt;
    private boolean freezeSystemPrompt;
    private boolean freezeUserPrompt;
    private final String operatorId;
    private final BiConsumer<String, Object> onParameterUpdated;

    public LLMCallOperator(Object systemPrompt, Object userPrompt) {
        this(systemPrompt, userPrompt, false, true, "llm_call", null);
    }

    public LLMCallOperator(Object systemPrompt,
                           Object userPrompt,
                           boolean freezeSystemPrompt,
                           boolean freezeUserPrompt,
                           String operatorId,
                           BiConsumer<String, Object> onParameterUpdated) {
        this.systemPrompt = PromptTemplate.builder().content(systemPrompt).build();
        this.userPrompt = PromptTemplate.builder().content(resolveUserPrompt(userPrompt)).build();
        this.freezeSystemPrompt = freezeSystemPrompt;
        this.freezeUserPrompt = freezeUserPrompt;
        this.operatorId = operatorId == null ? "llm_call" : operatorId;
        this.onParameterUpdated = onParameterUpdated;
    }

    @Override
    public String getOperatorId() {
        return operatorId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        Map<String, TunableSpec> tunables = new LinkedHashMap<>();
        if (!freezeSystemPrompt) {
            tunables.put("system_prompt", new TunableSpec(
                    "system_prompt",
                    "prompt",
                    "system_prompt"
            ));
        }
        if (!freezeUserPrompt) {
            tunables.put("user_prompt", new TunableSpec(
                    "user_prompt",
                    "prompt",
                    "user_prompt"
            ));
        }
        return tunables;
    }

    @Override
    public void setParameter(String target, Object value) {
        Object content = normalizePromptContent(value);
        if ("system_prompt".equals(target) && !freezeSystemPrompt) {
            systemPrompt = PromptTemplate.builder().content(content).build();
            notifyParameterUpdated("system_prompt", content);
        } else if ("user_prompt".equals(target) && !freezeUserPrompt) {
            userPrompt = PromptTemplate.builder().content(content).build();
            notifyParameterUpdated("user_prompt", content);
        }
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("system_prompt", systemPrompt.getContent());
        state.put("user_prompt", userPrompt.getContent());
        return state;
    }

    @Override
    public void loadState(Map<String, Object> state) {
        if (state == null) {
            return;
        }
        if (state.containsKey("system_prompt")) {
            Object content = normalizePromptContent(state.get("system_prompt"));
            systemPrompt = PromptTemplate.builder().content(content).build();
            notifyParameterUpdated("system_prompt", content);
        }
        if (state.containsKey("user_prompt")) {
            Object content = normalizePromptContent(state.get("user_prompt"));
            userPrompt = PromptTemplate.builder().content(content).build();
            notifyParameterUpdated("user_prompt", content);
        }
    }

    public void setFreezeSystemPrompt(boolean switchValue) {
        freezeSystemPrompt = switchValue;
    }

    public void setFreezeUserPrompt(boolean switchValue) {
        freezeUserPrompt = switchValue;
    }

    public boolean getFreezeSystemPrompt() {
        return freezeSystemPrompt;
    }

    public boolean getFreezeUserPrompt() {
        return freezeUserPrompt;
    }

    public PromptTemplate getSystemPrompt() {
        return systemPrompt;
    }

    public PromptTemplate getUserPrompt() {
        return userPrompt;
    }

    private static Object resolveUserPrompt(Object value) {
        if (value == null) {
            return DEFAULT_USER_PROMPT;
        }
        if (value instanceof String text && text.isEmpty()) {
            return DEFAULT_USER_PROMPT;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return DEFAULT_USER_PROMPT;
        }
        return value;
    }

    private static Object normalizePromptContent(Object value) {
        if (value instanceof String || value instanceof List<?>) {
            return value;
        }
        return toPythonString(value);
    }

    private static String toPythonString(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean flag) {
            return flag ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            return toPythonMapString(map);
        }
        return String.valueOf(value);
    }

    private static String toPythonMapString(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(toPythonRepr(entry.getKey()))
                    .append(": ")
                    .append(toPythonRepr(entry.getValue()));
            first = false;
        }
        return builder.append('}').toString();
    }

    private static String toPythonListString(List<?> list) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(toPythonRepr(item));
            first = false;
        }
        return builder.append(']').toString();
    }

    private static String toPythonRepr(Object value) {
        if (value instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Map<?, ?> map) {
            return toPythonMapString(map);
        }
        if (value instanceof List<?> list) {
            return toPythonListString(list);
        }
        return toPythonString(value);
    }

    private void notifyParameterUpdated(String target, Object value) {
        if (onParameterUpdated != null) {
            onParameterUpdated.accept(target, value);
        }
    }
}
