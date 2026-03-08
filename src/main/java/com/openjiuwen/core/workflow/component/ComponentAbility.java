/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component;

/**
 * Defines the execution abilities of a workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.base.ComponentAbility}.
 */
public enum ComponentAbility {
    /** Batch invoke: takes full input, returns full output. */
    INVOKE,
    /** Streaming output: takes full input, yields chunks. */
    STREAM,
    /** Collect: consumes a stream of chunks, returns full output. */
    COLLECT,
    /** Transform: consumes a stream of chunks, yields transformed chunks. */
    TRANSFORM
}
