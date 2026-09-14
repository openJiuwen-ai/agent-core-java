/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Mirrors Python's {@code NamedGraphObject} in
 * {@code openjiuwen/core/foundation/store/graph/graph_object.py}.
 */
public class NamedGraphObject extends BaseGraphObject {

    private String name = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }
}
