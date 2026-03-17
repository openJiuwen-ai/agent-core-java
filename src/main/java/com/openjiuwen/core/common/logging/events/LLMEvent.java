/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/** LLM call related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class LLMEvent extends BaseLogEvent {
    private String modelName;
    private String modelProvider;
    private String query;
    private List<Map<String, Object>> messages;
    private List<Map<String, Object>> tools;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private String responseContent;
    private List<Map<String, Object>> toolCalls;
    private Map<String, Object> usage;
    private Double latencyMs;
    private boolean isStream;
    private Integer chunkIndex;
    private Map<String, Object> extraParams;
    private Double timeout;
    private String stop;
    private Integer maxRetries;

    public LLMEvent() {
        super();
        setModuleType(ModuleType.LLM);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "model_name", modelName);
        putIfNotNull(map, "model_provider", modelProvider);
        putIfNotNull(map, "query", query);
        putIfNotNull(map, "messages", messages);
        putIfNotNull(map, "tools", tools);
        putIfNotNull(map, "temperature", temperature);
        putIfNotNull(map, "max_tokens", maxTokens);
        putIfNotNull(map, "top_p", topP);
        putIfNotNull(map, "response_content", responseContent);
        putIfNotNull(map, "tool_calls", toolCalls);
        putIfNotNull(map, "usage", usage);
        putIfNotNull(map, "latency_ms", latencyMs);
        map.put("is_stream", isStream);
        putIfNotNull(map, "chunk_index", chunkIndex);
        putIfNotNull(map, "extra_params", extraParams);
        putIfNotNull(map, "timeout", timeout);
        putIfNotNull(map, "stop", stop);
        putIfNotNull(map, "max_retries", maxRetries);
    }
}
