/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mirrors Python's {@code ChannelManager} in
 * {@code openjiuwen/core/graph/pregel/channels.py}.
 */
public class ChannelManager {

    private final Map<String, Channel> mapKeyToChannel = new HashMap<>();

    private final Map<String, List<Channel>> mapNodeToChannels = new HashMap<>();

    private final Set<String> readyNodeNames = new HashSet<>();

    private final List<Message> buffer = new ArrayList<>();

    public ChannelManager(List<Channel> channels) {
        for (Channel channel : channels) {
            mapKeyToChannel.put(channel.getKey(), channel);
            mapNodeToChannels.computeIfAbsent(channel.getNodeName(), ignored -> new ArrayList<>()).add(channel);
            if (channel.isReady()) {
                readyNodeNames.add(channel.getNodeName());
            }
        }
    }

    public void bufferMessage(Message message) {
        buffer.add(message);
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public void flush() {
        Set<String> updatedNodes = new HashSet<>();

        for (Message message : buffer) {
            Channel channel = mapKeyToChannel.get(message.getTarget());
            if (channel == null) {
                throw new IllegalArgumentException("Channel not found for target key: '" + message.getTarget() + "'");
            }

            Object before = channel.snapshot();
            channel.accept(message);
            Object after = channel.snapshot();
            boolean changed = !Objects.deepEquals(before, after);
            if (changed) {
                updatedNodes.add(channel.getNodeName());
            }
        }

        buffer.clear();

        for (String nodeName : updatedNodes) {
            List<Channel> channels = mapNodeToChannels.get(nodeName);
            if (channels != null && channels.stream().anyMatch(Channel::isReady)) {
                readyNodeNames.add(nodeName);
            }
        }
    }

    public List<String> getReadyNodes() {
        return new ArrayList<>(readyNodeNames);
    }

    public void consume(String nodeName) {
        List<Channel> channels = mapNodeToChannels.get(nodeName);
        if (channels == null) {
            return;
        }

        for (Channel channel : channels) {
            if (channel.isReady()) {
                channel.consume();
            }
        }

        readyNodeNames.remove(nodeName);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        for (Map.Entry<String, List<Channel>> entry : mapNodeToChannels.entrySet()) {
            String nodeName = entry.getKey();
            if (PregelConstants.END.equals(nodeName)) {
                continue;
            }

            List<Object> channelStates = new ArrayList<>();
            boolean hasState = false;
            for (Channel channel : entry.getValue()) {
                Object state = channel.snapshot();
                channelStates.add(state);
                if (state != null && !isEmptyState(state)) {
                    hasState = true;
                }
            }

            if (hasState) {
                snapshot.put(nodeName, channelStates);
            }
        }
        return snapshot;
    }

    public void restore(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            List<Channel> channels = mapNodeToChannels.get(entry.getKey());
            if (channels == null || !(entry.getValue() instanceof List<?> channelStates)) {
                continue;
            }
            if (channels.size() != channelStates.size()) {
                continue;
            }

            for (int i = 0; i < channels.size(); i++) {
                Object state = channelStates.get(i);
                if (state != null && !isEmptyState(state)) {
                    Channel channel = channels.get(i);
                    channel.restore(state);
                    if (channel.isReady()) {
                        readyNodeNames.add(entry.getKey());
                    }
                }
            }
        }
    }

    public List<Message> getBuffer() {
        return buffer;
    }

    private static boolean isEmptyState(Object state) {
        if (state instanceof List<?> list) {
            return list.isEmpty();
        }
        if (state instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
