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
import com.openjiuwen.core.sysop.result.ReadFileResult;

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
 * 
 * @since 0.1.7
 */
public class OffloadMessageBuffer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Map<String, List<BaseMessage>> inMemoryOffloadMessages;
    private SysOperation sysOperation;
    private String workspaceDir;
    private String sessionId;

    /**
     * OffloadMessageBuffer.
     * 
     * @since 0.1.7
     */
    public OffloadMessageBuffer() {
        this.inMemoryOffloadMessages = new HashMap<>();
    }

    /**
     * OffloadMessageBuffer.
     * 
     * @param initMessages initMessages
     * @since 0.1.7
     */
    public OffloadMessageBuffer(Map<String, List<BaseMessage>> initMessages) {
        this.inMemoryOffloadMessages = initMessages != null ? new HashMap<>(initMessages) : new HashMap<>();
    }

    /**
     * Offload messages to the specified storage.
     * 
     * @param offloadHandle unique identifier for the offloaded messages
     * @param offloadType storage type (currently only "in_memory")
     * @param messages the messages to offload
     * @since 0.1.7
     */
    public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.put(offloadHandle, messages);
        }
    }

    /**
     * setSysOperation.
     * 
     * @param sysOperation sysOperation
     * @since 0.1.7
     */
    public void setSysOperation(SysOperation sysOperation) {
        this.sysOperation = sysOperation;
    }

    /**
     * setWorkspaceInfo.
     * 
     * @param workspaceDir workspaceDir
     * @param sessionId sessionId
     * @since 0.1.7
     */
    public void setWorkspaceInfo(String workspaceDir, String sessionId) {
        this.workspaceDir = workspaceDir;
        this.sessionId = sessionId;
    }

    /**
     * Reload offloaded messages from storage.
     * 
     * @param offloadHandle the handle of the messages to reload
     * @param offloadType the storage type
     * @return the reloaded messages, or empty list if not found
     * @since 0.1.7
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

    /**
     * reloadFromFilesystem.
     * 
     * @param offloadHandle offloadHandle
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> reloadFromFilesystem(String offloadHandle) {
        for (String offloadPath : filesystemReloadPaths(offloadHandle)) {
            try {
                String payload = readOffloadPayload(offloadPath);
                if (payload == null || payload.isBlank()) {
                    continue;
                }
                Map<String, Object> data = MAPPER.readValue(payload, new TypeReference<>() {
                });
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

    /**
     * readOffloadPayload.
     * 
     * @param offloadPath offloadPath
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    private String readOffloadPayload(String offloadPath) throws IOException {
        if (sysOperation != null) {
            ReadFileResult result = sysOperation.fs().readFile(offloadPath, "text", null, null, null, "utf-8", 0, null);
            if (result.getCode() == 0 && result.getData() != null) {
                return result.getData().getContentAsString();
            }
            return "";
        }
        return "";
    }

    /**
     * filesystemReloadPaths.
     * 
     * @param offloadHandle offloadHandle
     * @return the result
     * @since 0.1.7
     */
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
                        .sorted(Comparator.comparing(Path::toString)).map(Path::toString)
                        .filter(path -> !candidates.contains(path)).forEach(candidates::add);
            } catch (IOException ignored) {
                // Fall back to exact path only.
            }
        }
        return candidates;
    }

    /**
     * Clear a specific offloaded message set.
     * 
     * @param offloadHandle offloadHandle
     * @param offloadType offloadType
     * @since 0.1.7
     */
    public void clear(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.remove(offloadHandle);
        }
    }

    /**
     * Get all offloaded messages.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<BaseMessage>> getAll() {
        return inMemoryOffloadMessages;
    }

    @SuppressWarnings("unchecked")
    /**
     * toMessage.
     * 
     * @param map map
     * @return the result
     * @since 0.1.7
     */
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
                            converted.add(ToolCall.builder().id(stringOrNull(tcMap.get("id")))
                                    .type(tcMap.get("type") != null ? String.valueOf(tcMap.get("type")) : "function")
                                    .name(stringOrNull(tcMap.get("name")))
                                    .arguments(stringOrNull(tcMap.get("arguments")))
                                    .index(tcMap.get("index") instanceof Number n ? n.intValue() : null).build());
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

    /**
     * stringOrNull.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringOrNull(Object value) {
        return value instanceof String text ? text : null;
    }
}
