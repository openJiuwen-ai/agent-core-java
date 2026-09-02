/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages all channels and message routing between Pregel nodes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.channels.ChannelManager}.
 * 
 * @since 0.1.7
 */
public class ChannelManager {
    private final Map<String, Channel> mapKeyToChannel = new HashMap<>();

    /**
     * Maps node name → list of Channels.
     * 
     * @since 0.1.7
     */
    private final Map<String, List<Channel>> mapNodeToChannels = new HashMap<>();

    /**
     * Set of node names that are ready to execute.
     * 
     * @since 0.1.7
     */
    private final Set<String> readyNodeNames = new HashSet<>();

    /**
     * Message buffer for next flush.
     * 
     * @since 0.1.7
     */
    private final List<Message> buffer = new ArrayList<>();

    /**
     * ChannelManager.
     * 
     * @param channels channels
     * @since 0.1.7
     */
    public ChannelManager(List<Channel> channels) {
        for (Channel ch : channels) {
            mapKeyToChannel.put(ch.getKey(), ch);
            mapNodeToChannels.computeIfAbsent(ch.getNodeName(), k -> new ArrayList<>()).add(ch);

            // Recover: if already ready, mark the node
            if (ch.isReady()) {
                readyNodeNames.add(ch.getNodeName());
            }
        }
    }

    /**
     * Add a message to the buffer for the next flush.
     * 
     * @param msg msg
     * @since 0.1.7
     */
    public void bufferMessage(Message msg) {
        buffer.add(msg);
    }

    /**
     * Check if the message buffer is empty.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    /**
     * Flush buffered messages into channels and update ready nodes.
     * 
     * @since 0.1.7
     */
    public void flush() {
        Set<String> updatedNodes = new HashSet<>();

        for (Message msg : buffer) {
            Channel ch = mapKeyToChannel.get(msg.getTarget());
            if (ch == null) {
                throw new IllegalStateException("Channel not found for target key: '" + msg.getTarget() + "'");
            }

            boolean changed = ch.accept(msg);
            if (changed) {
                updatedNodes.add(ch.getNodeName());
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

    /**
     * Get names of all nodes that are ready to execute.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getReadyNodes() {
        return new ArrayList<>(readyNodeNames);
    }

    /**
     * Consume (clear) all ready channels for the given node.
     * 
     * @param nodeName nodeName
     * @since 0.1.7
     */
    public void consume(String nodeName) {
        List<Channel> channels = mapNodeToChannels.get(nodeName);
        if (channels == null) {
            return;
        }
        for (Channel ch : channels) {
            if (ch.isReady()) {
                ch.consume();
            }
        }
        readyNodeNames.remove(nodeName);
    }

    /**
     * Create a snapshot of all channel states for persistence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snap = new HashMap<>();
        for (Map.Entry<String, List<Channel>> entry : mapNodeToChannels.entrySet()) {
            String name = entry.getKey();
            if (PregelConstants.END.equals(name)) {
                continue;
            }
            List<Object> nodeChannelsSnap = new ArrayList<>();
            boolean hasState = false;
            for (Channel c : entry.getValue()) {
                Object s = c.snapshot();
                nodeChannelsSnap.add(s);
                if (s != null && !isEmptyCollection(s)) {
                    hasState = true;
                }
            }
            if (hasState) {
                snap.put(name, nodeChannelsSnap);
            }
        }
        return snap;
    }

    /**
     * restore.
     * 
     * @param snapshotMap snapshotMap
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public void restore(Map<String, Object> snapshotMap) {
        if (snapshotMap == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : snapshotMap.entrySet()) {
            String nodeName = entry.getKey();
            List<Channel> channels = mapNodeToChannels.get(nodeName);
            if (channels == null) {
                continue;
            }
            if (entry.getValue() instanceof List<?> channelStates) {
                if (channels.size() != channelStates.size()) {
                    continue;
                }
                for (int i = 0; i < channels.size(); i++) {
                    Object state = channelStates.get(i);
                    if (state != null && !isEmptyCollection(state)) {
                        channels.get(i).restore(state);
                        if (channels.get(i).isReady()) {
                            readyNodeNames.add(nodeName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the raw buffer (for error state persistence).
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Message> getBuffer() {
        return buffer;
    }

    /**
     * isEmptyCollection.
     * 
     * @param obj obj
     * @return the result
     * @since 0.1.7
     */
    private static boolean isEmptyCollection(Object obj) {
        if (obj instanceof List<?> list) {
            return list.isEmpty();
        }
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }
}
