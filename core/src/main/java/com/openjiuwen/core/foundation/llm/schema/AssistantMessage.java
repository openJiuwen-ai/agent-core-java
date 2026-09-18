/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code AssistantMessage} in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssistantMessage extends BaseMessage {

    private List<ToolCall> toolCalls;

    @JsonProperty("usage_metadata")
    private UsageMetadata usageMetadata;

    @Builder.Default
    @JsonProperty("finish_reason")
    private String finishReason = "null";

    @JsonProperty("parser_content")
    private Object parserContent;

    @JsonProperty("reasoning_content")
    private String reasoningContent;

    @JsonProperty("prompt_token_ids")
    private List<Integer> promptTokenIds;

    @JsonProperty("completion_token_ids")
    private List<Integer> completionTokenIds;

    @JsonProperty("logprobs")
    private Object logprobs;

    public AssistantMessage(String content) {
        super("assistant", content);
        this.finishReason = "null";
    }

    @Override
    public String getRole() {
        String value = super.getRole();
        return value != null ? value : "assistant";
    }

    public static List<ToolCall> convertOpenAiToolCalls(List<Map<String, Object>> rawToolCalls) {
        return normalizeToolCalls(rawToolCalls);
    }

    @JsonProperty("tool_calls")
    public void setToolCallsRaw(List<?> rawToolCalls) {
        this.toolCalls = normalizeToolCalls(rawToolCalls);
    }

    @Override
    public Map<String, Object> modelDump() {
        Map<String, Object> result = super.modelDump();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<Map<String, Object>> serializedCalls = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                Map<String, Object> callMap = new LinkedHashMap<>();
                callMap.put("id", call.getId());
                callMap.put("type", call.getType());
                Map<String, Object> functionMap = new LinkedHashMap<>();
                functionMap.put("name", call.getName());
                functionMap.put("arguments", call.getArguments());
                callMap.put("function", functionMap);
                if (call.getIndex() != null) {
                    callMap.put("index", call.getIndex());
                }
                serializedCalls.add(callMap);
            }
            result.put("tool_calls", serializedCalls);
        }
        if (usageMetadata != null) {
            result.put("usage_metadata", usageMetadata.modelDump());
        }
        if (finishReason != null) {
            result.put("finish_reason", finishReason);
        }
        if (parserContent != null) {
            result.put("parser_content", parserContent);
        }
        if (reasoningContent != null) {
            result.put("reasoning_content", reasoningContent);
        }
        if (promptTokenIds != null) {
            result.put("prompt_token_ids", promptTokenIds);
        }
        if (completionTokenIds != null) {
            result.put("completion_token_ids", completionTokenIds);
        }
        if (logprobs != null) {
            result.put("logprobs", logprobs);
        }
        return result;
    }

    public Map<String, Object> toApiFormat() {
        return modelDump();
    }

    public Map<String, Object> model_dump() {
        return modelDump();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AssistantMessage that) || !super.equals(other)) {
            return false;
        }
        return Objects.equals(toolCalls, that.toolCalls)
                && Objects.equals(usageMetadata, that.usageMetadata)
                && Objects.equals(normalizeFinishReason(finishReason), normalizeFinishReason(that.finishReason))
                && Objects.equals(parserContent, that.parserContent)
                && Objects.equals(reasoningContent, that.reasoningContent)
                && Objects.equals(promptTokenIds, that.promptTokenIds)
                && Objects.equals(completionTokenIds, that.completionTokenIds)
                && Objects.equals(logprobs, that.logprobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                toolCalls,
                usageMetadata,
                normalizeFinishReason(finishReason),
                parserContent,
                reasoningContent,
                promptTokenIds,
                completionTokenIds,
                logprobs
        );
    }

    private static String normalizeFinishReason(String value) {
        return value == null || "null".equals(value) ? null : value;
    }

    @SuppressWarnings("unchecked")
    private static List<ToolCall> normalizeToolCalls(List<?> rawToolCalls) {
        if (rawToolCalls == null || rawToolCalls.isEmpty()) {
            return null;
        }
        List<ToolCall> result = new ArrayList<>();
        for (Object rawToolCall : rawToolCalls) {
            if (rawToolCall instanceof ToolCall toolCall) {
                result.add(toolCall);
                continue;
            }
            if (!(rawToolCall instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> callMap = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> callMap.put(String.valueOf(key), value));
            Object functionValue = callMap.get("function");
            if (functionValue instanceof Map<?, ?> functionMap) {
                Map<String, Object> normalizedFunction = new LinkedHashMap<>();
                functionMap.forEach((key, value) -> normalizedFunction.put(String.valueOf(key), value));
                result.add(ToolCall.builder()
                        .id((String) callMap.get("id"))
                        .type((String) callMap.getOrDefault("type", "function"))
                        .name((String) normalizedFunction.getOrDefault("name", ""))
                        .arguments((String) normalizedFunction.getOrDefault("arguments", ""))
                        .index(callMap.get("index") instanceof Number number ? number.intValue() : null)
                        .build());
                continue;
            }
            result.add(ToolCall.builder()
                    .id((String) callMap.get("id"))
                    .type((String) callMap.getOrDefault("type", "function"))
                    .name((String) callMap.getOrDefault("name", ""))
                    .arguments((String) callMap.getOrDefault("arguments", ""))
                    .index(callMap.get("index") instanceof Number number ? number.intValue() : null)
                    .build());
        }
        return result;
    }
}
