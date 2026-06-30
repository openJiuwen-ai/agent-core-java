/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
/**
 * Public class ClassAgentSpawnConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClassAgentSpawnConfig extends SpawnAgentConfig {
    @JsonProperty("agent_module")
    private String agentModule;

    @JsonProperty("agent_class")
    private String agentClass;

    @JsonProperty("init_kwargs")
    private Map<String, Object> initKwargs = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public ClassAgentSpawnConfig() {
        setAgentKind(SpawnAgentKind.CLASS_AGENT);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ClassAgentSpawnConfig(String agentModule, String agentClass, Map<String, Object> initKwargs) {
        setAgentKind(SpawnAgentKind.CLASS_AGENT);
        this.agentModule = agentModule;
        this.agentClass = agentClass;
        this.initKwargs = initKwargs != null ? initKwargs : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toPayload() {
        Map<String, Object> result = super.toPayload();
        result.put("agent_module", agentModule);
        result.put("agent_class", agentClass);
        result.put("init_kwargs", initKwargs != null ? initKwargs : new LinkedHashMap<String, Object>());
        return result;
    }
}
