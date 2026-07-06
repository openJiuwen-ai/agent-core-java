/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code StreamProcessor} in
 * {@code openjiuwen/core/graph/stream_actor/base.py}.
 */
public class StreamProcessor {

    private static final LoggerProtocol LOGGER = Loggers.GRAPH;
    private static final long MILLIS_PER_SECOND = 1000L;

    private final String nodeId;
    private final BlockingQueue<StreamPayload> queue = new LinkedBlockingQueue<>();
    private final Map<String, List<BlockingQueue<Object>>> processorQueues = new ConcurrentHashMap<>();
    private final List<Set<String>> sourceGroups;
    private final Set<String> sourceIds;
    private final boolean hasTimeout;
    private final long timeoutMillis;

    public StreamProcessor(String nodeId, List<List<String>> sourceGroups, double streamGeneratorTimeoutSeconds) {
        this.nodeId = nodeId;
        this.sourceGroups = normalizeSourceGroups(sourceGroups);
        this.sourceIds = collectSourceIds(this.sourceGroups);
        this.hasTimeout = streamGeneratorTimeoutSeconds > 0.0d;
        this.timeoutMillis = hasTimeout
                ? Math.max(1L, Math.round(streamGeneratorTimeoutSeconds * MILLIS_PER_SECOND))
                : 0L;
    }

    /**
     * Runs the queue processor until every configured source group has produced an end frame.
     *
     * @param ability consumer ability associated with this processor
     */
    public void run(ComponentAbility ability) {
        Set<String> handledSources = new HashSet<>();
        Map<String, Set<String>> sourcePathMap = new LinkedHashMap<>();
        while (true) {
            StreamPayload payload;
            try {
                payload = queue.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }

            Object message = payload.getMessage();
            String sourceKey = getUniqueSourceKey(payload);
            if (isEndMessage(message)) {
                String sourceId = getProducerId(message);
                handledSources.add(sourceKey);
                closeQueuesForSourceKey(sourceId, sourceKey, sourcePathMap);

                if (allSourceGroupsFinished(handledSources)) {
                    closeAllQueues(sourceId);
                }
            } else {
                closeInactiveGroupSources(sourceKey);
                routeMessageValue(message, sourceKey, sourcePathMap);
            }

            if (allSourceGroupsFinished(handledSources)) {
                break;
            }
        }
    }

    /**
     * Enqueues a stream payload for processing.
     *
     * @param payload stream payload
     */
    public void receive(StreamPayload payload) {
        queue.offer(payload);
    }

