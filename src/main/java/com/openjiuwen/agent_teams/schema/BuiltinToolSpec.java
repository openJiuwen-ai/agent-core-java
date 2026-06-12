/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative builtin tool reference resolved through the tool type registry.
 *
 * <p>Mirrors Python's {@code BuiltinToolSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class BuiltinToolSpec {

    private String type;
    private Map<String, Object> params = new LinkedHashMap<>();

    public BuiltinToolSpec() {
    }

    public BuiltinToolSpec(String type, Map<String, Object> params) {
        this.type = type;
        setParams(params);
    }

    public Object build(String language, String toolId) {
        return DeepAgentSpecPackage.buildTool(type, params, language, toolId);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParams() {
        return new LinkedHashMap<>(params);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }
}
