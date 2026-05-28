/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.schema;

import java.util.Map;

/**
 * API parameter mapper for mapping request parameters.
 * <p>
 * Mirrors Python's APIParamMapper.
 */
public class APIParamMapper {

    private Map<String, Object> schema;

    public APIParamMapper(Map<String, Object> schema) {
        this.schema = schema;
    }

    public Map<String, Object> getSchema() {
        return schema;
    }

    public APIParamLocation getLocation(String paramName) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties != null && properties.containsKey(paramName)) {
            Map<String, Object> paramDef = (Map<String, Object>) properties.get(paramName);
            String locationStr = (String) paramDef.get("location");
            if (locationStr != null) {
                return APIParamLocation.valueOf(locationStr.toUpperCase());
            }
        }
        return APIParamLocation.QUERY; // Default
    }

    public boolean hasParam(String paramName) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return properties != null && properties.containsKey(paramName);
    }
}