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

    public CallbackContext(DeepAgent agent, Map<String, Object> values) {
        this.agent = agent;
        if (values != null) {
            this.values.putAll(values);
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
