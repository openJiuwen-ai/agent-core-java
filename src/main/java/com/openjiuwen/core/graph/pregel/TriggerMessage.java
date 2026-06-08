/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Mirrors Python's {@code TriggerMessage} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public class TriggerMessage extends Message {

    public TriggerMessage(String sender, String target) {
        super(sender, target);
    }

    public TriggerMessage(String sender, String target, Object payload) {
        super(sender, target, payload);
    }
}
