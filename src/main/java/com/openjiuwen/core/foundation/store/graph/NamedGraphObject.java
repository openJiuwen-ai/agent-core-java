/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

/**
 * Base class for graph objects with names.
 * <p>
 * Mirrors Python's {@code NamedGraphObject} model from
 * <code>foundation/store/graph/graph_object.py</code>.
 */
public class NamedGraphObject extends BaseGraphObject {

    private String name;

    public NamedGraphObject() {
        super();
        this.name = "";
    }

    public NamedGraphObject(String name) {
        super();
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
