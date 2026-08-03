/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code TriggerChannel} in
 * {@code openjiuwen/core/graph/pregel/channels.py}.
 */
public class TriggerChannel extends Channel {

    private final List<TriggerMessage> messages = new ArrayList<>();

    public TriggerChannel(String name) {
        super(name);
    }

    @Override
    public boolean isReady() {
        return !messages.isEmpty();
    }

    @Override
    public void accept(Message msg) {
        if (msg instanceof TriggerMessage triggerMessage) {
            messages.add(triggerMessage);
        }
    }

    @Override
    public Object consume() {
        messages.clear();
        return null;
    }

    @Override
    public Object snapshot() {
        return new ArrayList<>(messages);
    }

    @Override
    public void restore(Object snapshot) {
        if (snapshot instanceof List<?> list) {
            messages.clear();
            for (Object item : list) {
                if (item instanceof TriggerMessage message) {
                    messages.add(message);
                }
            }
        }
    }
}
