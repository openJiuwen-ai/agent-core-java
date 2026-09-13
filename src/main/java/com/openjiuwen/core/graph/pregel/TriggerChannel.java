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
 * 
 * @since 0.1.7
 */
public class TriggerChannel extends Channel {
    private final List<TriggerMessage> messages = new ArrayList<>();

    /**
     * TriggerChannel.
     * 
     * @param name name
     * @since 0.1.7
     */
    public TriggerChannel(String name) {
        super(name);
    }

    /**
     * isReady.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isReady() {
        return !messages.isEmpty();
    }

    /**
     * accept.
     * 
     * @param msg msg
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean accept(Message msg) {
        if (msg instanceof TriggerMessage triggerMsg) {
            messages.add(triggerMsg);
            return true;
        }
        return false;
    }

    /**
     * consume.
     * 
     * @since 0.1.7
     */
    @Override
    public void consume() {
        messages.clear();
    }

    /**
     * snapshot.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object snapshot() {
        return new ArrayList<>(messages);
    }

    /**
     * restore.
     * 
     * @param snapshotData snapshotData
     * @since 0.1.7
     */
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
