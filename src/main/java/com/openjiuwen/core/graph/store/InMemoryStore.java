/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.openjiuwen.core.graph.pregel.BarrierMessage;
import com.openjiuwen.core.graph.pregel.Message;
import com.openjiuwen.core.graph.pregel.TriggerMessage;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of the graph state store.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.store.inmemory.InMemoryStore}.
 * Stores the latest graph state per session ID and namespace.
 * 
 * @since 0.1.7
 */
public class InMemoryStore implements Store {
    private final Map<String, Map<String, GraphStoreState>> storeCk = new ConcurrentHashMap<>();

    /**
     * get.
     * 
     * @param sessionId sessionId
     * @param ns ns
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Optional<GraphStoreState> get(String sessionId, String ns) {
        Map<String, GraphStoreState> sessionMap = storeCk.get(sessionId);
        if (sessionMap == null) {
            return Optional.empty();
        }
        GraphStoreState state = sessionMap.get(ns);
        return Optional.ofNullable(deepCopy(state));
    }

    /**
     * save.
     * 
     * @param sessionId sessionId
     * @param ns ns
     * @param state state
     * @since 0.1.7
     */
    @Override
    public void save(String sessionId, String ns, GraphStoreState state) {
        storeCk.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(ns, deepCopy(state));
    }

    /**
     * delete.
     * 
     * @param sessionId sessionId
     * @param ns ns
     * @since 0.1.7
     */
    @Override
    public void delete(String sessionId, String ns) {
        Map<String, GraphStoreState> sessionMap = storeCk.get(sessionId);
        if (sessionMap == null) {
            return;
        }

        if (ns == null) {
            storeCk.remove(sessionId);
        } else {
            deleteNsByPrefix(sessionMap, ns);
            if (sessionMap.isEmpty()) {
                storeCk.remove(sessionId);
            }
        }
    }

    /**
     * deleteNsByPrefix.
     * 
     * @param subMap subMap
     * @param prefix prefix
     * @since 0.1.7
     */
    private static void deleteNsByPrefix(Map<String, GraphStoreState> subMap, String prefix) {
        Iterator<String> it = subMap.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    /**
     * Recursive copy of graph state for Python copy.deepcopy-style store isolation.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private static GraphStoreState deepCopy(GraphStoreState state) {
        if (state == null) {
            return null;
        }
        return GraphStoreState.create(state.getNs(), state.getStep(),
                (Map<String, Object>) copyValue(state.getChannelValues()),
                (List<Message>) copyValue(state.getPendingBuffer()),
                (Map<String, PendingNode>) copyValue(state.getPendingNode()),
                (Map<String, Integer>) copyValue(state.getNodeVersion()));
    }

    /**
     * copyValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Object copyValue(Object value) {
        if (value == null || isImmutableValue(value)) {
            return value;
        }
        if (value instanceof GraphStoreState graphStoreState) {
            return deepCopy(graphStoreState);
        }
        if (value instanceof TriggerMessage triggerMessage) {
            return new TriggerMessage(triggerMessage.getSender(), triggerMessage.getTarget(),
                    copyValue(triggerMessage.getPayload()));
        }
        if (value instanceof BarrierMessage barrierMessage) {
            return new BarrierMessage(barrierMessage.getSender(), barrierMessage.getTarget(),
                    copyValue(barrierMessage.getPayload()));
        }
        if (value instanceof Message message) {
            return new Message(message.getSender(), message.getTarget(), copyValue(message.getPayload()));
        }
        if (value instanceof PendingNode pendingNode) {
            @SuppressWarnings("unchecked")
            List<Exception> exceptions = (List<Exception>) copyValue(pendingNode.getExceptions());
            return new PendingNode(pendingNode.getNodeName(), pendingNode.getStatus(), exceptions);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copied = value instanceof LinkedHashMap<?, ?> ? new LinkedHashMap<>() : new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copied.put(copyValue(entry.getKey()), copyValue(entry.getValue()));
            }
            return copied;
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            for (Object item : list) {
                copied.add(copyValue(item));
            }
            return copied;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copied = value instanceof LinkedHashSet<?> ? new LinkedHashSet<>() : new HashSet<>();
            for (Object item : set) {
                copied.add(copyValue(item));
            }
            return copied;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copied = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copied.add(copyValue(item));
            }
            return copied;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object copied = Array.newInstance(type.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(copied, i, copyValue(Array.get(value, i)));
            }
            return copied;
        }
        throw new IllegalArgumentException("Unsupported graph state value for deep copy: " + type.getName());
    }

    /**
     * isImmutableValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static boolean isImmutableValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof BigInteger
                || value instanceof BigDecimal || value instanceof Temporal || value instanceof Throwable;
    }
}
