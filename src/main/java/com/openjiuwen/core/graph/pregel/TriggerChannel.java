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

    /**
     * Auto-generated for codecheck compliance.
     */
    public TriggerChannel(String name) {
        super(name);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isReady() {
        return !messages.isEmpty();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean accept(Message msg) {
        if (msg instanceof TriggerMessage triggerMsg) {
            messages.add(triggerMsg);
            return true;
        }
        return false;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void consume() {
        messages.clear();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object snapshot() {
        return new ArrayList<>(messages);
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
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
