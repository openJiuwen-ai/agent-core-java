/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.List;
import java.util.Map;

/**
 * Backward-compatible alias for the pre-0.1.14 budget processor.
 *
 * <p>Delegates all logic to
 * {@link com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor}.</p>
 */
public class ToolResultBudgetProcessor extends MessageOffloader {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String PERSISTED_OUTPUT_TAG =
            com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor.PERSISTED_OUTPUT_TAG;
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String PERSISTED_OUTPUT_CLOSING_TAG =
            com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor.PERSISTED_OUTPUT_CLOSING_TAG;

    private final com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor delegate;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolResultBudgetProcessor(ToolResultBudgetProcessorConfig config) {
        super(toOffloaderConfig(config));
        com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig newConfig =
                toNewConfig(config);
        this.delegate = new com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessor(newConfig);
    }

    @Override
    public void loadState(Map<String, Object> state) {
        delegate.loadState(state);
    }

    @Override
    public Map<String, Object> saveState() {
        return delegate.saveState();
    }

    @Override
    public java.util.concurrent.CompletionStage<Boolean> triggerAddMessages(
            com.openjiuwen.core.context_engine.context.SessionModelContext context,
            List<BaseMessage> messagesToAdd,
            Map<String, Object> kwargs) {
        return delegate.triggerAddMessages(context, messagesToAdd, kwargs);
    }

    @Override
    public java.util.concurrent.CompletionStage<com.openjiuwen.core.context_engine.context.SessionModelContext.ProcessResult> onAddMessages(
            com.openjiuwen.core.context_engine.context.SessionModelContext context,
            List<BaseMessage> messagesToAdd,
            boolean force,
            Map<String, Object> kwargs) {
        return delegate.onAddMessages(context, messagesToAdd, force, kwargs);
    }

    @Override
    public java.util.concurrent.CompletionStage<Boolean> triggerGetContextWindow(
            com.openjiuwen.core.context_engine.context.SessionModelContext context,
            com.openjiuwen.core.context_engine.ContextWindow window,
            Map<String, Object> kwargs) {
        return delegate.triggerGetContextWindow(context, window, kwargs);
    }

    @Override
    public java.util.concurrent.CompletionStage<com.openjiuwen.core.context_engine.context.SessionModelContext.ProcessResult> onGetContextWindow(
            com.openjiuwen.core.context_engine.context.SessionModelContext context,
            com.openjiuwen.core.context_engine.ContextWindow window,
            Map<String, Object> kwargs) {
        return delegate.onGetContextWindow(context, window, kwargs);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolResultBudgetProcessorConfig getToolResultConfig() {
        return fromNewConfig(delegate.getBudgetConfig());
    }

    boolean isAlreadyOffloaded(ToolMessage message) {
        return message instanceof com.openjiuwen.core.context_engine.schema.OffloadToolMessage;
    }

    private static MessageOffloaderConfig toOffloaderConfig(ToolResultBudgetProcessorConfig config) {
        if (config == null) {
            config = ToolResultBudgetProcessorConfig.builder().build();
        }
        return MessageOffloaderConfig.builder()
                .messagesThreshold(config.getMessagesThreshold())
                .messagesToKeep(config.getMessagesToKeep())
                .tokensThreshold(config.getTokensThreshold())
                .largeMessageThreshold(config.getLargeMessageThreshold())
                .trimSize(config.getTrimSize())
                .offloadMessageType(List.of("tool"))
                .keepLastRound(false)
                .build();
    }

    private static com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig toNewConfig(
            ToolResultBudgetProcessorConfig config) {
        if (config == null) {
            return new com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig();
        }
        com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig newConfig =
                new com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig();
        newConfig.setTokensThreshold(config.getTokensThreshold());
        newConfig.setLargeMessageThreshold(config.getLargeMessageThreshold());
        newConfig.setTrimSize(config.getTrimSize());
        newConfig.setOffloadFilePrefix(config.getOffloadFilePrefix());
        newConfig.setToolNameAllowlist(config.getToolNameAllowlist());
        return newConfig;
    }

    @SuppressWarnings("unchecked")
    private static ToolResultBudgetProcessorConfig fromNewConfig(
            com.openjiuwen.core.context_engine.processor.offloader.ToolResultBudgetProcessorConfig newConfig) {
        ToolResultBudgetProcessorConfig config = ToolResultBudgetProcessorConfig.builder().build();
        config.setTokensThreshold(newConfig.getTokensThreshold());
        config.setLargeMessageThreshold(newConfig.getLargeMessageThreshold());
        config.setTrimSize(newConfig.getTrimSize());
        config.setOffloadFilePrefix(newConfig.getOffloadFilePrefix());
        config.setToolNameAllowlist(newConfig.getToolNameAllowlist());
        return config;
    }
}
