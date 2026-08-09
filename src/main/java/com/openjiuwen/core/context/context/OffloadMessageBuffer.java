/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Storage for messages offloaded from a context window.
 *
 * <p>Mirrors Python's {@code OffloadMessageBuffer} in
 * {@code openjiuwen/core/context_engine/context/message_buffer.py}.</p>
 */
public class OffloadMessageBuffer {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Map<String, List<BaseMessage>> inMemoryOffloadMessages;
    private SysOperationPort sysOperation;
    private String workspaceDir;
    private String sessionId;

    public OffloadMessageBuffer() {
        this(null);
    }

    public OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages) {
        this.inMemoryOffloadMessages = new LinkedHashMap<>();
        if (initMessages != null) {
            initMessages.forEach((key, value) -> this.inMemoryOffloadMessages.put(key, copyMessages(value)));
        }
    }

    public void setSysOperation(SysOperationPort sysOperation) {
        this.sysOperation = sysOperation;
    }

    public void setWorkspaceInfo(String workspaceDir, String sessionId) {
        this.workspaceDir = workspaceDir == null ? "" : workspaceDir;
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.put(offloadHandle, copyMessages(messages));
        }
    }

    public CompletionStage<List<BaseMessage>> reload(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            return CompletableFuture.completedFuture(
                    copyMessages(inMemoryOffloadMessages.getOrDefault(offloadHandle, List.of())));
        }
        if ("filesystem".equals(offloadType)) {
            return CompletableFuture.completedFuture(reloadFromFilesystem(offloadHandle));
        }
        return CompletableFuture.completedFuture(List.of());
    }

    public List<BaseMessage> reloadBlocking(String offloadHandle, String offloadType) {
        return reload(offloadHandle, offloadType).toCompletableFuture().join();
    }

    public void clear(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.remove(offloadHandle);
        }
    }

    public Map<String, List<BaseMessage>> getAll() {
        Map<String, List<BaseMessage>> result = new LinkedHashMap<>();
        inMemoryOffloadMessages.forEach((key, value) -> result.put(key, copyMessages(value)));
        return result;
    }

    public List<String> filesystemReloadPaths(String offloadHandle) {
        if (isBlank(workspaceDir) || isBlank(sessionId)) {
            return List.of(offloadHandle);
        }
        Path offloadDir = Path.of(workspaceDir, "context", sessionId + "_context", "offload");
        List<String> paths = new ArrayList<>();
        Path exactPath = offloadDir.resolve(offloadHandle + ".json");
        paths.add(exactPath.toString());
        if (Files.isDirectory(offloadDir)) {
            try (Stream<Path> stream = Files.list(offloadDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith("_" + offloadHandle + ".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .map(Path::toString)
                        .filter(path -> !paths.contains(path))
                        .forEach(paths::add);
            } catch (IOException ignored) {
                paths.add(offloadHandle);
                return paths;
            }
        }
        paths.add(offloadHandle);
        return paths;
    }

    private List<BaseMessage> reloadFromFilesystem(String offloadHandle) {
        if (sysOperation == null) {
            return List.of();
        }
        for (String offloadPath : filesystemReloadPaths(offloadHandle)) {
            Optional<String> rawContent = sysOperation.readFile(offloadPath);
            if (rawContent.isEmpty() || rawContent.get().isBlank()) {
                continue;
            }
            List<BaseMessage> messages = parseMessages(rawContent.get());
            if (!messages.isEmpty()) {
                return messages;
            }
        }
        return List.of();
    }

    private static List<BaseMessage> parseMessages(String rawContent) {
        try {
            Map<String, Object> payload = JSON_MAPPER.readValue(rawContent, new TypeReference<Map<String, Object>>() {
            });
            return asMessageList(payload.get("messages"));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BaseMessage> asMessageList(Object value) {
        if (!(value instanceof List<?> rawMessages)) {
            return List.of();
        }
        List<BaseMessage> messages = new ArrayList<>();
        for (Object rawMessage : rawMessages) {
            if (rawMessage instanceof BaseMessage message) {
                messages.add(message);
                continue;
            }
            BaseMessage parsed = messageFromMap(rawMessage);
            if (parsed != null) {
                messages.add(parsed);
            }
        }
        return messages;
    }

    private static BaseMessage messageFromMap(Object item) {
        if (!(item instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));
        String role = map.get("role") == null ? "user" : String.valueOf(map.get("role"));
        Object content = map.getOrDefault("content", "");
        BaseMessage message;
        if ("assistant".equals(role)) {
            AssistantMessage assistantMessage = new AssistantMessage(content == null ? "" : String.valueOf(content));
            if (map.get("tool_calls") instanceof List<?> toolCalls) {
                assistantMessage.setToolCallsRaw(toolCalls);
            }
            message = assistantMessage;
        } else if ("tool".equals(role)) {
            ToolMessage toolMessage = new ToolMessage("", map.get("tool_call_id") == null
                    ? "" : String.valueOf(map.get("tool_call_id")));
            toolMessage.setContent(content == null ? "" : content);
            message = toolMessage;
        } else if ("system".equals(role)) {
            message = new SystemMessage(content == null ? "" : String.valueOf(content));
        } else {
            message = new UserMessage(content == null ? "" : String.valueOf(content));
        }
        if (map.get("name") instanceof String name) {
            message.setName(name);
        }
        if (map.get("metadata") instanceof Map<?, ?> metadataMap) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadataMap.forEach((key, metadataValue) -> metadata.put(String.valueOf(key), metadataValue));
            message.setMetadata(metadata);
        }
        return message;
    }

    private static List<BaseMessage> copyMessages(List<BaseMessage> messages) {
        return messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Narrow file-read adapter for filesystem offload reload.
     *
     * <p>Mirrors Python's {@code sys_operation.fs().read_file(...)} collaborator in
     * {@code openjiuwen/core/context_engine/context/message_buffer.py}.</p>
     */
    public interface SysOperationPort {
        Optional<String> readFile(String path);
    }
}
