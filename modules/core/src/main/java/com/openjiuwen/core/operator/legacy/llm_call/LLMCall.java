/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.legacy.llm_call;

import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.session.Session;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy compatibility implementation of the pre-operator LLMCall wrapper.
 * 
 * @since 0.1.7
 */
public class LLMCall {
    /**
     * DEFAULT_USER_PROMPT.
     * 
     * @since 0.1.7
     */
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
     * LLMCall.
     * 
     * @param modelName modelName
     * @param llm llm
     * @param systemPrompt systemPrompt
     * @param userPrompt userPrompt
     * @param freezeSystemPrompt freezeSystemPrompt
     * @param freezeUserPrompt freezeUserPrompt
     * @param llmCallId llmCallId
     * @since 0.1.7
     */
    public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt, boolean freezeSystemPrompt,
            boolean freezeUserPrompt, String llmCallId) {
        this.llm = llm;
        this.modelName = modelName;
        this.systemPrompt = PromptTemplate.builder().content(systemPrompt).build();
        this.userPrompt = PromptTemplate.builder().content(resolveInitialUserPrompt(userPrompt)).build();
        this.freezeSystemPrompt = freezeSystemPrompt;
        this.freezeUserPrompt = freezeUserPrompt;
        this.llmCallId = llmCallId != null ? llmCallId : "llm_call";
    }

    /**
     * LLMCall.
     * 
     * @param modelName modelName
     * @param llm llm
     * @param systemPrompt systemPrompt
     * @param userPrompt userPrompt
     * @since 0.1.7
     */
    public LLMCall(String modelName, Model llm, Object systemPrompt, Object userPrompt) {
        this(modelName, llm, systemPrompt, userPrompt, false, true, "llm_call");
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @param history history
     * @param tools tools
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public AssistantMessage invoke(Map<String, Object> inputs, Session session, List<BaseMessage> history, Object tools)
            throws Exception {
        List<BaseMessage> messages = formatLlmInput(inputs, history);
        AssistantMessage response =
            llm.invoke(messages, tools, null, null, modelName, null, null, null, null, Collections.emptyMap());
        if (optimizerCallback != null) {
            optimizerCallback.onComplete(llmCallId, inputs, response, session);
        }
        return response;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public AssistantMessage invoke(Map<String, Object> inputs, Session session) throws Exception {
        return invoke(inputs, session, null, null);
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @param history history
     * @param tools tools
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session,
            List<BaseMessage> history, Object tools) throws Exception {
        List<BaseMessage> messages = formatLlmInput(inputs, history);
        Iterator<AssistantMessageChunk> delegate =
            llm.stream(messages, tools, null, null, modelName, null, null, null, null, Collections.emptyMap());
        return new LegacyStream(delegate, llmCallId, inputs, session, optimizerCallback);
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public OperatorStream<AssistantMessageChunk> stream(Map<String, Object> inputs, Session session) throws Exception {
        return stream(inputs, session, null, null);
    }

    /**
     * Reactive version of {@link #invoke(Map, Session, List, Object)}.
     * 
     * @param inputs operator inputs
     * @param session session context
     * @param history chat history
     * @param tools available tools
     * @return Mono emitting the assistant message
     * @since 0.1.7
     */
    public Mono<AssistantMessage> invokeAsync(Map<String, Object> inputs, Session session, List<BaseMessage> history,
            Object tools) {
        return ReactiveAdapters.fromCallable(() -> invoke(inputs, session, history, tools));
    }

    /**
     * Reactive version of {@link #stream(Map, Session, List, Object)}.
     * 
     * @param inputs operator inputs
     * @param session session context
     * @param history chat history
     * @param tools available tools
     * @return Flux emitting assistant message chunks
     * @since 0.1.7
     */
    public Flux<AssistantMessageChunk> streamAsync(Map<String, Object> inputs, Session session,
            List<BaseMessage> history, Object tools) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(inputs, session, history, tools));
    }

    /**
     * getOptimizerCallback.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LegacyOptimizerCallback getOptimizerCallback() {
        return optimizerCallback;
    }

    /**
     * setOptimizerCallback.
     * 
     * @param optimizerCallback optimizerCallback
     * @since 0.1.7
     */
    public void setOptimizerCallback(LegacyOptimizerCallback optimizerCallback) {
        this.optimizerCallback = optimizerCallback;
    }

    /**
     * getSystemPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public PromptTemplate getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * getUserPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public PromptTemplate getUserPrompt() {
        return userPrompt;
    }

    /**
     * updateSystemPrompt.
     * 
     * @param systemPrompt systemPrompt
     * @since 0.1.7
     */
    public void updateSystemPrompt(Object systemPrompt) {
        if (!freezeSystemPrompt) {
            this.systemPrompt = PromptTemplate.builder().content(systemPrompt).build();
        }
    }

    /**
     * updateUserPrompt.
     * 
     * @param userPrompt userPrompt
     * @since 0.1.7
     */
    public void updateUserPrompt(Object userPrompt) {
        if (!freezeUserPrompt) {
            this.userPrompt = PromptTemplate.builder().content(userPrompt).build();
        }
    }

    /**
     * setFreezeSystemPrompt.
     * 
     * @param freezeSystemPrompt freezeSystemPrompt
     * @since 0.1.7
     */
    public void setFreezeSystemPrompt(boolean freezeSystemPrompt) {
        this.freezeSystemPrompt = freezeSystemPrompt;
    }

    /**
     * setFreezeUserPrompt.
     * 
     * @param freezeUserPrompt freezeUserPrompt
     * @since 0.1.7
     */
    public void setFreezeUserPrompt(boolean freezeUserPrompt) {
        this.freezeUserPrompt = freezeUserPrompt;
    }

    /**
     * getFreezeSystemPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean getFreezeSystemPrompt() {
        return freezeSystemPrompt;
    }

    /**
     * getFreezeUserPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean getFreezeUserPrompt() {
        return freezeUserPrompt;
    }

    /**
     * formatLlmInput.
     * 
     * @param inputs inputs
     * @param history history
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> formatLlmInput(Map<String, Object> inputs, List<BaseMessage> history) {
        List<BaseMessage> systemMessages = new ArrayList<>();
        for (BaseMessage message : systemPrompt.format(inputs).toMessages()) {
            systemMessages.add(SystemMessage.builder().content(message.getContent()).name(message.getName()).build());
        }
        List<BaseMessage> userMessages = userPrompt.format(inputs).toMessages();
        List<BaseMessage> historyMessages = history == null ? List.of() : history;
        List<BaseMessage> messages =
            new ArrayList<>(systemMessages.size() + historyMessages.size() + userMessages.size());
        messages.addAll(systemMessages);
        messages.addAll(historyMessages);
        messages.addAll(userMessages);
        return messages;
    }

    /**
     * resolveInitialUserPrompt.
     * 
     * @param userPrompt userPrompt
     * @return the result
     * @since 0.1.7
     */
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

        /**
         * StringBuilder.
         * 
         * @since 0.1.7
         */
        private final StringBuilder response = new StringBuilder();
        private boolean isClosed;

        /**
         * LegacyStream.
         * 
         * @param delegate delegate
         * @param llmCallId llmCallId
         * @param inputs inputs
         * @param session session
         * @param callback callback
         * @since 0.1.7
         */
        private LegacyStream(Iterator<AssistantMessageChunk> delegate, String llmCallId, Map<String, Object> inputs,
                Session session, LegacyOptimizerCallback callback) {
            this.delegate = delegate;
            this.llmCallId = llmCallId;
            this.inputs = inputs;
            this.session = session;
            this.callback = callback;
        }

        /**
         * hasNext.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public boolean hasNext() {
            boolean hasNext = delegate.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        /**
         * next.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public AssistantMessageChunk next() {
            AssistantMessageChunk chunk = delegate.next();
            response.append(chunk.getContent() == null ? "" : chunk.getContent());
            if (!delegate.hasNext()) {
                close();
            }
            return chunk;
        }

        /**
         * close.
         * 
         * @since 0.1.7
         */
        @Override
        public void close() {
            if (isClosed) {
                return;
            }
            isClosed = true;
            if (callback != null) {
                try {
                    callback.onComplete(llmCallId, inputs, response.toString(), session);
                } catch (Exception ignored) {

                    // Ignore.
                }
            }
        }
    }
}
