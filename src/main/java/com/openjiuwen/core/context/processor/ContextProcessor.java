/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.processor;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.OffloadMessages;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for all context-processing plug-ins.
 * <p>
 * A context processor can intervene at two life-cycle points:
 * <ol>
 *   <li>When new messages are about to be added ({@link #onAddMessages})</li>
 *   <li>When the context window is being materialized ({@link #onGetContextWindow})</li>
 * </ol>
 * Each processor decides <em>whether</em> to intervene via the corresponding
 * {@code trigger_*} method and, if so, <em>how</em> to intervene in the paired
 * {@code on_*} method.
 * <p>
 * Mirrors Python's {@code ContextProcessor} from {@code processor/base.py}.
 */
public abstract class ContextProcessor {

    private static final String OFFLOAD_MESSAGE_HANDLE = "[[OFFLOAD: handle=%s, type=%s]]";

    private final Object config;

    /**
     * Store the processor-specific configuration.
     *
     * @param config validated configuration object
     */
    protected ContextProcessor(Object config) {
        this.config = config;
    }

    // ------------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------------

    /**
     * Result from a processor hook. Contains the processed messages and/or
     * a modified context window.
     */
    public record ProcessResult(
            ContextEvent event,
            List<BaseMessage> messages,
            ContextWindow contextWindow
    ) {
        public static ProcessResult ofMessages(ContextEvent event, List<BaseMessage> messages) {
            return new ProcessResult(event, messages, null);
        }

        public static ProcessResult ofContextWindow(ContextEvent event, ContextWindow contextWindow) {
            return new ProcessResult(event, null, contextWindow);
        }
    }

    // ------------------------------------------------------------------
    // Processing hooks (synchronous – Python async → Java sync)
    // ------------------------------------------------------------------

    /**
     * Transform or filter the <b>incoming</b> message batch.
     * <p>
     * Called only when {@link #triggerAddMessages} returned {@code true}.
     * Default implementation is a no-op pass-through.
     */
    public ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        return ProcessResult.ofMessages(null, messagesToAdd);
    }

    /**
     * Mutate the <b>outgoing</b> context window (e.g. compress, reorder).
     * <p>
     * Called only when {@link #triggerGetContextWindow} returned {@code true}.
     * Default implementation is a no-op pass-through.
     */
    public ProcessResult onGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        return ProcessResult.ofContextWindow(null, contextWindow);
    }

    // ------------------------------------------------------------------
    // Trigger hooks
    // ------------------------------------------------------------------

    /**
     * Return {@code true} if this processor wants to intervene <b>before</b>
     * the messages are appended to the context.
     */
    public boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd) {
        return false;
    }

    /**
     * Return {@code true} if this processor wants to intervene <b>before</b>
     * the context window is returned to the caller.
     */
    public boolean triggerGetContextWindow(ModelContext context, ContextWindow contextWindow) {
        return false;
    }

    // ------------------------------------------------------------------
    // State persistence
    // ------------------------------------------------------------------

    /**
     * Restore internal state from a dictionary produced by {@link #saveState()}.
     */
    public abstract void loadState(Map<String, Object> state);

    /**
     * Export internal state to a serialisable map.
     */
    public abstract Map<String, Object> saveState();

    // ------------------------------------------------------------------
    // Introspection
    // ------------------------------------------------------------------

    /**
     * Return the registered processor type string (the simple class name).
     * Replaces Python's metaclass-set {@code __processor_type}.
     */
    public String processorType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Read-only access to the validated configuration object.
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig() {
        return (T) config;
    }

    // ------------------------------------------------------------------
    // Offload helpers
    // ------------------------------------------------------------------

    /**
     * Offload messages to in-memory storage and return a replacement marker message.
     *
     * @param role           the role of the replacement message
     * @param content        base content (offload marker will be appended)
     * @param messages       messages to store
     * @param context        the model context (must support offloading)
     * @param offloadHandle  unique handle; auto-generated if null
     * @param offloadType    storage type, defaults to "in_memory"
     * @return replacement message with offload marker, or null
     */
    protected BaseMessage offloadMessages(
            String role,
            String content,
            List<BaseMessage> messages,
            ModelContext context,
            String offloadHandle,
            String offloadType) {

        if (messages == null || messages.isEmpty()) {
            return null;
        }
        if (offloadHandle == null || offloadHandle.isEmpty()) {
            offloadHandle = UUID.randomUUID().toString().replace("-", "");
        }
        if (offloadType == null || offloadType.isEmpty()) {
            offloadType = "in_memory";
        }

        if ("in_memory".equals(offloadType)) {
            return offloadMessagesToMemory(role, content, messages, context, offloadHandle);
        }
        return null;
    }

    /**
     * Overloaded convenience method with defaults.
     */
    protected BaseMessage offloadMessages(
            String role,
            String content,
            List<BaseMessage> messages,
            ModelContext context) {
        return offloadMessages(role, content, messages, context, null, "in_memory");
    }

    private static BaseMessage offloadMessagesToMemory(
            String role,
            String content,
            List<BaseMessage> messages,
            ModelContext context,
            String offloadHandle) {

        String markedContent = content + String.format(OFFLOAD_MESSAGE_HANDLE, offloadHandle, "in_memory");

        if (context instanceof SessionModelContext sessionContext) {
            sessionContext.offloadMessages(offloadHandle, messages);
            BaseMessage offloadMsg = OffloadMessages.createOffloadMessage(role, markedContent, offloadHandle, "in_memory");
            // Preserve toolCallId from original tool message
            if ("tool".equals(role) && offloadMsg instanceof ToolMessage offloadTool && !messages.isEmpty()) {
                BaseMessage original = messages.get(0);
                if (original instanceof ToolMessage origTool && origTool.getToolCallId() != null) {
                    offloadTool.setToolCallId(origTool.getToolCallId());
                }
            }
            return offloadMsg;
        }
        return null;
    }
}
