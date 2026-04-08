/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Barrier router that sends barrier messages for N→1 fan-in synchronization.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.router.BarrierRouter}.
 */
public class BarrierRouter implements IRouter {

    private final List<String> targets;

    public BarrierRouter(List<String> targets) {
        this.targets = targets != null ? new ArrayList<>(targets) : new ArrayList<>();
    }

    @Override
    public List<Message> dispatch(String sourceNode) {
        List<Message> messages = new ArrayList<>(targets.size());
        for (String target : targets) {
            messages.add(new BarrierMessage(sourceNode, target));
        }
        return messages;
    }
}
