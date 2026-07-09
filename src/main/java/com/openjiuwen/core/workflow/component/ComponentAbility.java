/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

/**
 * Defines the execution abilities of a workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.base.ComponentAbility}.
 * 
 * @since 0.1.7
 */
public enum ComponentAbility {
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

    /**
     * getAbilityName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getAbilityName() {
        return name;
    }

    /**
     * getDesc.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDesc() {
        return desc;
    }
}
