/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

/**
 * Defines the execution abilities of a workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.base.ComponentAbility}.
 */
public enum ComponentAbility {
    /** Batch invoke: takes full input, returns full output. */
    INVOKE("invoke", "batch in, batch out"),
    /** Streaming output: takes full input, yields chunks. */
    STREAM("stream", "batch in, stream out"),
    /** Collect: consumes a stream of chunks, returns full output. */
    COLLECT("collect", "stream in, batch out"),
    /** Transform: consumes a stream of chunks, yields transformed chunks. */
    TRANSFORM("transform", "stream in, stream out");

    private final String name;
    private final String desc;

    ComponentAbility(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getAbilityName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }
}
