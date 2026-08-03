/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.workflow.component.ComponentAbility;

/**
 * Mirrors Python's {@code StreamPayload} in
 * {@code openjiuwen/core/graph/stream_actor/base.py}.
 */
public class StreamPayload {

    private final Object message;
    private final ComponentAbility sourceAbility;

    public StreamPayload(Object message, ComponentAbility sourceAbility) {
        this.message = message;
        this.sourceAbility = sourceAbility;
    }

    public Object getMessage() {
        return message;
    }

    public ComponentAbility getSourceAbility() {
        return sourceAbility;
    }
}
