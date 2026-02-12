// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata card for a workflow.
 * 
 * <p>Contains descriptive information and input schema for a workflow.
 * 
 * <p><strong>Note:</strong> This is a placeholder class. Full implementation
 * will be completed when the workflow module is converted.
 * 
 * @see BaseCard
 */
public class WorkflowCard extends BaseCard {
    
    /**
     * Workflow version.
     */
    private String version = "";
    
    /**
     * Creates an empty workflow card.
     */
    public WorkflowCard() {
        super();
    }
    
    /**
     * Creates a workflow card with the specified name and description.
     *
     * @param name the workflow name
     * @param description the workflow description
     */
    public WorkflowCard(String name, String description) {
        super(name, description);
    }
    
    /**
     * Creates a workflow card with full details.
     *
     * @param id the workflow ID (if null, auto-generated)
     * @param name the workflow name
     * @param description the workflow description
     */
    public WorkflowCard(String id, String name, String description) {
        super(id, name, description, null);
    }
    
    /**
     * Creates a workflow card with full details including input params.
     *
     * @param id the workflow ID (if null, auto-generated)
     * @param name the workflow name
     * @param description the workflow description
     * @param inputParams the input parameters schema
     */
    public WorkflowCard(String id, String name, String description, Object inputParams) {
        super(id, name, description, inputParams);
    }
    
    /**
     * Gets the workflow version.
     *
     * @return the version string
     */
    public String getVersion() {
        return version;
    }
    
    /**
     * Sets the workflow version.
     *
     * @param version the version string
     */
    public void setVersion(String version) {
        this.version = version != null ? version : "";
    }
    
    /**
     * Sets the input parameters.
     *
     * @param inputParams the input parameters schema (Map or other object)
     */
    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }
    
    @Override
    public Object toolInfo() {
        Map<String, Object> params = new HashMap<>();
        if (inputParams instanceof Map) {
            params.putAll((Map<String, Object>) inputParams);
        }
        return new ToolInfo(
            getName(),
            getDescription() != null ? getDescription() : "",
            params
        );
    }
}

