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

    /**
     * Create a new builder for LLMCallDetail.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for LLMCallDetail.
     */
    public static final class Builder {
        private String model;
        private List<Object> messages;
        private Object response;
        private List<Object> tools;
        private Map<String, Object> usage;
        private Map<String, Object> meta;

        private Builder() {
            this.messages = new ArrayList<>();
            this.meta = new LinkedHashMap<>();
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder messages(List<Object> messages) {
            this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder response(Object response) {
            this.response = response;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder tools(List<Object> tools) {
            this.tools = tools;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder usage(Map<String, Object> usage) {
            this.usage = usage;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
            return this;
        }

        /**
         * Build the LLMCallDetail instance.
         *
         * @return new LLMCallDetail
         */
        public LLMCallDetail build() {
            return new LLMCallDetail(model, messages, response, tools, usage, meta);
        }
    }
}
