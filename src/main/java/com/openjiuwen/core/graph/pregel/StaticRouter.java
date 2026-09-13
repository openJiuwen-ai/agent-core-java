/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Static router that sends trigger messages to fixed targets (1→N).
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.router.StaticRouter}.
 * 
 * @since 0.1.7
 */
public class StaticRouter implements IRouter {
    private final List<String> targets;

    /**
     * StaticRouter.
     * 
     * @param targets targets
     * @since 0.1.7
     */
    public StaticRouter(List<String> targets) {
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
            messages.add(new TriggerMessage(sourceNode, target));
        }
        return messages;
    }
}
