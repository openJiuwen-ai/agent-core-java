/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streaming assistant message chunk with tool call fragment merging.
 * <p>
 * Mirrors Python's {@code AssistantMessageChunk} model. Tool call fragments
 * from the same call are concatenated rather than appended as new elements.
 * 
 * @since 0.1.7
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantMessageChunk extends AssistantMessage {
    private static final LoggerProtocol LOG = Loggers.LLM;

    /**
     * AssistantMessageChunk.
     * 
     * @since 0.1.7
     */
    public AssistantMessageChunk() {
    }

    /**
     * Merge another chunk into this one, combining content and tool call fragments.
     * <p>
     * Tool call fragments are merged by a stable key with priority:
     * {@code index > id > name > anonymous}. This is more tolerant than the previous
     * "compare with the last element" approach and works for OpenAI / DeepSeek / GLM
     * streaming formats, which differ in how they populate id, index, and name on
     * incremental argument chunks.
     * 
     * @param other the chunk to merge
     * @return a new merged chunk
     * @since 0.1.7
     */
    public AssistantMessageChunk merge(AssistantMessageChunk other) {
        if (other == null) {
            return this;
        }

        // Merge content
        Object combinedContent = BaseMessageChunk.mergeContent(this.getContent(), other.getContent());

        // Merge tool_calls by bucketing fragments on a stable key per call
        LinkedHashMap<Object, ToolCall> bucket = new LinkedHashMap<>();
        if (this.getToolCalls() != null) {
            for (ToolCall tc : this.getToolCalls()) {
                bucket.put(keyOf(tc), cloneOf(tc));
            }
        }

        if (other.getToolCalls() != null) {
            for (ToolCall incoming : other.getToolCalls()) {
                Object key = keyOf(incoming);
                ToolCall exist = bucket.get(key);
                if (exist != null) {
                    appendFragment(exist, incoming);
                    logMerge("hit", key, exist, incoming);
                    continue;
                }
                // Fallback: a pure-arguments fragment (no id / no name / only args) is
                // an argument continuation of the most recent call regardless of key.
                if (isPureArgumentsFragment(incoming) && !bucket.isEmpty()) {
                    ToolCall last = lastValue(bucket);
                    appendFragment(last, incoming);
                    logMerge("fallback-args", keyOf(last), last, incoming);
                    continue;
                }
                // Fallback: incoming has a name but no id/index and bucket has exactly one
                // entry whose name is still empty — treat as the same call (name arrives late).
                if (hasOwnName(incoming) && bucket.size() == 1) {
                    ToolCall only = lastValue(bucket);
                    if (!hasOwnName(only) && only.getArguments() != null) {
                        appendFragment(only, incoming);
                        logMerge("fallback-name", keyOf(only), only, incoming);
                        continue;
                    }
                }
                bucket.put(key, cloneOf(incoming));
                logMerge("new", key, null, incoming);
            }
        }

        List<ToolCall> mergedToolCalls = new ArrayList<>(bucket.values());

        String mergedFinishReason =
            !"null".equals(other.getFinishReason()) ? other.getFinishReason() : this.getFinishReason();

        return AssistantMessageChunk.builder().role(this.getRole()).content(combinedContent)
                .toolCalls(mergedToolCalls.isEmpty() ? null : mergedToolCalls)
                .usageMetadata(other.getUsageMetadata() != null ? other.getUsageMetadata() : this.getUsageMetadata())
                .finishReason(mergedFinishReason)
                .parserContent(other.getParserContent() != null ? other.getParserContent() : this.getParserContent())
                .reasoningContent(
                        other.getReasoningContent() != null ? other.getReasoningContent() : this.getReasoningContent())
                .build();
    }

    /**
     * keyOf.
     * 
     * @param tc tc
     * @return the result
     * @since 0.1.7
     */
    private static Object keyOf(ToolCall tc) {
        if (tc == null) {
            return "anon";
        }
        if (tc.getIndex() != null) {
            return "idx:" + tc.getIndex();
        }
        if (tc.getId() != null && !tc.getId().isEmpty()) {
            return "id:" + tc.getId();
        }
        if (tc.getName() != null && !tc.getName().isEmpty()) {
            return "name:" + tc.getName();
        }
        return "anon";
    }

    /**
     * cloneOf.
     * 
     * @param src src
     * @return the result
     * @since 0.1.7
     */
    private static ToolCall cloneOf(ToolCall src) {
        if (src == null) {
            return null;
        }
        return ToolCall.builder().id(src.getId()).type(src.getType()).name(src.getName()).arguments(src.getArguments())
                .index(src.getIndex()).build();
    }

    /**
     * lastValue.
     * 
     * @param bucket bucket
     * @return the result
     * @since 0.1.7
     */
    private static ToolCall lastValue(Map<Object, ToolCall> bucket) {
        ToolCall last = null;
        for (ToolCall v : bucket.values()) {
            last = v;
        }
        return last;
    }

    /**
     * appendFragment.
     * 
     * @param base base
     * @param inc inc
     * @since 0.1.7
     */
    private static void appendFragment(ToolCall base, ToolCall inc) {
        if (base.getId() == null || base.getId().isEmpty()) {
            base.setId(inc.getId());
        }
        if (base.getType() == null || base.getType().isEmpty()) {
            base.setType(inc.getType() != null ? inc.getType() : "function");
        }
        if (base.getName() == null || base.getName().isEmpty()) {
            base.setName(inc.getName());
        } else if (inc.getName() != null && !inc.getName().isEmpty() && !base.getName().equals(inc.getName())) {
            // Some providers repeat the name on every fragment. Only append when it
            // actually differs to avoid name duplication like "skill_toolskill_tool".
            // If names disagree across fragments for the same key, keep the first one.
            LOG.debug("[merge] name conflict on key={}, keeping existing={}, incoming={}", keyOf(base), base.getName(),
                    inc.getName());
        } else {
            // names match or incoming name is empty
        }
        if (base.getIndex() == null && inc.getIndex() != null) {
            base.setIndex(inc.getIndex());
        }
        base.setArguments(orEmpty(base.getArguments()) + orEmpty(inc.getArguments()));
    }

    /**
     * isPureArgumentsFragment.
     * 
     * @param tc tc
     * @return the result
     * @since 0.1.7
     */
    private static boolean isPureArgumentsFragment(ToolCall tc) {
        if (tc == null) {
            return false;
        }
        boolean noId = tc.getId() == null || tc.getId().isEmpty();
        boolean noName = tc.getName() == null || tc.getName().isEmpty();
        boolean noIndex = tc.getIndex() == null;
        boolean hasArgs = tc.getArguments() != null && !tc.getArguments().isEmpty();
        return noId && noName && noIndex && hasArgs;
    }

    /**
     * hasOwnName.
     * 
     * @param tc tc
     * @return the result
     * @since 0.1.7
     */
    private static boolean hasOwnName(ToolCall tc) {
        return tc != null && tc.getName() != null && !tc.getName().isEmpty();
    }

    /**
     * logMerge.
     * 
     * @param action action
     * @param key key
     * @param base base
     * @param incoming incoming
     * @since 0.1.7
     */
    private static void logMerge(String action, Object key, ToolCall base, ToolCall incoming) {
        LoggerProtocol logger = Loggers.LLM;
        if (logger == null) {
            return;
        }
        logger.debug("[merge] {} key={} base={} incoming={}", action, key,
                base == null ? "<none>" : formatToolCall(base), formatToolCall(incoming));
    }

    /**
     * formatToolCall.
     * 
     * @param tc tc
     * @return the result
     * @since 0.1.7
     */
    private static String formatToolCall(ToolCall tc) {
        if (tc == null) {
            return "<null>";
        }
        return "{id=" + tc.getId() + ", type=" + tc.getType() + ", name=" + tc.getName() + ", index=" + tc.getIndex()
                + ", argsLen=" + (tc.getArguments() == null ? 0 : tc.getArguments().length()) + ", args="
                + tc.getArguments() + "}";
    }

    /**
     * orEmpty.
     * 
     * @param s s
     * @return the result
     * @since 0.1.7
     */
    private static String orEmpty(String s) {
        return s != null ? s : "";
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
    public static class Builder extends AssistantMessage.Builder {
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
        public Builder metadata(java.util.Map<String, Object> metadata) {
            super.metadata(metadata);
            return this;
        }

        /**
         * toolCalls.
         * 
         * @param toolCalls toolCalls
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder toolCalls(List<ToolCall> toolCalls) {
            super.toolCalls(toolCalls);
            return this;
        }

        /**
         * usageMetadata.
         * 
         * @param usageMetadata usageMetadata
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder usageMetadata(UsageMetadata usageMetadata) {
            super.usageMetadata(usageMetadata);
            return this;
        }

        /**
         * finishReason.
         * 
         * @param finishReason finishReason
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder finishReason(String finishReason) {
            super.finishReason(finishReason);
            return this;
        }

        /**
         * parserContent.
         * 
         * @param parserContent parserContent
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder parserContent(Object parserContent) {
            super.parserContent(parserContent);
            return this;
        }

        /**
         * reasoningContent.
         * 
         * @param reasoningContent reasoningContent
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder reasoningContent(String reasoningContent) {
            super.reasoningContent(reasoningContent);
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public AssistantMessageChunk build() {
            AssistantMessageChunk chunk = new AssistantMessageChunk();
            chunk.setRole(role);
            chunk.setContent(content);
            chunk.setName(name);
            chunk.setMetadata(metadata);
            chunk.setToolCalls(toolCalls);
            chunk.setUsageMetadata(usageMetadata);
            chunk.setFinishReason(finishReason);
            chunk.setParserContent(parserContent);
            chunk.setReasoningContent(reasoningContent);
            return chunk;
        }
    }
}
