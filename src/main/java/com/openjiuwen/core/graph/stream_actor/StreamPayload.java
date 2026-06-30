/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.workflow.component.ComponentAbility;

/**
 * Payload for stream messages between graph nodes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamPayload}.
 */
public class StreamPayload {

    private final Object message;
    private final ComponentAbility sourceAbility;

    /**
     * Auto-generated for codecheck compliance.
     */
    public StreamPayload(Object message, ComponentAbility sourceAbility) {
        this.message = message;
        this.sourceAbility = sourceAbility;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getMessage() {
        return message;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ComponentAbility getSourceAbility() {
        return sourceAbility;
    }
}
