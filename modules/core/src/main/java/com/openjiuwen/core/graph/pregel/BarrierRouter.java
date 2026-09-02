/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Barrier router that sends barrier messages for N→1 fan-in synchronization.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.router.BarrierRouter}.
 * 
 * @since 0.1.7
 */
public class BarrierRouter implements IRouter {
    private final List<String> targets;

    /**
     * BarrierRouter.
     * 
     * @param targets targets
     * @since 0.1.7
     */
    public BarrierRouter(List<String> targets) {
        this.targets = targets != null ? new ArrayList<>(targets) : new ArrayList<>();
    }

    /**
     * dispatch.
     * 
     * @param sourceNode sourceNode
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Message> dispatch(String sourceNode) {
        List<Message> messages = new ArrayList<>(targets.size());
        for (String target : targets) {
            messages.add(new BarrierMessage(sourceNode, target));
        }
        return messages;
    }
}
