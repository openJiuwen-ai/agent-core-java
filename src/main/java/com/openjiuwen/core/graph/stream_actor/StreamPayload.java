/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.workflow.component.ComponentAbility;

/**
 * Payload for stream messages between graph nodes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamPayload}.
 * 
 * @since 0.1.7
 */
public class StreamPayload {
    private final Object message;
    private final ComponentAbility sourceAbility;

    /**
     * StreamPayload.
     * 
     * @param message message
     * @param sourceAbility sourceAbility
     * @since 0.1.7
     */
    public StreamPayload(Object message, ComponentAbility sourceAbility) {
        this.message = message;
        this.sourceAbility = sourceAbility;
    }

    /**
     * getMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getMessage() {
        return message;
    }

    /**
     * getSourceAbility.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ComponentAbility getSourceAbility() {
        return sourceAbility;
    }
}