    /**
     * Creates a nested map whose stream references are backed by blocking iterators.
     *
     * @param schema input schema
     * @param streamCallback optional callback invoked after each yielded chunk
     * @return nested map matching the schema
     */
    public Map<String, Object> generator(Map<String, Object> schema, Consumer<Map<String, Object>> streamCallback) {
        if (schema == null || schema.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DictUtils.PathValuePair> inputs = new ArrayList<>();
        for (DictUtils.PathValuePair path : DictUtils.extractLeafNodes(schema)) {
            List<String> keyPath = path.path();
            Object refPath = path.value();
            String pathStr = DictUtils.formatPath(keyPath);
            if (!(refPath instanceof String refPathValue) || !refPathValue.contains("$")) {
                inputs.add(new DictUtils.PathValuePair(keyPath, refPath));
                continue;
            }
            inputs.add(new DictUtils.PathValuePair(
                    keyPath,
                    createIterator(pathStr, refPathValue, streamCallback)));
        }

        return castStringObjectMap(DictUtils.rebuildDict(inputs));
    }

    boolean allSourceGroupsFinished(Set<String> handledSources) {
        if (sourceGroups.isEmpty()) {
            return false;
        }
        for (Set<String> group : sourceGroups) {
            if (Collections.disjoint(group, handledSources)) {
                return false;
            }
        }
        return true;
    }

    private void routeMessageValue(Object message, String sourceKey, Map<String, Set<String>> sourcePathMap) {
        for (Map.Entry<String, List<BlockingQueue<Object>>> entry : processorQueues.entrySet()) {
            String path = entry.getKey();
            String originPath = SessionUtils.extractOriginKey(path);
            Object value = SessionUtils.getValueByNestedPath(originPath, message);
            if (value == null) {
                continue;
            }
            sourcePathMap.computeIfAbsent(sourceKey, ignored -> new LinkedHashSet<>()).add(path);
            for (BlockingQueue<Object> destinationQueue : entry.getValue()) {
                destinationQueue.offer(value);
            }
        }
    }

    private void closeInactiveGroupSources(String activeSourceKey) {
        for (Set<String> group : sourceGroups) {
            if (!group.contains(activeSourceKey) || group.size() <= 1) {
                continue;
            }
            for (String inactiveSourceKey : group) {
                if (!inactiveSourceKey.equals(activeSourceKey)) {
                    closeQueuesForSource(producerIdFromSourceKey(inactiveSourceKey));
                }
            }
        }
    }

    private void closeQueuesForSource(String sourceId) {
        for (Map.Entry<String, List<BlockingQueue<Object>>> entry : processorQueues.entrySet()) {
            String originPath = SessionUtils.extractOriginKey(entry.getKey());
            if (isValueFromSource(originPath, sourceId)) {
                putEndFrame(sourceId, entry.getValue());
            }
        }
    }

    private void closeQueuesForSourceKey(String sourceId, String sourceKey, Map<String, Set<String>> sourcePathMap) {
        Set<String> handledPaths = sourcePathMap.get(sourceKey);
        if (handledPaths == null || handledPaths.isEmpty()) {
            closeQueuesForSource(sourceId);
            return;
        }
        for (String path : handledPaths) {
            List<BlockingQueue<Object>> destinations = processorQueues.get(path);
            if (destinations != null) {
                putEndFrame(sourceId, destinations);
            }
        }
    }

    private void closeAllQueues(String sourceId) {
        for (List<BlockingQueue<Object>> destinations : processorQueues.values()) {
            putEndFrame(sourceId, destinations);
        }
    }

    private void putEndFrame(String sourceId, Collection<BlockingQueue<Object>> destinations) {
        SessionUtils.EndFrame endFrame = new SessionUtils.EndFrame(sourceId);
        for (BlockingQueue<Object> destination : destinations) {
            destination.offer(endFrame);
        }
    }

    private Iterator<Object> createIterator(
            String keyPath,
            String referencePath,
            Consumer<Map<String, Object>> streamCallback) {
        BlockingQueue<Object> iteratorQueue = new LinkedBlockingQueue<>();
        processorQueues.computeIfAbsent(referencePath, ignored -> new CopyOnWriteArrayList<>()).add(iteratorQueue);
        boolean useTimeout = hasTimeout && !pathHasDeclaredSource(referencePath);

        return new Iterator<>() {
            private Object next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    Object message = pollNextMessage(iteratorQueue, useTimeout);
                    if (message == null) {
                        LOGGER.warning("Receive chunk timeout {}ms of [{}.{}]",
                                timeoutMillis, nodeId, keyPath);
                        done = true;
                        return false;
                    }
                    if (message instanceof SessionUtils.EndFrame) {
                        LOGGER.debug("Receive EndFrame chunk of [{}.{}]", nodeId, keyPath);
                        done = true;
                        return false;
                    }
                    LOGGER.debug("Receive chunk of [{}.{}]", nodeId, keyPath);
                    next = message;
                    return true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    done = true;
                    return false;
                }
            }

            @Override
            public Object next() {
                if (next == null && !hasNext()) {
                    throw new NoSuchElementException();
                }
                Object value = next;
                next = null;
                if (streamCallback != null) {
                    streamCallback.accept(Map.of(keyPath, value));
                }
                return value;
            }
        };
    }

    private Object pollNextMessage(BlockingQueue<Object> iteratorQueue, boolean useTimeout)
            throws InterruptedException {
        if (useTimeout) {
            return iteratorQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        return iteratorQueue.take();
    }

    private boolean pathHasDeclaredSource(String referencePath) {
        String originKey = SessionUtils.extractOriginKey(referencePath);
        if (originKey == null || originKey.isEmpty()) {
            return false;
        }
        String sourceId = originKey.split("\\.", 2)[0];
        return sourceIds.contains(sourceId);
    }

    public static boolean isValueFromSource(String path, String sourceId) {
        return path != null && sourceId != null && (path.equals(sourceId) || path.startsWith(sourceId + "."));
    }

    static String getUniqueSourceKey(StreamPayload payload) {
        String sourceId = getProducerId(payload.getMessage());
        return sourceId + "-" + payload.getSourceAbility().getAbilityName();
    }

    static String producerIdFromSourceKey(String sourceKey) {
        int splitIndex = sourceKey.lastIndexOf('-');
        return splitIndex < 0 ? sourceKey : sourceKey.substring(0, splitIndex);
    }

    public static boolean isEndMessage(Object message) {
        Map.Entry<?, ?> entry = singleMessageEntry(message);
        Object messageContent = entry.getValue();
        return messageContent instanceof String text && text.startsWith("END_");
    }

    public static String getProducerId(Object message) {
        return String.valueOf(singleMessageEntry(message).getKey());
    }

    private static Map.Entry<?, ?> singleMessageEntry(Object message) {
        if (!(message instanceof Map<?, ?> map) || map.size() != 1) {
            throw new IllegalArgumentException("message is invalid");
        }
        return map.entrySet().iterator().next();
    }

    private static List<Set<String>> normalizeSourceGroups(List<List<String>> rawSourceGroups) {
        if (rawSourceGroups == null || rawSourceGroups.isEmpty()) {
            return List.of();
        }
        List<Set<String>> normalized = new ArrayList<>();
        for (List<String> group : rawSourceGroups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>(group);
            if (!values.isEmpty()) {
                normalized.add(Collections.unmodifiableSet(values));
            }
        }
        return Collections.unmodifiableList(normalized);
    }

    private static Set<String> collectSourceIds(List<Set<String>> sourceGroups) {
        Set<String> ids = new LinkedHashSet<>();
        for (Set<String> group : sourceGroups) {
            for (String sourceKey : group) {
                ids.add(producerIdFromSourceKey(sourceKey));
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }
}
