/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context.ModelContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @JsonIgnore
    private final Map<String, String> requestHeaders = new LinkedHashMap<>();

    @JsonProperty("model_context")
    private ModelContext modelContext;

    private Object response;

    /**
     * Name of the model actually used for this call, derived from the resolved
     * {@code Model}; may be {@code null} when the caller does not fill it in.
     *
     * @since 0.1.16
     */
    private String modelName;

    /**
     * Framework model-pool key ({@code ctx.dynamicModelId}) of the actual model,
     * kept verbatim; may be {@code null} when the default model is used.
     *
     * @since 0.1.16
     */
    private String modelId;

    public static Builder builder() {
        return new Builder();
    }

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

    /**
     * Return the name of the model actually used for this call.
     *
     * @return the model name, or {@code null} when not filled in
     * @since 0.1.16
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Set the name of the model actually used for this call.
     *
     * @param modelName the model name to report
     * @since 0.1.16
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    /**
     * Return the framework model-pool key of the actual model.
     *
     * @return the pool key, or {@code null} when the default model is used
     * @since 0.1.16
     */
    public String getModelId() {
        return modelId;
    }

    /**
     * Set the framework model-pool key of the actual model.
     *
     * @param modelId the pool key, kept verbatim
     * @since 0.1.16
     */
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    @JsonIgnore
    public Map<String, String> getRequestHeaders() {
        return new LinkedHashMap<>(requestHeaders);
    }

    public void setRequestHeaders(Map<String, String> headers) {
        requestHeaders.clear();
        mergeRequestHeaders(headers);
    }

    public void mergeRequestHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, value) -> {
            requestHeaders.keySet().removeIf(existingName -> sameHeaderName(existingName, name));
            requestHeaders.put(name, value);
        });
    }

    public Map<String, String> consumeRequestHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(requestHeaders);
        requestHeaders.clear();
        return headers;
    }

    private static boolean sameHeaderName(String first, String second) {
        if (first == null) {
            return second == null;
        }
        return second != null && first.equalsIgnoreCase(second);
    }

    /**
     * Fluent builder for {@link ModelCallInputs}.
     */
    public static final class Builder {
        private List<Object> messages;

        private String modelName;

        private String modelId;

        /**
         * Sets the model-call messages.
         *
         * @param messages messages to send
         * @return this builder
         */
        public Builder messages(List<Object> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets the name of the model actually used for this call.
         *
         * @param modelName the model name to report
         * @return this builder
         * @since 0.1.16
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the framework model-pool key of the actual model.
         *
         * @param modelId the pool key, kept verbatim
         * @return this builder
         * @since 0.1.16
         */
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        /**
         * Builds model-call inputs from the collected fields.
         *
         * @return a new inputs object
         */
        public ModelCallInputs build() {
            ModelCallInputs inputs = new ModelCallInputs();
            if (messages != null) {
                inputs.setMessages(messages);
            }
            if (modelName != null) {
                inputs.setModelName(modelName);
            }
            if (modelId != null) {
                inputs.setModelId(modelId);
            }
            return inputs;
        }
    }
}
