/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.llm_call;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.operator.TunableSpec;
import com.openjiuwen.core.session.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * LLM invocation operator with prompt tunables.
 *
 * <p>Mirrors Python's {@code LLMCallOperator} in
 * {@code openjiuwen.core.operator.llm_call.base}.</p>
 */
public class LLMCallOperator extends Operator {

    public static final String DEFAULT_USER_PROMPT = "{{query}}";

    private final Model llm;
    private final String modelName;
    private final String llmCallId;
    private final BiConsumer<String, Object> onParameterUpdated;
    private PromptTemplate systemPrompt;
    private PromptTemplate userPrompt;
    private boolean freezeSystemPrompt;
    private boolean freezeUserPrompt;

    public LLMCallOperator(String modelName,
                           Model llm,
                           Object systemPrompt,
                           Object userPrompt,
                           boolean freezeSystemPrompt,
                           boolean freezeUserPrompt,
                           String llmCallId,
                           BiConsumer<String, Object> onParameterUpdated) {
        this.modelName = modelName;
        this.llm = llm;
        this.systemPrompt = PromptTemplate.builder().content(systemPrompt).build();
        this.userPrompt = PromptTemplate.builder().content(resolveUserPrompt(userPrompt)).build();
        this.freezeSystemPrompt = freezeSystemPrompt;
        this.freezeUserPrompt = freezeUserPrompt;
        this.llmCallId = llmCallId != null ? llmCallId : "llm_call";
        this.onParameterUpdated = onParameterUpdated;
    }

    public LLMCallOperator(String modelName, Model llm, Object systemPrompt, Object userPrompt) {
        this(modelName, llm, systemPrompt, userPrompt, false, true, "llm_call", null);
    }

    @Override
    public String getOperatorId() {
        return llmCallId;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        Map<String, TunableSpec> tunables = new LinkedHashMap<>();
        if (!freezeSystemPrompt) {
            tunables.put("system_prompt", new TunableSpec("system_prompt", "prompt", "system_prompt"));
        }
        if (!freezeUserPrompt) {
            tunables.put("user_prompt", new TunableSpec("user_prompt", "prompt", "user_prompt"));
        }
        return tunables;
    }

    @Override
    public void setParameter(String target, Object value) {
        Object content = normalizePromptContent(value);
        if ("system_prompt".equals(target) && !freezeSystemPrompt) {
            updateSystemPrompt(content);
            notifyParameterUpdated("system_prompt", content);
        } else if ("user_prompt".equals(target) && !freezeUserPrompt) {
            updateUserPrompt(content);
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
            this.systemPrompt = PromptTemplate.builder().content(content).build();
            notifyParameterUpdated("system_prompt", content);
        }
        if (state.containsKey("user_prompt")) {
            Object content = normalizePromptContent(state.get("user_prompt"));
            this.userPrompt = PromptTemplate.builder().content(content).build();
            notifyParameterUpdated("user_prompt", content);
        }
    }

    public AssistantMessage invoke(Map<String, Object> inputs,
                                   Session session,
                                   Map<String, Object> kwargs) throws Exception {
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        List<BaseMessage> messages = formatMessages(inputs, extractHistory(safeKwargs));
        Object tools = safeKwargs.get("tools");
        Map<String, Object> passthroughKwargs = new LinkedHashMap<>(safeKwargs);
        passthroughKwargs.remove("history");
        passthroughKwargs.remove("tools");
        setOperatorContext(session, llmCallId);
        try {
            return llm.invoke(messages, tools, null, null, modelName, null, null, null, null, passthroughKwargs);
        } finally {
            setOperatorContext(session, null);
        }
    }

