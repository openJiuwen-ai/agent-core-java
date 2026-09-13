/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin / marker interface for offloaded messages.
 * <p>
 * Messages that have been offloaded from the context window carry an
 * offload handle and type so they can be later reloaded.
 * <p>
 * Mirrors Python's {@code OffloadMixin} from {@code context_engine/schema/messages.py}.
 * 
 * @since 0.1.7
 */
public final class OffloadMessages {
    /**
     * OffloadMessages.
     * 
     * @since 0.1.7
     */
    private OffloadMessages() {
    }

    // ==================== Offload Message Types ====================

    /**
     * OffloadUserMessage.
     * 
     * @since 0.1.7
     */
    @Data
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

        /**
         * getMetadata.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    /**
     * OffloadAssistantMessage.
     * 
     * @since 0.1.7
     */
    @Data
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

        /**
         * getMetadata.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    /**
     * OffloadSystemMessage.
     * 
     * @since 0.1.7
     */
    @Data
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

        /**
         * getMetadata.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }
    }

    /**
     * OffloadToolMessage.
     * 
     * @since 0.1.7
     */
    @Data
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

        /**
         * getMetadata.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> getMetadata() {
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            return metadata;
        }

        /**
         * builder.
         * 
         * @return the result
         * @since 0.1.7
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        public static class Builder extends ToolMessage.Builder {
            private String offloadType;
            private String offloadHandle;
            private Map<String, Object> metadata;

            /**
             * role.
             * 
             * @param role role
             * @return the result
             * @since 0.1.7
             */
            @Override
            public Builder role(String role) {
                super.role(role);
                return this;
            }

            /**
             * content.
             * 
             * @param content content
             * @return the result
             * @since 0.1.7
             */
            @Override
            public Builder content(Object content) {
                super.content(content);
                return this;
            }

            /**
             * name.
             * 
             * @param name name
             * @return the result
             * @since 0.1.7
             */
            @Override
            public Builder name(String name) {
                super.name(name);
                return this;
            }

            /**
             * metadata.
             * 
             * @param metadata metadata
             * @return the result
             * @since 0.1.7
             */
            @Override
            public Builder metadata(Map<String, Object> metadata) {
                super.metadata(metadata);
                this.metadata = metadata;
                return this;
            }

            /**
             * toolCallId.
             * 
             * @param toolCallId toolCallId
             * @return the result
             * @since 0.1.7
             */
            @Override
            public Builder toolCallId(String toolCallId) {
                super.toolCallId(toolCallId);
                return this;
            }

            /**
             * offloadType.
             * 
             * @param offloadType offloadType
             * @return the result
             * @since 0.1.7
             */
            public Builder offloadType(String offloadType) {
                this.offloadType = offloadType;
                return this;
            }

            /**
             * offloadHandle.
             * 
             * @param offloadHandle offloadHandle
             * @return the result
             * @since 0.1.7
             */
            public Builder offloadHandle(String offloadHandle) {
                this.offloadHandle = offloadHandle;
                return this;
            }

