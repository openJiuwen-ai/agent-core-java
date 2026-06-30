/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

/**
 * Abstract channel for message passing between Pregel nodes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.Channel}.
 * Channels buffer messages and determine when a node is ready to execute.
 */
public abstract class Channel {

    private final String name;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected Channel(String name) {
        this.name = name;
    }

    /**
     * Get the routing key for this channel.
     * By default, uses the channel name.
     */
    public String getKey() {
        return name;
    }

    /**
     * Get the node name this channel belongs to.
     */
    public String getNodeName() {
        return name;
    }

    /**
     * Check whether the channel has received enough messages to trigger execution.
     */
    public abstract boolean isReady();

    /**
     * Accept an incoming message.
     *
     * @param msg the message to accept
     * @return true if the channel state changed
     */
    public abstract boolean accept(Message msg);

    /**
     * Consume the buffered messages and reset the channel.
     */
    public abstract void consume();

    /**
     * Create a snapshot of the current channel state for persistence.
     *
     * @return serializable snapshot, or null
     */
    public abstract Object snapshot();

    /**
     * Restore channel state from a snapshot.
     *
     * @param snapshotData the snapshot data
     */
    public abstract void restore(Object snapshotData);
}
