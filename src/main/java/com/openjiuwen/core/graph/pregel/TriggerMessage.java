/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.pregel;

/**
 * Trigger message that activates a target node in the next super-step.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.TriggerMessage}.
 */
public class TriggerMessage extends Message {

    public TriggerMessage(String sender, String target) {
        super(sender, target);
    }

    public TriggerMessage(String sender, String target, Object payload) {
        super(sender, target, payload);
    }
}
