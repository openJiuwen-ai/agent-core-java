/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Channel for N→1 fan-in barrier synchronization.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.channels.BarrierChannel}.
 * The channel becomes ready only when all expected senders have sent a message.
 * 
 * @since 0.1.7
 */
public class BarrierChannel extends Channel {
    private final String nodeName;
    private final Set<String> expected;

    /**
     * HashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> received = new HashSet<>();
    private final String routerKey;

    /**
     * BarrierChannel.
     * 
     * @param nodeName nodeName
     * @param expected expected
     * @since 0.1.7
     */
    public BarrierChannel(String nodeName, Set<String> expected) {
        super(nodeName);
        this.nodeName = nodeName;
        this.expected = new HashSet<>(expected);
        this.routerKey = makeRouterKey(nodeName, expected);
    }

    /**
     * getKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getKey() {
        return routerKey;
    }

    /**
     * getNodeName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getNodeName() {
        return nodeName;
    }

    /**
     * isReady.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean isReady() {
        return received.equals(expected);
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
        if (msg instanceof BarrierMessage barrierMsg) {
            if (!received.contains(barrierMsg.getSender())) {
                received.add(barrierMsg.getSender());
                return true;
            }
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
        received.clear();
    }

    /**
     * snapshot.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object snapshot() {
        return new ArrayList<>(received);
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
            received.clear();
            for (Object item : list) {
                if (item instanceof String s) {
                    received.add(s);
                }
            }
        }
    }

    /**
     * makeRouterKey.
     * 
     * @param name name
     * @param expected expected
     * @return the result
     * @since 0.1.7
     */
    private static String makeRouterKey(String name, Set<String> expected) {
        String senders = expected.stream().sorted().collect(Collectors.joining("|"));
        return "barrier:" + senders + "->" + name;
    }
}
