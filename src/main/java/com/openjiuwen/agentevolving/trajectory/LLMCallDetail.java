/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.LLMCallDetail.
 * 
 * @since 0.1.7
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMCallDetail {
    private String model;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Object> messages = new ArrayList<>();
    private Object response;
    private List<Object> tools;
    private Map<String, Object> usage;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> meta = new LinkedHashMap<>();

    /**
     * LLMCallDetail.
     * 
     * @since 0.1.7
     */
    public LLMCallDetail() {
    }

    /**
     * LLMCallDetail.
     * 
     * @param model model
     * @param messages messages
     * @param response response
     * @param tools tools
     * @param usage usage
     * @param meta meta
     * @since 0.1.7
     */
    public LLMCallDetail(String model, List<Object> messages, Object response, List<Object> tools,
            Map<String, Object> usage, Map<String, Object> meta) {
        this.model = model;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.response = response;
        this.tools = tools != null ? new ArrayList<>(tools) : null;
        this.usage = usage != null ? new LinkedHashMap<>(usage) : null;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * getModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getModel() {
        return model;
    }

    /**
     * setModel.
     * 
     * @param model model
     * @since 0.1.7
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * getMessages.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getMessages() {
        return messages;
    }

    /**
     * setMessages.
     * 
     * @param messages messages
     * @since 0.1.7
     */
    public void setMessages(List<Object> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * getResponse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getResponse() {
        return response;
    }

    /**
     * setResponse.
     * 
     * @param response response
     * @since 0.1.7
     */
    public void setResponse(Object response) {
        this.response = response;
    }

    /**
     * getTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getTools() {
        return tools;
    }

    /**
     * setTools.
     * 
     * @param tools tools
     * @since 0.1.7
     */
    public void setTools(List<Object> tools) {
        this.tools = tools != null ? new ArrayList<>(tools) : null;
    }

    /**
     * getUsage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getUsage() {
        return usage;
    }

    /**
     * setUsage.
     * 
     * @param usage usage
     * @since 0.1.7
     */
    public void setUsage(Map<String, Object> usage) {
        this.usage = usage != null ? new LinkedHashMap<>(usage) : null;
    }

    /**
     * getMeta.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * setMeta.
     * 
     * @param meta meta
     * @since 0.1.7
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }
}
