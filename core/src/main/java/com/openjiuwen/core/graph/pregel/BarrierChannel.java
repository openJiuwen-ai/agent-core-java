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
 * Mirrors Python's {@code BarrierChannel} in
 * {@code openjiuwen/core/graph/pregel/channels.py}.
 */
public class BarrierChannel extends Channel {

    private final List<Set<String>> expectedGroups;
    private final Set<String> received = new HashSet<>();
    private final String routerKey;

    public BarrierChannel(String nodeName, List<Set<String>> expectedGroups) {
        super(nodeName);
        this.expectedGroups = normalizeGroups(expectedGroups);
        this.routerKey = makeRouterKey(nodeName, this.expectedGroups);
    }

    public BarrierChannel(String nodeName, Set<String> expected) {
        this(nodeName, expected.stream().sorted().map(Set::of).toList());
    }

    @Override
    public String getKey() {
        return routerKey;
    }

    @Override
    public boolean isReady() {
        if (received.isEmpty()) {
            return false;
        }
        for (Set<String> group : expectedGroups) {
            if (group.stream().noneMatch(received::contains)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void accept(Message msg) {
        if (msg instanceof BarrierMessage barrierMessage && !received.contains(barrierMessage.getSender())) {
            received.add(barrierMessage.getSender());
        }
    }

    @Override
    public Object consume() {
        received.clear();
        return null;
    }

    @Override
    public Object snapshot() {
        return new ArrayList<>(received);
    }

    @Override
    public void restore(Object snapshot) {
        if (snapshot instanceof List<?> list) {
            received.clear();
            for (Object item : list) {
                if (item instanceof String sender) {
                    received.add(sender);
                }
            }
        }
    }

    private static List<Set<String>> normalizeGroups(List<Set<String>> groups) {
        List<Set<String>> normalized = new ArrayList<>();
        for (Set<String> group : groups) {
            normalized.add(new HashSet<>(group));
        }
        return normalized;
    }

    private static String makeRouterKey(String nodeName, List<Set<String>> groups) {
        List<String> parts = new ArrayList<>();
        for (Set<String> group : groups) {
            String joined = group.stream().sorted().collect(Collectors.joining("|"));
            parts.add(group.size() == 1 ? joined : "(" + joined + ")");
        }
        return "barrier:" + String.join("&", parts) + "->" + nodeName;
    }
}
