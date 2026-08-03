/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * JSON file-based session storage.
 * <p>
 * Mirrors Python's {@code SessionStore} in
 * {@code openjiuwen/harness/cli/storage/session_store.py}.
 */
public class CliSessionStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storeDir;
    private StoredSession current;

    public CliSessionStore() {
        this(Path.of(System.getProperty("user.home"), ".openjiuwen", "sessions"));
    }

    public CliSessionStore(String storeDir) {
        this(Path.of(storeDir));
    }

    public CliSessionStore(Path storeDir) {
        this.storeDir = storeDir;
        try {
            Files.createDirectories(storeDir);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to create session store directory " + storeDir, error);
        }
    }

    public void newSession(String sessionId, String model) {
        current = new StoredSession(
                sessionId,
                model,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                new ArrayList<>()
        );
    }

    public void addMessage(String role, String content) {
        if (current == null) {
            return;
        }
        current.messages().add(new StoredMessage(
                role,
                content,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                null
        ));
        saveCurrent();
    }

    public List<Map<String, Object>> listSessions() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        try (Stream<Path> paths = Files.list(storeDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            Map<String, Object> data = OBJECT_MAPPER.readValue(
                                    Files.readString(path, StandardCharsets.UTF_8),
                                    new TypeReference<Map<String, Object>>() {
                                    }
                            );
                            Object sessionId = data.get("session_id");
                            Object model = data.get("model");
                            Object createdAt = data.get("created_at");
                            if (!(sessionId instanceof String)
                                    || !(model instanceof String)
                                    || !(createdAt instanceof String)) {
                                return;
                            }
                            int turns = 0;
                            Object messages = data.get("messages");
                            if (messages instanceof List<?> messageList) {
                                turns = messageList.size();
                            }
                            Map<String, Object> summary = new LinkedHashMap<>();
                            summary.put("id", sessionId);
                            summary.put("model", model);
                            summary.put("created_at", createdAt);
                            summary.put("turns", turns);
                            sessions.add(summary);
                        } catch (IOException | RuntimeException ignored) {
                            // Python skips malformed session files.
                        }
                    });
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to list sessions under " + storeDir, error);
        }
        return sessions;
    }

    StoredSession getCurrentSession() {
        return current;
    }

    private void saveCurrent() {
        if (current == null) {
            return;
        }
        Path path = storeDir.resolve(current.sessionId() + ".json");
        try {
            Files.writeString(path, OBJECT_MAPPER.writeValueAsString(current), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Failed to write session file " + path, error);
        }
    }

    /**
     * Mirrors Python's {@code StoredMessage} in
     * {@code openjiuwen/harness/cli/storage/session_store.py}.
     */
    public record StoredMessage(
            String role,
            String content,
            String timestamp,
            @JsonProperty("token_count") Integer tokenCount
    ) {
    }

    /**
     * Mirrors Python's {@code StoredSession} in
     * {@code openjiuwen/harness/cli/storage/session_store.py}.
     */
    public record StoredSession(
            @JsonProperty("session_id") String sessionId,
            String model,
            @JsonProperty("created_at") String createdAt,
            List<StoredMessage> messages
    ) {
    }
}
