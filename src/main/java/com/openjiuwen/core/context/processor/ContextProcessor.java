/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.OffloadMessages;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for all context-processing plug-ins.
 *
 * <p>Mirrors Python's {@code ContextProcessor} in
 * {@code openjiuwen/core/context_engine/processor/base.py}.</p>
 *
 * @since 0.1.7
 */
public abstract class ContextProcessor {

    /**
     * Offload handle marker format for in-memory offloaded messages.
     */
    public static final String OFFLOAD_MESSAGE_HANDLE = "[[OFFLOAD: handle=%s, type=%s]]";

    /**
     * Offload handle marker format for filesystem offloaded messages.
     */
    public static final String OFFLOAD_MESSAGE_HANDLE_WITH_PATH = "[[OFFLOAD: type=%s, path=%s]]";

    private final Object config;

    /**
     * Construct a ContextProcessor with the given configuration.
     *
     * @param config processor configuration
     */
    protected ContextProcessor(Object config) {
        this.config = config;
    }

    /**
     * Return the processor type name (defaults to the simple class name).
     *
     * @return processor type name
     */
    public String processorType() {
        return getClass().getSimpleName();
    }

    /**
     * Return the typed configuration object.
     *
     * @param <T> configuration type
     * @return the configuration cast to the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig() {
        return (T) config;
    }

    /**
     * Determine whether the processor should trigger on add-messages.
     *
     * @param context       the model context
     * @param messagesToAdd the messages being added
     * @return true if the processor should activate
     */
    public abstract boolean triggerAddMessages(ModelContext context, List<BaseMessage> messagesToAdd);

    /**
     * Process messages being added to the context.
     *
     * @param context       the model context
     * @param messagesToAdd the messages being added
     * @return the processing result
     */
    public abstract ProcessResult onAddMessages(ModelContext context, List<BaseMessage> messagesToAdd);

    /**
     * Determine whether the processor should trigger on get-context-window.
     *
     * @param context       the model context
     * @param contextWindow the context window being built
     * @return true if the processor should activate
     */
    public abstract boolean triggerGetContextWindow(ModelContext context, ContextWindow contextWindow);

    /**
     * Process the context window being constructed.
     *
     * @param context       the model context
     * @param contextWindow the context window
     * @return the processing result
     */
    public abstract ProcessResult onGetContextWindow(ModelContext context, ContextWindow contextWindow);

    /**
     * Load processor state from a persisted map.
     *
     * @param state the state map
     */
    public abstract void loadState(Map<String, Object> state);

    /**
     * Save processor state to a map for persistence.
     *
     * @return the state map
     */
    public abstract Map<String, Object> saveState();

    /**
     * Offload messages to memory or filesystem storage.
     *
     * @param role         message role
     * @param content      trimmed content
     * @param messages     original messages to offload
     * @param context      the model context
     * @param offloadHandle offload handle identifier
     * @param offloadType  storage type ("in_memory" or "filesystem")
     * @param offloadPath  filesystem path (may be null for in-memory)
     * @param extraFields  additional fields to preserve
     * @return the offload replacement message, or null on failure
     */
    protected BaseMessage offloadMessages(
            String role,
            String content,
            List<BaseMessage> messages,
            ModelContext context,
            String offloadHandle,
            String offloadType,
            String offloadPath,
            Map<String, Object> extraFields) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        String effectiveHandle = isBlank(offloadHandle)
                ? UUID.randomUUID().toString().replace("-", "")
                : offloadHandle;
        if (context == null) {
            return null;
        }
        String effectiveType = isBlank(offloadType) ? "filesystem" : offloadType;

