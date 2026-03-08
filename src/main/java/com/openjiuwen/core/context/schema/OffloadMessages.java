/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin / marker interface for offloaded messages.
 * <p>
 * Messages that have been offloaded from the context window carry an
 * offload handle and type so they can be later reloaded.
 * <p>
 * Mirrors Python's {@code OffloadMixin} from {@code context_engine/schema/messages.py}.
 */
public final class OffloadMessages {

    private OffloadMessages() {
    }

    // ==================== Offload Message Types ====================

    /**
     * User message that has been offloaded.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffloadUserMessage extends UserMessage implements OffloadMixin {

        @JsonProperty("offload_type")
        private String offloadType;

        @JsonProperty("offload_handle")
        private String offloadHandle;

        private Map<String, Object> metadata;

        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    /**
     * Assistant message that has been offloaded.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffloadAssistantMessage extends AssistantMessage implements OffloadMixin {

        @JsonProperty("offload_type")
        private String offloadType;

        @JsonProperty("offload_handle")
        private String offloadHandle;

        private Map<String, Object> metadata;

        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    /**
     * System message that has been offloaded.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffloadSystemMessage extends SystemMessage implements OffloadMixin {

        @JsonProperty("offload_type")
        private String offloadType;

        @JsonProperty("offload_handle")
        private String offloadHandle;

        private Map<String, Object> metadata;

        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    /**
     * Tool message that has been offloaded.
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OffloadToolMessage extends ToolMessage implements OffloadMixin {

        @JsonProperty("offload_type")
        private String offloadType;

        @JsonProperty("offload_handle")
        private String offloadHandle;

        private Map<String, Object> metadata;

        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    // ==================== Factory Method ====================

    /**
     * Create an offloaded message of the appropriate type based on role.
     *
     * @param role          message role (user, assistant, system, tool)
     * @param content       the (compressed/trimmed) content
     * @param offloadHandle unique handle for reloading
     * @param offloadType   storage type (e.g., "in_memory")
     * @return an offload message instance
     */
    public static BaseMessage createOffloadMessage(
            String role,
            String content,
            String offloadHandle,
            String offloadType) {

        return switch (role) {
            case "assistant" -> {
                var msg = new OffloadAssistantMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                yield msg;
            }
            case "tool" -> {
                var msg = new OffloadToolMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                yield msg;
            }
            case "system" -> {
                var msg = new OffloadSystemMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                yield msg;
            }
            default -> {
                var msg = new OffloadUserMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                yield msg;
            }
        };
    }
}
