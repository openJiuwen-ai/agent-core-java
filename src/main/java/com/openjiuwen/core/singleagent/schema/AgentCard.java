/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent card format definition.
 *
 * <p>Mirrors Python's {@code AgentCard} in
 * {@code openjiuwen/core/single_agent/schema/agent_card.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard extends BaseCard {

    @JsonProperty("input_params")
    private Object inputParams;

    @JsonProperty("output_params")
    private Object outputParams;

    @JsonProperty("interface_url")
    private String interfaceUrl;

    public AgentCard() {
        super();
    }

    public AgentCard(String id, String name, String description) {
        super(id, name, description);
    }

    public Object getInputParams() {
        return inputParams;
    }

    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    public Object getOutputParams() {
        return outputParams;
    }

    public void setOutputParams(Object outputParams) {
        this.outputParams = outputParams;
    }

    public String getInterfaceUrl() {
        return interfaceUrl;
    }

    public void setInterfaceUrl(String interfaceUrl) {
        this.interfaceUrl = interfaceUrl;
    }

    @Override
    public ToolInfo toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(parametersMap(inputParams))
                .build();
    }

    private static Map<String, Object> parametersMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
        return normalized;
    }
}
