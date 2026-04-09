  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Agent related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AgentEvent extends BaseLogEvent {
    private String agentType;
    private Map<String, Object> agentConfig;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private Integer iterationCount;
    private Integer maxIterations;
    private Double executionTimeMs;

    public AgentEvent() {
        super();
        setModuleType(ModuleType.AGENT);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "agent_type", agentType);
        putIfNotNull(map, "agent_config", agentConfig);
        putIfNotNull(map, "input_data", inputData);
        putIfNotNull(map, "output_data", outputData);
        putIfNotNull(map, "iteration_count", iterationCount);
        putIfNotNull(map, "max_iterations", maxIterations);
        putIfNotNull(map, "execution_time_ms", executionTimeMs);
    }
}