            /**
             * build.
             * 
             * @return the result
             * @since 0.1.7
             */
            public OffloadToolMessage build() {
                OffloadToolMessage message = new OffloadToolMessage();
                message.setRole(role);
                message.setContent(content);
                message.setName(name);
                message.setToolCallId(toolCallId);
                message.setMetadata(metadata);
                message.setOffloadType(offloadType);
                message.setOffloadHandle(offloadHandle);
                return message;
            }
        }
    }

    // ==================== Factory Method ====================

    /**
     * Create an offloaded message of the appropriate type based on role.
     * 
     * @param role message role (user, assistant, system, tool)
     * @param content the (compressed/trimmed) content
     * @param offloadHandle unique handle for reloading
     * @param offloadType storage type (e.g., "in_memory")
     * @return an offload message instance
     * @since 0.1.7
     */
    public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
            String offloadType) {
        return createOffloadMessage(role, content, offloadHandle, offloadType, null);
    }

    /**
     * Create an offloaded message of the appropriate type based on role,
     * preserving additional fields from the original message.
     * <p>
     * Mirrors Python's {@code create_offload_message(..., **kwargs)} which passes
     * through extra fields like {@code tool_call_id}, {@code tool_calls},
     * {@code usage_metadata}, {@code finish_reason}, {@code parser_content},
     * {@code reasoning_content}, and {@code name}.
     * 
     * @param role message role (user, assistant, system, tool)
     * @param content the (compressed/trimmed) content
     * @param offloadHandle unique handle for reloading
     * @param offloadType storage type (e.g., "in_memory")
     * @param extraFields additional fields from the original message to preserve; may be null
     * @return an offload message instance with preserved fields
     * @since 0.1.7
     */
    public static BaseMessage createOffloadMessage(String role, String content, String offloadHandle,
            String offloadType, Map<String, Object> extraFields) {
        return switch (role) {
            case "assistant" -> {
                var msg = new OffloadAssistantMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                applyAssistantExtraFields(msg, extraFields);
                yield msg;
            }
            case "tool" -> {
                var msg = new OffloadToolMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                applyToolExtraFields(msg, extraFields);
                yield msg;
            }
            case "system" -> {
                var msg = new OffloadSystemMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                applyBaseExtraFields(msg, extraFields);
                yield msg;
            }
            default -> {
                var msg = new OffloadUserMessage();
                msg.setContent(content);
                msg.setOffloadHandle(offloadHandle);
                msg.setOffloadType(offloadType);
                applyBaseExtraFields(msg, extraFields);
                yield msg;
            }
        };
    }

    // ==================== Extra Fields Helpers ====================

    /**
     * applyBaseExtraFields.
     * 
     * @param msg msg
     * @param extraFields extraFields
     * @since 0.1.7
     */
    private static void applyBaseExtraFields(BaseMessage msg, Map<String, Object> extraFields) {
        if (extraFields == null) {
            return;
        }
        Object name = extraFields.get("name");
        if (name instanceof String s) {
            msg.setName(s);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * applyAssistantExtraFields.
     * 
     * @param msg msg
     * @param extraFields extraFields
     * @since 0.1.7
     */
    private static void applyAssistantExtraFields(OffloadAssistantMessage msg, Map<String, Object> extraFields) {
        applyBaseExtraFields(msg, extraFields);
        if (extraFields == null) {
            return;
        }
        Object toolCalls = extraFields.get("tool_calls");
        if (toolCalls instanceof java.util.List<?> list) {
            msg.setToolCalls((java.util.List<com.openjiuwen.core.foundation.llm.schema.ToolCall>) toolCalls);
        }
        Object usageMetadata = extraFields.get("usage_metadata");
        if (usageMetadata instanceof com.openjiuwen.core.foundation.llm.schema.UsageMetadata um) {
            msg.setUsageMetadata(um);
        }
        Object finishReason = extraFields.get("finish_reason");
        if (finishReason instanceof String s) {
            msg.setFinishReason(s);
        }
        Object parserContent = extraFields.get("parser_content");
        if (parserContent != null) {
            msg.setParserContent(parserContent);
        }
        Object reasoningContent = extraFields.get("reasoning_content");
        if (reasoningContent instanceof String s) {
            msg.setReasoningContent(s);
        }
    }

    /**
     * applyToolExtraFields.
     * 
     * @param msg msg
     * @param extraFields extraFields
     * @since 0.1.7
     */
    private static void applyToolExtraFields(OffloadToolMessage msg, Map<String, Object> extraFields) {
        applyBaseExtraFields(msg, extraFields);
        if (extraFields == null) {
            return;
        }
        Object toolCallId = extraFields.get("tool_call_id");
        if (toolCallId instanceof String s) {
            msg.setToolCallId(s);
        }
    }
}
