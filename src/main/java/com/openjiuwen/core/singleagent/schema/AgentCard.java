// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.common.schema.Param;
import com.openjiuwen.core.common.schema.BaseCard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent card data class.
 * 
 * <p>Defines the metadata and configuration for an agent, including
 * input and output parameter specifications.
 */
public class AgentCard extends BaseCard {
    
    private List<Param> outputParams;
    
    /**
     * Creates an empty agent card.
     */
    public AgentCard() {
        super();
        this.outputParams = new ArrayList<>();
    }
    
    /**
     * Creates an agent card with the specified name and description.
     *
     * @param name the agent name
     * @param description the agent description
     */
    public AgentCard(String name, String description) {
        super(name, description);
        this.outputParams = new ArrayList<>();
    }
    
    /**
     * Creates an agent card with full details.
     *
     * @param id the agent ID (if null, auto-generated)
     * @param name the agent name
     * @param description the agent description
     * @param inputParams the input parameters
     */
    public AgentCard(String id, String name, String description, List<Param> inputParams) {
        super(id, name, description, inputParams);
        this.outputParams = new ArrayList<>();
    }
    
    /**
     * Gets the input parameters.
     *
     * @return the list of input parameters
     */
    @SuppressWarnings("unchecked")
    public List<Param> getAgentInputParams() {
        Object params = getInputParams();
        if (params instanceof List) {
            return (List<Param>) params;
        }
        return new ArrayList<>();
    }
    
    /**
     * Sets the input parameters.
     *
     * @param inputParams the input parameters
     */
    public void setAgentInputParams(List<Param> inputParams) {
        this.inputParams = inputParams != null ? inputParams : new ArrayList<>();
    }
    
    /**
     * Gets the output parameters.
     *
     * @return the list of output parameters
     */
    public List<Param> getOutputParams() {
        return outputParams;
    }
    
    /**
     * Sets the output parameters.
     *
     * @param outputParams the output parameters
     */
    public void setOutputParams(List<Param> outputParams) {
        this.outputParams = outputParams != null ? outputParams : new ArrayList<>();
    }
    
    /**
     * Adds an input parameter.
     *
     * @param param the parameter to add
     * @return this card for chaining
     */
    @SuppressWarnings("unchecked")
    public AgentCard addInputParam(Param param) {
        if (param != null) {
            if (this.inputParams == null) {
                this.inputParams = new ArrayList<Param>();
            }
            ((List<Param>) this.inputParams).add(param);
        }
        return this;
    }
    
    /**
     * Adds an output parameter.
     *
     * @param param the parameter to add
     * @return this card for chaining
     */
    public AgentCard addOutputParam(Param param) {
        if (param != null) {
            this.outputParams.add(param);
        }
        return this;
    }
    
    @Override
    public Object toolInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("id", getId());
        info.put("name", getName());
        info.put("description", getDescription());
        info.put("type", "agent");
        info.put("inputParams", getAgentInputParams());
        info.put("outputParams", getOutputParams());
        return info;
    }
}
