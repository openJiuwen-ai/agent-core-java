/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context_engine.ModelContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Input data for before/after model call events.
 *
 * <p>Mirrors Python's {@code ModelCallInputs} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelCallInputs implements EventInputs {
    private List<Object> messages = new ArrayList<>();
    private List<Object> tools;

    @JsonProperty("model_context")
    private ModelContext modelContext;

    private Object response;

    public List<Object> getMessages() {
        return messages;
    }

    public void setMessages(List<Object> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public List<Object> getTools() {
        return tools;
    }

    public void setTools(List<Object> tools) {
        this.tools = tools == null ? null : new ArrayList<>(tools);
    }

    public ModelContext getModelContext() {
        return modelContext;
    }

    public void setModelContext(ModelContext modelContext) {
        this.modelContext = modelContext;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Object response) {
        this.response = response;
    }
}
