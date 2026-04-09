/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.graph.pregel;

/**
 * Barrier message for N→1 fan-in synchronization.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.BarrierMessage}.
 */
public class BarrierMessage extends Message {

    public BarrierMessage(String sender, String target) {
        super(sender, target);
    }

    public BarrierMessage(String sender, String target, Object payload) {
        super(sender, target, payload);
    }
}
