/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Legacy compatibility implementation of the pre-operator LLMCall wrapper.
 *
 * <p>Mirrors Python's {@code LLMCall} in
 * {@code openjiuwen/core/operator/legacy/llm_call/base.py}.</p>
 */
public class LLMCall {

    public static final String DEFAULT_USER_PROMPT = "{{query}}";

    private final Model llm;
    private final String modelName;
    private final String llmCallId;
    private PromptTemplate systemPrompt;
    private PromptTemplate userPrompt;
    private boolean freezeSystemPrompt;
    private boolean freezeUserPrompt;
    private LegacyOptimizerCallback optimizerCallback;

    public LLMCall(String modelName,
                   Model llm,
                   Object systemPrompt,
                   Object userPrompt,
                   boolean freezeSystemPrompt,
                   boolean freezeUserPrompt,
                   String llmCallId) {
        this.llm = llm;
        this.modelName = modelName;
        this.systemPrompt = PromptTemplate.builder().content(normalizePromptContent(systemPrompt)).build();
        this.userPrompt = PromptTemplate.builder().content(resolveInitialUserPrompt(userPrompt)).build();
        this.freezeSystemPrompt = freezeSystemPrompt;
        this.freezeUserPrompt = freezeUserPrompt;
        this.llmCallId = llmCallId == null ? "llm_call" : llmCallId;
    }

