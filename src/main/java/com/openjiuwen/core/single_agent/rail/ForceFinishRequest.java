/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signal to terminate the agent loop and return a result immediately.
 *
 * <p>Mirrors Python's {@code ForceFinishRequest} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
public class ForceFinishRequest {
    private Map<String, Object> result = new LinkedHashMap<>();

    public ForceFinishRequest() {
    }

    public ForceFinishRequest(Map<String, Object> result) {
        setResult(result);
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
    }
}
