/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Workflow related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WorkflowEvent extends BaseLogEvent {
    private String workflowId;
    private String workflowName;
    private String componentId;
    private String componentName;
    private String componentTypeStr;
    private String branchCondition;
    private String selectedBranch;
    private Map<String, Object> inputs;
    private Object outputs;
    private Object chunk;
    private Integer chunkIdx;
    private Map<String, Object> outputData;
    private Double executionTimeMs;

    public WorkflowEvent() {
        super();
        setModuleType(ModuleType.WORKFLOW);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "workflow_id", workflowId);
        putIfNotNull(map, "workflow_name", workflowName);
        putIfNotNull(map, "component_id", componentId);
        putIfNotNull(map, "component_name", componentName);
        putIfNotNull(map, "component_type_str", componentTypeStr);
        putIfNotNull(map, "branch_condition", branchCondition);
        putIfNotNull(map, "selected_branch", selectedBranch);
        putIfNotNull(map, "inputs", inputs);
        putIfNotNull(map, "outputs", outputs);
        putIfNotNull(map, "chunk", chunk);
        putIfNotNull(map, "chunk_idx", chunkIdx);
        putIfNotNull(map, "output_data", outputData);
        putIfNotNull(map, "execution_time_ms", executionTimeMs);
    }
}
