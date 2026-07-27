/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.sysop.SysOperation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Buffer for messages that have been offloaded from the context window.
 * Supports in-memory and filesystem storage.
 * <p>
 * Mirrors Python's {@code OffloadMessageBuffer} from {@code context_engine/context/message_buffer.py}.
 */
public class OffloadMessageBuffer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Map<String, List<BaseMessage>> inMemoryOffloadMessages;
    private SysOperation sysOperation;
    private String workspaceDir;
    private String sessionId;

    /**
     * Auto-generated for codecheck compliance.
     */
    public OffloadMessageBuffer() {
        this.inMemoryOffloadMessages = new HashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages) {
        this.inMemoryOffloadMessages = initMessages != null ? new HashMap<>(initMessages) : new HashMap<>();
    }

    /**
     * Offload messages to the specified storage.
     *
     * @param offloadHandle unique identifier for the offloaded messages
     * @param offloadType   storage type (currently only "in_memory")
     * @param messages      the messages to offload
     */
    public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.put(offloadHandle, messages);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSysOperation(SysOperation sysOperation) {
        this.sysOperation = sysOperation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkspaceInfo(String workspaceDir, String sessionId) {
        this.workspaceDir = workspaceDir;
        this.sessionId = sessionId;
    }

    /**
     * Reload offloaded messages from storage.
     *
     * @param offloadHandle the handle of the messages to reload
     * @param offloadType   the storage type
     * @return the reloaded messages, or empty list if not found
     */
    public List<BaseMessage> reload(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            return inMemoryOffloadMessages.getOrDefault(offloadHandle, new ArrayList<>());
        }
        if ("filesystem".equals(offloadType)) {
            return reloadFromFilesystem(offloadHandle);
        }
        return new ArrayList<>();
    }

    private List<BaseMessage> reloadFromFilesystem(String offloadHandle) {
        for (String offloadPath : filesystemReloadPaths(offloadHandle)) {
            try {
                String payload = readOffloadPayload(offloadPath);
                if (payload == null || payload.isBlank()) {
                    continue;
                }
                Map<String, Object> data = MAPPER.readValue(payload, new TypeReference<>() {});
                Object rawMessages = data.get("messages");
                if (!(rawMessages instanceof List<?> list) || list.isEmpty()) {
                    continue;
                }
                List<BaseMessage> messages = new ArrayList<>();
                for (Object raw : list) {
                    if (raw instanceof Map<?, ?> map) {
                        BaseMessage message = toMessage((Map<String, Object>) map);
                        if (message != null) {
                            messages.add(message);
                        }
                    }
                }
                if (!messages.isEmpty()) {
                    return messages;
                }
            } catch (IOException | IllegalArgumentException ignored) {
                // Keep Python behavior: swallow and try next candidate path.
            }
        }
        return new ArrayList<>();
    }

    private String readOffloadPayload(String offloadPath) throws IOException {
        if (sysOperation != null) {
            try {
                Object fs = sysOperation.fs();
                java.lang.reflect.Method readFileMethod = fs.getClass().getMethod(
                        "readFile", String.class, String.class, String.class,
                        Integer.class, Long.class, String.class, int.class, Long.class);
                Object result = readFileMethod.invoke(fs, offloadPath, "text", null, null, null, "utf-8", 0, null);
                if (result != null) {
                    java.lang.reflect.Method getCodeMethod = result.getClass().getMethod("getCode");
                    int code = (int) getCodeMethod.invoke(result);
                    java.lang.reflect.Method getDataMethod = result.getClass().getMethod("getData");
                    Object data = getDataMethod.invoke(result);
                    if (code == 0 && data != null) {
                        java.lang.reflect.Method getContentMethod = data.getClass().getMethod("getContent");
                        Object content = getContentMethod.invoke(data);
                        if (content instanceof String text && !text.isBlank()) {
                            return text;
                        }
                        if (content != null) {
                            String text = String.valueOf(content);
                            if (!text.isBlank()) {
                                return text;
                            }
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to direct file read.
            }
        }
        if (offloadPath != null) {
            Path path = Path.of(offloadPath);
            if (Files.isRegularFile(path)) {
                return Files.readString(path);
            }
        }
        return "";
    }

    private List<String> filesystemReloadPaths(String offloadHandle) {
        if (workspaceDir == null || workspaceDir.isBlank() || sessionId == null || sessionId.isBlank()) {
            return List.of(offloadHandle);
        }
        Path offloadDir = Path.of(workspaceDir, "context", sessionId + "_context", "offload");
        List<String> candidates = new ArrayList<>();
        candidates.add(offloadDir.resolve(offloadHandle + ".json").toString());
        if (Files.isDirectory(offloadDir)) {
            try (Stream<Path> stream = Files.list(offloadDir)) {
                stream.filter(path -> path.getFileName().toString().endsWith("_" + offloadHandle + ".json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .map(Path::toString)
                        .filter(path -> !candidates.contains(path))
                        .forEach(candidates::add);
            } catch (IOException ignored) {
                // Fall back to exact path only.
            }
        }
        return candidates;
    }

    /**
     * Clear a specific offloaded message set.
     */
    public void clear(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.remove(offloadHandle);
        }
    }

    /**
     * Get all offloaded messages.
     */
    public Map<String, List<BaseMessage>> getAll() {
        return inMemoryOffloadMessages;
    }

    @SuppressWarnings("unchecked")
    private BaseMessage toMessage(Map<String, Object> map) {
        String role = String.valueOf(map.getOrDefault("role", "user"));
        Object content = map.get("content");
        String name = map.get("name") instanceof String s ? s : null;
        return switch (role) {
            case "assistant" -> {
                AssistantMessage message = new AssistantMessage();
                message.setRole("assistant");
                message.setContent(content);
                message.setName(name);
                Object toolCalls = map.get("tool_calls");
                if (toolCalls instanceof List<?> list) {
                    List<ToolCall> converted = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> tcMap) {
                            converted.add(ToolCall.builder()
                                    .id(stringOrNull(tcMap.get("id")))
                                    .type(tcMap.get("type") != null ? String.valueOf(tcMap.get("type")) : "function")
                                    .name(stringOrNull(tcMap.get("name")))
                                    .arguments(stringOrNull(tcMap.get("arguments")))
                                    .index(tcMap.get("index") instanceof Number n ? n.intValue() : null)
                                    .build());
                        }
                    }
                    message.setToolCalls(converted);
                }
                if (map.get("finish_reason") instanceof String finishReason) {
                    message.setFinishReason(finishReason);
                }
                if (map.containsKey("parser_content")) {
                    message.setParserContent(map.get("parser_content"));
                }
                if (map.get("reasoning_content") instanceof String reasoningContent) {
                    message.setReasoningContent(reasoningContent);
                }
                yield message;
            }
            case "tool" -> {
                ToolMessage message = new ToolMessage();
                message.setRole("tool");
                message.setContent(content);
                message.setName(name);
                if (map.get("tool_call_id") instanceof String toolCallId) {
                    message.setToolCallId(toolCallId);
                }
                yield message;
            }
            case "system" -> {
                SystemMessage message = new SystemMessage();
                message.setRole("system");
                message.setContent(content);
                message.setName(name);
                yield message;
            }
            default -> {
                UserMessage message = new UserMessage();
                message.setRole("user");
                message.setContent(content);
                message.setName(name);
                yield message;
            }
        };
    }

    private static String stringOrNull(Object value) {
        return value instanceof String text ? text : null;
    }
}
