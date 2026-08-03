/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pluggable storage layer specification resolved through the blueprint registry.
 *
 * <p>Mirrors Python's {@code StorageSpec} in
 * {@code openjiuwen/agent_teams/schema/blueprint.py}.</p>
 */
public class StorageSpec {

    private String type;
    private Map<String, Object> params = new LinkedHashMap<>();

    public StorageSpec() {
    }

    public StorageSpec(String type) {
        this(type, Map.of());
    }

    public StorageSpec(String type, Map<String, Object> params) {
        this.type = type;
        setParams(params);
    }

    public Object build() {
        return TeamBlueprintPackage.buildStorage(type, params);
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
