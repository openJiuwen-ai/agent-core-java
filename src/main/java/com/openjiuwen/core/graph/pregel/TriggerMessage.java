/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Trigger message that activates a target node in the next super-step.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.TriggerMessage}.
 * 
 * @since 0.1.7
 */
public class TriggerMessage extends Message {
    /**
     * TriggerMessage.
     * 
     * @param sender sender
     * @param target target
     * @since 0.1.7
     */
    public TriggerMessage(String sender, String target) {
        super(sender, target);
    }

    /**
     * TriggerMessage.
     * 
     * @param sender sender
     * @param target target
     * @param payload payload
     * @since 0.1.7
     */
    public TriggerMessage(String sender, String target, Object payload) {
        super(sender, target, payload);
    }
}
