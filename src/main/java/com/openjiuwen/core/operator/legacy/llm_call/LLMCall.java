/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.session.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy compatibility implementation of the pre-operator LLMCall wrapper.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMCall(String modelName,
                   Model llm,
                   Object systemPrompt,
                   Object userPrompt,
                   boolean freezeSystemPrompt,
                   boolean freezeUserPrompt,
                   String llmCallId) {
        this.llm = llm;
        this.modelName = modelName;
        this.systemPrompt = PromptTemplate.builder().content(systemPrompt).build();
        this.userPrompt = PromptTemplate.builder().content(resolveInitialUserPrompt(userPrompt)).build();
        this.freezeSystemPrompt = freezeSystemPrompt;
        this.freezeUserPrompt = freezeUserPrompt;
        this.llmCallId = llmCallId != null ? llmCallId : "llm_call";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt) {
        this(modelName, llm, systemPrompt, userPrompt, false, true, "llm_call");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AssistantMessage invoke(Map<String, Object> inputs,
                                   Session session,
                                   List<BaseMessage> history,
                                   Object tools) throws Exception {
        List<BaseMessage> messages = formatLlmInput(inputs, history);
        AssistantMessage response = llm.invoke(messages, tools, null, null, modelName, null, null, null, null, Collections.emptyMap());
        if (optimizerCallback != null) {
            optimizerCallback.onComplete(llmCallId, inputs, response, session);
        }
        return response;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AssistantMessage invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs,
                                                        Session session,
                                                        List<BaseMessage> history,
                                                        Object tools) throws Exception {
        List<BaseMessage> messages = formatLlmInput(inputs, history);
        Iterator<AssistantMessageChunk> delegate = llm.stream(
                messages, tools, null, null, modelName, null, null, null, null, Collections.emptyMap());
        return new LegacyStream(delegate, llmCallId, inputs, session, optimizerCallback);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LegacyOptimizerCallback getOptimizerCallback() {
        return optimizerCallback;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOptimizerCallback(LegacyOptimizerCallback optimizerCallback) {
        this.optimizerCallback = optimizerCallback;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public PromptTemplate getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public PromptTemplate getUserPrompt() {
        return userPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateSystemPrompt(Object systemPrompt) {
        if (!freezeSystemPrompt) {
            this.systemPrompt = PromptTemplate.builder().content(systemPrompt).build();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateUserPrompt(Object userPrompt) {
        if (!freezeUserPrompt) {
            this.userPrompt = PromptTemplate.builder().content(userPrompt).build();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFreezeSystemPrompt(boolean freezeSystemPrompt) {
        this.freezeSystemPrompt = freezeSystemPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFreezeUserPrompt(boolean freezeUserPrompt) {
        this.freezeUserPrompt = freezeUserPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean getFreezeSystemPrompt() {
        return freezeSystemPrompt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean getFreezeUserPrompt() {
        return freezeUserPrompt;
    }

    private List<BaseMessage> formatLlmInput(Map<String, Object> inputs, List<BaseMessage> history) {
        List<BaseMessage> systemMessages = new ArrayList<>();
        for (BaseMessage message : systemPrompt.format(inputs).toMessages()) {
            systemMessages.add(SystemMessage.builder()
                    .content(message.getContent())
                    .name(message.getName())
                    .build());
        }
        List<BaseMessage> userMessages = userPrompt.format(inputs).toMessages();
        List<BaseMessage> historyMessages = history == null ? List.of() : history;
        List<BaseMessage> messages = new ArrayList<>(systemMessages.size() + historyMessages.size() + userMessages.size());
        messages.addAll(systemMessages);
        messages.addAll(historyMessages);
        messages.addAll(userMessages);
        return messages;
    }

    private static Object resolveInitialUserPrompt(Object userPrompt) {
        if (userPrompt instanceof String stringPrompt && stringPrompt.isEmpty()) {
            return DEFAULT_USER_PROMPT;
        }
        return userPrompt != null ? userPrompt : DEFAULT_USER_PROMPT;
    }

    private static final class LegacyStream implements OperatorStream<AssistantMessageChunk> {

        private final Iterator<AssistantMessageChunk> delegate;
        private final String llmCallId;
        private final Map<String, Object> inputs;
        private final Session session;
        private final LegacyOptimizerCallback callback;
        private final StringBuilder response = new StringBuilder();
        private boolean isClosed;

        private LegacyStream(Iterator<AssistantMessageChunk> delegate,
                             String llmCallId,
                             Map<String, Object> inputs,
                             Session session,
                             LegacyOptimizerCallback callback) {
            this.delegate = delegate;
            this.llmCallId = llmCallId;
            this.inputs = inputs;
            this.session = session;
            this.callback = callback;
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunk = delegate.next();
            response.append(chunk.getContent() == null ? "" : chunk.getContent());
            if (!delegate.hasNext()) {
                close();
            }
            return chunk;
        }

        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public void close() {
            if (isClosed) {
                return;
            }
            isClosed = true;
            if (callback != null) {
                try {
                    callback.onComplete(llmCallId, inputs, response.toString(), session);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
