/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code LLMCallDetail} in {@code openjiuwen.agent_evolving.trajectory.types}.
 * Complete LLM call execution data.
 */
public class LLMCallDetail {

    private String model;
    private List<Map<String, Object>> messages;
    private Map<String, Object> response;
    private List<Map<String, Object>> tools;
    private Map<String, Object> usage;
    private Map<String, Object> meta;

    public LLMCallDetail() {
        this.messages = new ArrayList<>();
        this.meta = new LinkedHashMap<>();
    }

    public LLMCallDetail(String model, List<Map<String, Object>> messages,
                         Map<String, Object> response, List<Map<String, Object>> tools,
                         Map<String, Object> usage, Map<String, Object> meta) {
        this.model = model;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.response = response;
        this.tools = tools;
        this.usage = usage;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static Builder builder() { return new Builder(); }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Map<String, Object>> getMessages() { return messages; }
    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    public Map<String, Object> getResponse() { return response; }
    public void setResponse(Map<String, Object> response) { this.response = response; }

    public List<Map<String, Object>> getTools() { return tools; }
    public void setTools(List<Map<String, Object>> tools) { this.tools = tools; }

    public Map<String, Object> getUsage() { return usage; }
    public void setUsage(Map<String, Object> usage) { this.usage = usage; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public static final class Builder {
        private String model;
        private List<Map<String, Object>> messages;
        private Map<String, Object> response;
        private List<Map<String, Object>> tools;
        private Map<String, Object> usage;
        private Map<String, Object> meta;

        private Builder() {
            this.messages = new ArrayList<>();
            this.meta = new LinkedHashMap<>();
        }

        public Builder model(String model) { this.model = model; return this; }
        public Builder messages(List<Map<String, Object>> messages) {
            this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            return this;
        }
        public Builder response(Map<String, Object> response) { this.response = response; return this; }
        public Builder tools(List<Map<String, Object>> tools) { this.tools = tools; return this; }
        public Builder usage(Map<String, Object> usage) { this.usage = usage; return this; }
        public Builder meta(Map<String, Object> meta) {
            this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
            return this;
        }

        public LLMCallDetail build() {
            return new LLMCallDetail(model, messages, response, tools, usage, meta);
        }
    }
}
