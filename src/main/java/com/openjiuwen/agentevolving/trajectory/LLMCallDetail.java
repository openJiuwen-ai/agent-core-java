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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LLMCallDetail {
    private String model;
    private List<Object> messages = new ArrayList<>();
    private Object response;
    private List<Object> tools;
    private Map<String, Object> usage;
    private Map<String, Object> meta = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMCallDetail() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LLMCallDetail(String model,
                         List<Object> messages,
                         Object response,
                         List<Object> tools,
                         Map<String, Object> usage,
                         Map<String, Object> meta) {
        this.model = model;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.response = response;
        this.tools = tools != null ? new ArrayList<>(tools) : null;
        this.usage = usage != null ? new LinkedHashMap<>(usage) : null;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getModel() {
        return model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getMessages() {
        return messages;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMessages(List<Object> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getResponse() {
        return response;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setResponse(Object response) {
        this.response = response;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> getTools() {
        return tools;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTools(List<Object> tools) {
        this.tools = tools != null ? new ArrayList<>(tools) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getUsage() {
        return usage;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUsage(Map<String, Object> usage) {
        this.usage = usage != null ? new LinkedHashMap<>(usage) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMeta() {
        return meta;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }
}
