/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Mirrors Python's {@code Channel} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public abstract class Channel {

    private final String name;

    protected Channel(String name) {
        this.name = name;
    }

    public String getKey() {
        return name;
    }

    public String getNodeName() {
        return name;
    }

    public abstract boolean isReady();

    public abstract void accept(Message msg);

    public abstract Object consume();

    public Object snapshot() {
        return null;
    }

    public abstract void restore(Object snapshot);
}
