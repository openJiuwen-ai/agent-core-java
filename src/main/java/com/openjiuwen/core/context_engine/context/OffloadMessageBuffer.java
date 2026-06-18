/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

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
        this.inMemoryOffloadMessages = initMessages == null ? new LinkedHashMap<>() : new LinkedHashMap<>(initMessages);
    }

    public void setSysOperation(SysOperationPort sysOperation) {
        this.sysOperation = sysOperation;
    }

    public void setWorkspaceInfo(String workspaceDir, String sessionId) {
        this.workspaceDir = workspaceDir;
        this.sessionId = sessionId;
    }

    public void offload(String offloadHandle, String offloadType, List<BaseMessage> messages) {
        if ("in_memory".equals(offloadType)) {
            inMemoryOffloadMessages.put(offloadHandle, messages);
        }
    }

    public CompletionStage<List<BaseMessage>> reload(String offloadHandle, String offloadType) {
        if ("in_memory".equals(offloadType)) {
            return CompletableFuture.completedFuture(inMemoryOffloadMessages.getOrDefault(offloadHandle, List.of()));
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
        return inMemoryOffloadMessages;
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
                return paths;
            }
        }
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
            Object messagesData = payload.get("messages");
            if (!(messagesData instanceof List<?> rawMessages)) {
                return List.of();
            }
            List<BaseMessage> messages = new ArrayList<>();
            for (Object rawMessage : rawMessages) {
                messages.add(JSON_MAPPER.convertValue(rawMessage, BaseMessage.class));
            }
            return messages;
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            return List.of();
        }
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