    public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt) {
        this(modelName, llm, systemPrompt, userPrompt, false, true, "llm_call");
    }

    public CompletionStage<AssistantMessage> invoke(Map<String, Object> inputs,
                                                    Object session,
                                                    List<BaseMessage> history,
                                                    List<ToolInfo> tools) {
        Map<String, Object> safeInputs = safeInputs(inputs);
        List<BaseMessage> messages = formatLlmInput(safeInputs, history);
        CompletionStage<AssistantMessage> response = llm.invoke(messages, invokeOptions(tools));
        if (optimizerCallback == null) {
            return response;
        }
        return response.thenCompose(message -> {
            try {
                optimizerCallback.onComplete(llmCallId, safeInputs, message, session);
                return CompletableFuture.completedFuture(message);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        });
    }

    public CompletionStage<AssistantMessage> invoke(Map<String, Object> inputs, Object session) {
        return invoke(inputs, session, null, null);
    }

    public Iterator<AssistantMessageChunk> stream(Map<String, Object> inputs,
                                                  Object session,
                                                  List<BaseMessage> history,
                                                  List<ToolInfo> tools) {
        Map<String, Object> safeInputs = safeInputs(inputs);
        List<BaseMessage> messages = formatLlmInput(safeInputs, history);
        Iterator<AssistantMessageChunk> delegate = llm.stream(messages, invokeOptions(tools));
        return new LegacyStream(delegate, llmCallId, safeInputs, session, optimizerCallback);
    }

    public Iterator<AssistantMessageChunk> stream(Map<String, Object> inputs, Object session) {
        return stream(inputs, session, null, null);
    }

    public LegacyOptimizerCallback getOptimizerCallback() {
        return optimizerCallback;
    }

    public void setOptimizerCallback(LegacyOptimizerCallback callback) {
        this.optimizerCallback = callback;
    }

    public PromptTemplate getSystemPrompt() {
        return systemPrompt;
    }

    public PromptTemplate getUserPrompt() {
        return userPrompt;
    }

    public void updateSystemPrompt(Object systemPrompt) {
        if (!freezeSystemPrompt) {
            this.systemPrompt = PromptTemplate.builder().content(normalizePromptContent(systemPrompt)).build();
        }
    }

    public void updateUserPrompt(Object userPrompt) {
        if (!freezeUserPrompt) {
            this.userPrompt = PromptTemplate.builder().content(normalizePromptContent(userPrompt)).build();
        }
    }

    public void setFreezeSystemPrompt(boolean switchValue) {
        this.freezeSystemPrompt = switchValue;
    }

    public void setFreezeUserPrompt(boolean switchValue) {
        this.freezeUserPrompt = switchValue;
    }

    public boolean getFreezeSystemPrompt() {
        return freezeSystemPrompt;
    }

    public boolean getFreezeUserPrompt() {
        return freezeUserPrompt;
    }

    private List<BaseMessage> formatLlmInput(Map<String, Object> inputs, List<BaseMessage> history) {
        List<BaseMessage> systemMessages = new ArrayList<>();
        for (BaseMessage message : systemPrompt.format(inputs).toMessages()) {
            SystemMessage systemMessage = new SystemMessage(message.getContentAsString(), message.getName());
            systemMessages.add(systemMessage);
        }
        List<BaseMessage> userMessages = userPrompt.format(inputs).toMessages();
        List<BaseMessage> historyMessages = history == null ? List.of() : history;
        List<BaseMessage> messages = new ArrayList<>(systemMessages.size() + historyMessages.size() + userMessages.size());
        messages.addAll(systemMessages);
        messages.addAll(historyMessages);
        messages.addAll(userMessages);
        return messages;
    }

    private ModelInvokeOptions invokeOptions(List<ToolInfo> tools) {
        return ModelInvokeOptions.builder()
                .model(modelName)
                .tools(tools == null ? null : List.copyOf(tools))
                .build();
    }

    private static Map<String, Object> safeInputs(Map<String, Object> inputs) {
        return inputs == null ? Map.of() : inputs;
    }

    private static Object resolveInitialUserPrompt(Object userPrompt) {
        if (userPrompt == null) {
            return DEFAULT_USER_PROMPT;
        }
        if (userPrompt instanceof String text && text.isEmpty()) {
            return DEFAULT_USER_PROMPT;
        }
        if (userPrompt instanceof List<?> list && list.isEmpty()) {
            return DEFAULT_USER_PROMPT;
        }
        return normalizePromptContent(userPrompt);
    }

    private static Object normalizePromptContent(Object promptContent) {
        if (!(promptContent instanceof List<?> list)) {
            return promptContent;
        }
        if (list.stream().anyMatch(item -> !(item instanceof BaseMessage) && !(item instanceof Map<?, ?>))) {
            return promptContent;
        }
        List<BaseMessage> messages = new ArrayList<>();
        for (Object item : list) {
            messages.add(toMessage(item));
        }
        return messages;
    }

    private static BaseMessage toMessage(Object value) {
        if (value instanceof BaseMessage message) {
            return message;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new UserMessage(String.valueOf(value));
        }
        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> map.put(String.valueOf(key), mapValue));
        String role = String.valueOf(map.getOrDefault("role", "user"));
        Object content = map.getOrDefault("content", "");
        String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
        BaseMessage message;
        if ("system".equals(role)) {
            message = new SystemMessage(String.valueOf(content), name);
        } else if ("user".equals(role)) {
            message = new UserMessage(String.valueOf(content), name);
        } else {
            message = new BaseMessage(role, content);
            message.setName(name);
        }
        return message;
    }

    private static final class LegacyStream implements Iterator<AssistantMessageChunk>, AutoCloseable {

        private final Iterator<AssistantMessageChunk> delegate;
        private final String llmCallId;
        private final Map<String, Object> inputs;
        private final Object session;
        private final LegacyOptimizerCallback callback;
        private final StringBuilder response = new StringBuilder();
        private boolean closed;

        private LegacyStream(Iterator<AssistantMessageChunk> delegate,
                             String llmCallId,
                             Map<String, Object> inputs,
                             Object session,
                             LegacyOptimizerCallback callback) {
            this.delegate = delegate;
            this.llmCallId = llmCallId;
            this.inputs = inputs;
            this.session = session;
            this.callback = callback;
        }

        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        @Override
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunk = delegate.next();
            response.append(chunk == null ? "null" : chunk.getContentAsString());
            if (!delegate.hasNext()) {
                close();
            }
            return chunk;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (callback == null) {
                return;
            }
            try {
                callback.onComplete(llmCallId, inputs, response.toString(), session);
            } catch (Exception exception) {
                throw new IllegalStateException("Legacy LLMCall stream callback failed", exception);
            }
        }
    }
}
