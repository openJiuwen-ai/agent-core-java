/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.harness.workspace.Workspace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declarative rail reference resolved through the rail type registry.
 *
 * <p>Mirrors Python's {@code RailSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class RailSpec {

    private String type;
    private Map<String, Object> params = new LinkedHashMap<>();

    public RailSpec() {
    }

    public RailSpec(String type, Map<String, Object> params) {
        this.type = type;
        setParams(params);
    }

    public Object build(String language, Workspace workspace) {
        return DeepAgentSpecPackage.buildRail(type, params, language, workspace);
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
