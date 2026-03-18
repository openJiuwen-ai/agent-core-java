/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
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
 */
public class BarrierChannel extends Channel {

    private final String nodeName;
    private final Set<String> expected;
    private final Set<String> received = new HashSet<>();
    private final String routerKey;

    public BarrierChannel(String nodeName, Set<String> expected) {
        super(nodeName);
        this.nodeName = nodeName;
        this.expected = new HashSet<>(expected);
        this.routerKey = makeRouterKey(nodeName, expected);
    }

    @Override
    public String getKey() {
        return routerKey;
    }

    @Override
    public String getNodeName() {
        return nodeName;
    }

    @Override
    public boolean isReady() {
        return received.equals(expected);
    }

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

    @Override
    public void consume() {
        received.clear();
    }

    @Override
    public Object snapshot() {
        return new ArrayList<>(received);
    }

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

    private static String makeRouterKey(String name, Set<String> expected) {
        String senders = expected.stream().sorted().collect(Collectors.joining("|"));
        return "barrier:" + senders + "->" + name;
    }
}