        if ("in_memory".equals(effectiveType)) {
            return offloadMessagesToMemory(role, content, messages, context, effectiveHandle, extraFields);
        }
        if ("filesystem".equals(effectiveType)) {
            String path = isBlank(offloadPath)
                    ? generateOffloadPath(context.workspaceDir(), context.sessionId(), effectiveHandle)
                    : offloadPath;
            boolean writeSuccess = writeOffloadToFile(context.sessionId(), effectiveHandle, path, messages);
            if (!writeSuccess) {
                return offloadMessagesToMemory(role, content, messages, context, effectiveHandle, extraFields);
            }
            return offloadMessagesToFilesystem(role, content, effectiveHandle, path, extraFields);
        }
        return null;
    }

    /**
     * Generate a filesystem path for offloaded messages.
     *
     * @param workspaceDir  workspace directory
     * @param sessionId     session identifier
     * @param offloadHandle offload handle
     * @return the generated path string
     */
    public static String generateOffloadPath(String workspaceDir, String sessionId, String offloadHandle) {
        if (!isBlank(workspaceDir)) {
            return Path.of(workspaceDir, "context", sessionId + "_context", "offload",
                    offloadHandle + ".json").toString();
        }
        return Path.of("memory", "offloads", sessionId, offloadHandle + ".json").toString();
    }

    private static BaseMessage offloadMessagesToMemory(
            String role, String content, List<BaseMessage> messages,
            ModelContext context, String offloadHandle, Map<String, Object> kwargs) {
        String markedContent = String.valueOf(content)
                + String.format(OFFLOAD_MESSAGE_HANDLE, offloadHandle, "in_memory");
        if (context instanceof SessionModelContext smc) {
            smc.offloadMessages(offloadHandle, messages);
        }
        return OffloadMessages.createOffloadMessage(role, markedContent, offloadHandle, "in_memory", kwargs);
    }

    private static BaseMessage offloadMessagesToFilesystem(
            String role, String content, String offloadHandle,
            String offloadPath, Map<String, Object> kwargs) {
        String markedContent;
        if (!isBlank(offloadPath)) {
            markedContent = String.valueOf(content)
                    + String.format(OFFLOAD_MESSAGE_HANDLE_WITH_PATH, "filesystem", offloadPath);
        } else {
            markedContent = String.valueOf(content)
                    + String.format(OFFLOAD_MESSAGE_HANDLE, offloadHandle, "filesystem");
        }
        return OffloadMessages.createOffloadMessage(role, markedContent, offloadHandle, "filesystem", kwargs);
    }

    private static boolean writeOffloadToFile(
            String sessionId, String offloadHandle, String offloadPath,
            List<BaseMessage> messages) {
        String filePath = isBlank(offloadPath)
                ? Path.of("memory", "offloads", sessionId, offloadHandle + ".json").toString()
                : offloadPath;
        try {
            com.fasterxml.jackson.databind.ObjectMapper jsonMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("offload_handle", offloadHandle);
            payload.put("messages", messages.stream().map(BaseMessage::modelDump).toList());
            String jsonContent = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Path path = Path.of(filePath);
            if (!path.isAbsolute()) {
                return false;
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, jsonContent, StandardCharsets.UTF_8);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Result record returned by context processor operations.
     *
     * @param event         the context event describing modifications, or null
     * @param messages      the resulting messages (for onAddMessages), or null
     * @param contextWindow the resulting context window (for onGetContextWindow), or null
     */
    public record ProcessResult(ContextEvent event, List<BaseMessage> messages, ContextWindow contextWindow) {

        /**
         * Create a ProcessResult for add-messages operations.
         *
         * @param event    the context event
         * @param messages the resulting messages
         * @return a new ProcessResult
         */
        public static ProcessResult ofMessages(ContextEvent event, List<BaseMessage> messages) {
            return new ProcessResult(event, messages, null);
        }

        /**
         * Create a ProcessResult for get-context-window operations.
         *
         * @param event         the context event
         * @param contextWindow the resulting context window
         * @return a new ProcessResult
         */
        public static ProcessResult ofContextWindow(ContextEvent event, ContextWindow contextWindow) {
            return new ProcessResult(event, null, contextWindow);
        }
    }
}
