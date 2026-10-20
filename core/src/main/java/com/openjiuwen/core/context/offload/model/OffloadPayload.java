/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persisted offload payload, JSON {@code schema_version=2}
 * (共享约定 6).
 *
 * <p>Field order is fixed: {@code schema_version, offload_handle, namespace,
 * created_at, expires_at, content_sha256, messages[]}. The sha256 is computed
 * over the compact JSON serialization of the {@code messages} array in
 * UTF-8 hex; verification recomputes the digest after deserialization.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffloadPayload(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("offload_handle") String offloadHandle,
        @JsonProperty("namespace") OffloadNamespace namespace,
        @JsonProperty("created_at") long createdAt,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("content_sha256") String contentSha256,
        @JsonProperty("messages") List<Map<String, Object>> messages) {

    /** Current payload schema version (设计文档 v2). */
    public static final String SCHEMA_VERSION = "2";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Builds a payload from live messages, computing the integrity digest.
     *
     * @param handle    offload handle
     * @param namespace owning namespace
     * @param messages  original messages (serialized via {@code BaseMessage.modelDump()})
     * @param expiresAt epoch millis expiry, may be {@code null}
     * @return a new {@link OffloadPayload} with computed sha256 and fixed schema version
     */
    public static OffloadPayload create(String handle, OffloadNamespace namespace, List<BaseMessage> messages,
                                        Long expiresAt) {
        List<Map<String, Object>> dumps = messages == null ? List.of()
                : messages.stream().map(OffloadPayload::dumpMessage).toList();
        long now = System.currentTimeMillis();
        return new OffloadPayload(SCHEMA_VERSION, handle, namespace, now, expiresAt,
                sha256Hex(compactJson(dumps)), dumps);
    }

    /**
     * Serializes this payload into compact (non-pretty) JSON with the fixed field order.
     *
     * @return compact (non-pretty) JSON with the fixed field order
     */
    public String serializeJson() {
        try {
            return JSON_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OffloadPayload serialization failed", ex);
        }
    }

    /**
     * Parses a stored JSON payload; numeric sha256/field checks stay with
     * the service layer.
     *
     * @param json the compact JSON string produced by {@link #serializeJson()}
     * @return a deserialized {@link OffloadPayload} instance
     */
    public static OffloadPayload deserializeJson(String json) {
        try {
            return JSON_MAPPER.readValue(json, OffloadPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("OffloadPayload deserialization failed", ex);
        }
    }

    /**
     * Verifies the integrity of the stored payload by recomputing the sha256
     * digest of the deserialized messages and comparing it against the stored
     * value.
     *
     * @return {@code true} when the stored sha256 matches a freshly computed
     * digest of the deserialized messages
     */
    public boolean sha256Matches() {
        return contentSha256 != null && contentSha256.equals(sha256Hex(compactJson(messages)));
    }

    /**
     * Restores the persisted dump form back to live {@link BaseMessage}
     * instances. Role dispatch mirrors the legacy buffer's message parsing:
     * {@code assistant} (re-attaches {@code tool_calls} via
     * {@code setToolCallsRaw}), {@code tool} (re-attaches
     * {@code tool_call_id}), {@code system}, {@code user}; plus
     * {@code name} and {@code metadata} reconstruction.
     *
     * @return a fresh mutable list, never {@code null}; empty when the
     * payload holds no restorable messages
     */
    public List<BaseMessage> toBaseMessages() {
        List<BaseMessage> restored = new ArrayList<>();
        if (messages == null) {
            return restored;
        }
        for (Map<String, Object> dump : messages) {
            restoreMessage(dump).ifPresent(restored::add);
        }
        return restored;
    }

    private static Optional<BaseMessage> restoreMessage(Map<String, Object> rawMap) {
        if (rawMap == null) {
            // G.MET.06: null input → empty (caller skips via ifPresent)
            return Optional.empty();
        }
        Map<String, Object> map = new LinkedHashMap<>(rawMap);
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
        return Optional.of(message);
    }

    private static Map<String, Object> dumpMessage(BaseMessage message) {
        Map<String, Object> dump = message == null ? new LinkedHashMap<>() : message.modelDump();
        return new LinkedHashMap<>(dump);
    }

    private static String compactJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Offload payload digest serialization failed", ex);
        }
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }
}
