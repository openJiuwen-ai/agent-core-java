/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic callback context passed between DeepAgent and rails.
 *
 * <p>Mirrors Python's callback context dict usage in
 * {@code openjiuwen/harness/rails/base.py}.</p>
 */
public class CallbackContext {

    private final DeepAgent agent;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private boolean rejected;
    private String rejectionMessage;
    private String dynamicModelId;

    public CallbackContext(DeepAgent agent, Map<String, Object> values) {
        this.agent = agent;
        if (values != null) {
            this.values.putAll(values);
            Object fromValues = this.values.get("model_id");
            if (fromValues == null) {
                fromValues = this.values.get("dynamic_model_id");
            }
            if (fromValues == null) {
                fromValues = this.values.get("target_model_id");
            }
            if (fromValues instanceof String text && !text.isBlank()) {
                this.dynamicModelId = text;
            }
        }
    }

    public DeepAgent getAgent() {
        return agent;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public void put(String key, Object value) {
        values.put(key, value);
        if ("model_id".equals(key) || "dynamic_model_id".equals(key) || "target_model_id".equals(key)) {
            if (value instanceof String text && !text.isBlank()) {
                this.dynamicModelId = text;
            } else if (value == null) {
                this.dynamicModelId = null;
            }
        }
    }

    /**
     * Returns the request-scoped dynamic model id.
     *
     * @return model id, or null when unset
     * @since 0.1.15
     */
    public String getDynamicModelId() {
        return dynamicModelId;
    }

    /**
     * Sets the request-scoped dynamic model id for this invoke.
     *
     * @param dynamicModelId model id to resolve; may be null
     * @since 0.1.15
     */
    public void setDynamicModelId(String dynamicModelId) {
        this.dynamicModelId = dynamicModelId;
        if (dynamicModelId == null || dynamicModelId.isBlank()) {
            values.remove("target_model_id");
            values.remove("dynamic_model_id");
            return;
        }
        values.put("target_model_id", dynamicModelId);
        values.put("dynamic_model_id", dynamicModelId);
    }

    public boolean isRejected() {
        return rejected;
    }

    public String getRejectionMessage() {
        return rejectionMessage;
    }

    public void reject(String message) {
        this.rejected = true;
        this.rejectionMessage = message;
        values.put("rejected", true);
        values.put("error", message);
    }
}
