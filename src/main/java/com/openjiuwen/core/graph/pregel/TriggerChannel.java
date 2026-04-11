/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Channel that triggers when any message is received.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.channels.TriggerChannel}.
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
    public boolean accept(Message msg) {
        if (msg instanceof TriggerMessage triggerMsg) {
            messages.add(triggerMsg);
            return true;
        }
        return false;
    }

    @Override
    public void consume() {
        messages.clear();
    }

    @Override
    public Object snapshot() {
        return new ArrayList<>(messages);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(Object snapshotData) {
        if (snapshotData instanceof List<?> list) {
            messages.clear();
            for (Object item : list) {
                if (item instanceof TriggerMessage msg) {
                    messages.add(msg);
                }
            }
        }
    }
}
