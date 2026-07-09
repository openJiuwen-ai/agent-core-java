/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Workflow streaming event — for workflow component streaming.
 * <p>
 * Mirrors Python's {@code WorkflowStreamEvent} which extends {@code StreamEvent}
 * with workflow-specific fields.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WorkflowStreamEvent extends StreamEvent {
    private String workflowId;
    private String workflowName;
    private String componentId;
    private String componentName;
    private String componentTypeStr;

    /**
     * WorkflowStreamEvent.
     * 
     * @since 0.1.7
     */
    public WorkflowStreamEvent() {
        super();
        setModuleType(ModuleType.WORKFLOW_COMPONENT);
    }

    /**
     * addFieldsToMap.
     * 
     * @param map map
     * @since 0.1.7
     */
    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        super.addFieldsToMap(map);
        putIfNotNull(map, "workflow_id", workflowId);
        putIfNotNull(map, "workflow_name", workflowName);
        putIfNotNull(map, "component_id", componentId);
        putIfNotNull(map, "component_name", componentName);
        putIfNotNull(map, "component_type_str", componentTypeStr);
    }
}