    public AssistantMessage invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, Collections.emptyMap());
    }

    public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs,
                                                        Session session,
                                                        Map<String, Object> kwargs) throws Exception {
        Map<String, Object> safeKwargs = kwargs != null ? kwargs : Collections.emptyMap();
        List<BaseMessage> messages = formatMessages(inputs, extractHistory(safeKwargs));
        Object tools = safeKwargs.get("tools");
        Map<String, Object> passthroughKwargs = new LinkedHashMap<>(safeKwargs);
        passthroughKwargs.remove("history");
        passthroughKwargs.remove("tools");
        setOperatorContext(session, llmCallId);
        try {
            java.util.Iterator<AssistantMessageChunk> iterator = llm.stream(
                    messages, tools, null, null, modelName, null, null, null, null, passthroughKwargs);
            return OperatorStream.wrap(iterator, () -> setOperatorContext(session, null));
        } catch (Exception ex) {
            setOperatorContext(session, null);
            throw ex;
        }
    }

    public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, Collections.emptyMap());
    }

    public PromptTemplate getSystemPrompt() {
        return systemPrompt;
    }

    public PromptTemplate getUserPrompt() {
        return userPrompt;
    }

    public boolean hasModel() {
        return llm != null;
    }

    public void updateSystemPrompt(Object value) {
        if (!freezeSystemPrompt) {
            this.systemPrompt = PromptTemplate.builder().content(value).build();
        }
    }

    public void updateUserPrompt(Object value) {
        if (!freezeUserPrompt) {
            this.userPrompt = PromptTemplate.builder().content(value).build();
        }
    }

    public void setFreezeSystemPrompt(boolean freezeSystemPrompt) {
        this.freezeSystemPrompt = freezeSystemPrompt;
    }

    public void setFreezeUserPrompt(boolean freezeUserPrompt) {
        this.freezeUserPrompt = freezeUserPrompt;
    }

    public boolean getFreezeSystemPrompt() {
        return freezeSystemPrompt;
    }

    public boolean getFreezeUserPrompt() {
        return freezeUserPrompt;
    }

    private static Object resolveUserPrompt(Object userPrompt) {
        if (userPrompt instanceof String stringPrompt && stringPrompt.isEmpty()) {
            return DEFAULT_USER_PROMPT;
        }
        return userPrompt != null ? userPrompt : DEFAULT_USER_PROMPT;
    }

    private static Object normalizePromptContent(Object value) {
        if (value instanceof String || value instanceof List<?>) {
            return value;
        }
        return value != null ? value.toString() : null;
    }

    private void notifyParameterUpdated(String target, Object value) {
        if (onParameterUpdated != null) {
            onParameterUpdated.accept(target, value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<BaseMessage> extractHistory(Map<String, Object> kwargs) {
        Object history = kwargs.get("history");
        if (!(history instanceof List<?> list)) {
            return null;
        }
        List<BaseMessage> result = new ArrayList<>();
        for (Object item : list) {
            result.add((BaseMessage) item);
        }
        return result;
    }

    private List<BaseMessage> formatMessages(Map<String, Object> inputs, List<BaseMessage> history) {
        Object passthrough = inputs != null ? inputs.get("messages") : null;
        if (passthrough instanceof List<?> list) {
            return formatPassthrough(inputs, list);
        }
        return formatLlmInput(inputs, history);
    }

    private List<BaseMessage> formatLlmInput(Map<String, Object> inputs, List<BaseMessage> history) {
        List<BaseMessage> messages = new ArrayList<>();
        for (BaseMessage message : systemPrompt.format(inputs).toMessages()) {
            messages.add(SystemMessage.builder()
                    .content(message.getContent())
                    .name(message.getName())
                    .build());
        }
        if (history != null) {
            messages.addAll(history);
        }
        messages.addAll(userPrompt.format(inputs).toMessages());
        return messages;
    }

    private List<BaseMessage> formatPassthrough(Map<String, Object> inputs, List<?> passthroughMessages) {
        List<BaseMessage> messages = new ArrayList<>();
        for (BaseMessage message : systemPrompt.format(inputs).toMessages()) {
            messages.add(SystemMessage.builder()
                    .content(message.getContent())
                    .name(message.getName())
                    .build());
        }
        for (Object item : passthroughMessages) {
            messages.add((BaseMessage) item);
        }
        return messages;
    }

}
