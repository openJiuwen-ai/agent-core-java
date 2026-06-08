/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code ConditionalRouter} in
 * {@code openjiuwen/core/graph/pregel/router.py}.
 */
public class ConditionalRouter implements IRouter {

    private final Supplier<?> selector;
    private final Function<Object, ?> stateSelector;

    public ConditionalRouter(Supplier<?> selector) {
        this.selector = selector;
        this.stateSelector = null;
    }

    public ConditionalRouter(Function<Object, ?> stateSelector) {
        this.selector = null;
        this.stateSelector = stateSelector;
    }

    @Override
    public List<Message> dispatch(String sourceNode) {
        Object rawTargets;
        if (stateSelector != null) {
            rawTargets = stateSelector.apply(null);
        } else if (selector != null) {
            rawTargets = selector.get();
        } else {
            throw new IllegalStateException("ConditionalRouter requires a selector");
        }
        List<String> targets = normalizeTargets(rawTargets);
        List<Message> messages = new ArrayList<>(targets.size());
        for (String target : targets) {
            messages.add(new TriggerMessage(sourceNode, target));
        }
        return messages;
    }

    private List<String> normalizeTargets(Object rawTargets) {
        if (rawTargets instanceof String target) {
            return List.of(target);
        }
        if (rawTargets instanceof Collection<?> collection) {
            List<String> targets = new ArrayList<>(collection.size());
            for (Object item : collection) {
                targets.add(String.valueOf(item));
            }
            return targets;
        }
        if (rawTargets != null && rawTargets.getClass().isArray()) {
            int length = Array.getLength(rawTargets);
            List<String> targets = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                targets.add(String.valueOf(Array.get(rawTargets, index)));
            }
            return targets;
        }
        throw new IllegalArgumentException(
                "ConditionalRouter selector must return a target string or a target collection");
    }
}
