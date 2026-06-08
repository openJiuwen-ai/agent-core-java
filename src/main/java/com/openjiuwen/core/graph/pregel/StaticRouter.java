/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code StaticRouter} in
 * {@code openjiuwen/core/graph/pregel/router.py}.
 */
public class StaticRouter implements IRouter {

    private final List<String> targets;

    public StaticRouter(List<String> targets) {
        this.targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public List<String> getTargets() {
        return targets;
    }

    @Override
    public List<Message> dispatch(String sourceNode) {
        List<Message> messages = new ArrayList<>(targets.size());
        for (String target : targets) {
            messages.add(new TriggerMessage(sourceNode, target));
        }
        return messages;
    }
}
