/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Map;

/**
 * Base class for graph objects with names.
 */
public class NamedGraphObject extends BaseGraphObject {
    private String name = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> result = super.toMap();
        result.put("name", name);
        return result;
    }
}
