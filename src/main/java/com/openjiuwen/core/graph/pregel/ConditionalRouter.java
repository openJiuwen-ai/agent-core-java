/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Conditional router that determines targets dynamically via a selector function.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.router.ConditionalRouter}.
 * The selector returns either a single node name or a list of node names.
 * 
 * @since 0.1.7
 */
public class ConditionalRouter implements IRouter {
    private final Function<Object, Object> selector;

    /**
     * Create a conditional router.
     * 
     * @param selector function that takes optional state and returns
     *            a String (single target) or List&lt;String&gt; (multiple targets)
     * @since 0.1.7
     */
    public ConditionalRouter(Function<Object, Object> selector) {
        this.selector = selector;
    }

    /**
     * dispatch.
     * 
     * @param sourceNode sourceNode
     * @return the result
     * @since 0.1.7
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<Message> dispatch(String sourceNode) {
        Object result = selector.apply(null);
        List<String> targets;
        if (result instanceof String s) {
            targets = List.of(s);
        } else if (result instanceof List<?> list) {
            targets = (List<String>) list;
        } else {
            targets = List.of(String.valueOf(result));
        }

        List<Message> messages = new ArrayList<>(targets.size());
        for (String target : targets) {
            messages.add(new TriggerMessage(sourceNode, target));
        }
        return messages;
    }
}
