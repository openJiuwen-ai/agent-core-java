/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.common.constants.TimeoutConstants;
import com.openjiuwen.core.common.exception.GraphError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.DictUtils;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Processes stream messages for a single node by managing message routing and
 * generating iterators for consuming stream data.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamProcessor}.
 * Uses BlockingQueue instead of asyncio.Queue, and Iterator instead of AsyncGenerator.
 * 
 * @since 0.1.7
 */
public class StreamProcessor {
    private static final LoggerProtocol logger = Loggers.GRAPH;

    /**
     * END_SENTINEL.
     * 
     * @since 0.1.7
     */
    public static final Object END_SENTINEL = new Object();

    /**
     * TIMEOUT_SENTINEL. Offered to processor queues when the main loop queue poll
     * times out, so the consumer's iterator can distinguish a genuine stream end
     * ({@link #END_SENTINEL}) from an upstream stall / crash (this sentinel).
     * {@code hasNext()} sees it, logs, and raises a
     * {@link GraphError}({@link StatusCode#STREAM_PROCESSOR_QUEUE_TIMEOUT})
     * instead of returning {@code false} (which the caller would interpret as a
     * normal stream end and silently consume incomplete data).
     *
     * @since 0.1.15
     */
    public static final Object TIMEOUT_SENTINEL = new Object();

    private final String nodeId;

    /**
     * LinkedBlockingQueue<>.
     *
     * @since 0.1.7
     */
    private final BlockingQueue<StreamPayload> queue = new LinkedBlockingQueue<>();

    /**
     * HashMap<>.
     *
     * @since 0.1.7
     */
    private final Map<String, List<BlockingQueue<Object>>> processorQueues = new HashMap<>();

    /**
     * Source groups (CNF OR-groups). The processor finishes once each group has
     * at least one handled source. Mirrors Python {@code StreamProcessor.source_groups}.
     *
     * @since 0.1.7
     */
    private final List<Set<String>> sourceGroups;

    /**
     * Union of all source keys across groups, for fast membership tests.
     *
     * @since 0.1.7
     */
    private final Set<String> sources;
    private final Set<String> completedSources = ConcurrentHashMap.newKeySet();
    private final Set<BlockingQueue<Object>> closedProcessorQueues = ConcurrentHashMap.newKeySet();

    private final long timeoutSeconds;

    /**
     * StreamProcessor.
     *
     * @param nodeId nodeId
     * @param sourceGroups sourceGroups (CNF OR-groups)
     * @param streamGeneratorTimeoutSeconds streamGeneratorTimeoutSeconds
     * @since 0.1.7
     */
    public StreamProcessor(String nodeId, List<Set<String>> sourceGroups, long streamGeneratorTimeoutSeconds) {
        this.nodeId = nodeId;
        this.sourceGroups = new ArrayList<>();
        Set<String> allSources = new HashSet<>();
        if (sourceGroups != null) {
            for (Set<String> group : sourceGroups) {
                if (group != null && !group.isEmpty()) {
                    Set<String> copy = new HashSet<>(group);
                    this.sourceGroups.add(copy);
                    allSources.addAll(copy);
                }
            }
        }
        this.sources = allSources;
        this.timeoutSeconds = streamGeneratorTimeoutSeconds > 0 ? streamGeneratorTimeoutSeconds : 0;
    }

    /**
     * Main processing loop. Reads from the queue and dispatches to processor queues.
     * Should be run on a virtual thread.
     *
     * <p>Mirrors Python {@code StreamProcessor.run}: a consumer's stream
     * processor finishes once every CNF OR-group has at least one handled source.
     * For single-source groups that source must send its end frame; for
     * multi-source groups (mutually-exclusive branches) any one source finishing
     * completes the group.
     *
     * @param ability the component ability being processed
     * @since 0.1.7
     */
    public void run(ComponentAbility ability) {
        Set<String> handleMap = new HashSet<>(completedSources);
        // source_path_map[producer_id] = set of schema paths this source produced.
        Map<String, Set<String>> sourcePathMap = new HashMap<>();
        boolean isTimedOut = false;
        try {
            for (String completedSource : completedSources) {
                closeQueuesForSource(producerIdFromSourceKey(completedSource));
            }
            if (allSourceGroupsFinished(handleMap)) {
                return;
            }

            while (true) {
                StreamPayload payload = pollPayload();
                if (payload == null) {
                    // pollPayload returns null only on timeout (upstream stall). On
                    // timeout we propagate TIMEOUT_SENTINEL so consumers distinguish a
                    // genuine stream end (END_SENTINEL) from an upstream stall.
                    // Interruption is handled separately by the catch block below —
                    // interrupt is a graceful shutdown signal, not a stall.
                    isTimedOut = true;
                    break;
                }
                processPayload(payload, handleMap, sourcePathMap);
                if (allSourceGroupsFinished(handleMap)) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            // Graceful shutdown via interrupt — close queues normally (END_SENTINEL)
            // so consumers see this as a regular stream end, not a timeout.
            Thread.currentThread().interrupt();
        } finally {
            if (isTimedOut) {
                closeAllQueuesWithTimeout();
            } else {
                closeAllQueues();
            }
        }
    }

    /**
     * Poll the next payload from the queue with the framework default blocking-queue timeout.
     *
     * @return the next payload, or null on timeout (caller should break and flag timeout)
     * @throws InterruptedException if the current thread was interrupted while polling
     * @since 0.1.7
     */
    private StreamPayload pollPayload() throws InterruptedException {
        // queue.poll() is bounded by the framework default blocking-queue timeout, so
        // an upstream producer that crashes without emitting the END frame cannot hang
        // the stream-in worker thread indefinitely. On expiry, log + return null so the
        // caller's loop exits rather than the whole thread dying silently.
        StreamPayload payload = queue.poll(
                TimeoutConstants.BLOCKING_QUEUE_MS,
                TimeUnit.MILLISECONDS);
        if (payload == null) {
            Loggers.PERFORMANCE.warning(
                    "StreamProcessor main loop queue poll timeout after {}ms, node_id={}",
                    TimeoutConstants.BLOCKING_QUEUE_MS, nodeId);
        }
        return payload;
    }

    /**
     * Process a single payload: route end-frames or data to the right processor queues.
     *
     * @param payload the stream payload to process
     * @param handleMap set of handled source keys (mutated)
     * @param sourcePathMap producer_id → schema paths produced (mutated)
     * @since 0.1.7
     */
    private void processPayload(StreamPayload payload, Set<String> handleMap,
            Map<String, Set<String>> sourcePathMap) {
        Object message = payload.getMessage();
        ComponentAbility sourceAbility = payload.getSourceAbility();
        String sourceKey = getUniqueSourceKey(payload);

        if (isEndMessage(message)) {
            String sourceId = getProducerId(message);
            handleMap.add(sourceKey);
            closeQueuesForSourceKey(sourceId, sourceKey, sourcePathMap);
        } else {
            closeInactiveGroupSources(sourceKey);
            for (Map.Entry<String, List<BlockingQueue<Object>>> entry : processorQueues.entrySet()) {
                String path = SessionUtils.extractOriginKey(entry.getKey());
                Object value = (message instanceof Map<?, ?> messageMap)
                        ? SessionUtils.getValueByNestedPath(path, (Map<String, Object>) messageMap)
                        : null;
                if (value != null) {
                    sourcePathMap.computeIfAbsent(sourceKey, k -> new HashSet<>()).add(path);
                    for (BlockingQueue<Object> q : entry.getValue()) {
                        q.offer(value);
                    }
                }
            }
        }
    }

    /**
     * Check whether every source group has at least one handled source.
     * Mirrors Python {@code StreamProcessor._all_source_groups_finished}.
     *
     * @param handledSources handledSources
     * @return the result
     * @since 0.1.7
     */
    private boolean allSourceGroupsFinished(Set<String> handledSources) {
        if (sourceGroups.isEmpty()) {
            return false;
        }
        for (Set<String> group : sourceGroups) {
            boolean hasIntersection = false;
            for (String source : group) {
                if (handledSources.contains(source)) {
                    hasIntersection = true;
                    break;
                }
            }
            if (!hasIntersection) {
                return false;
            }
        }
        return true;
    }

    /**
     * For multi-source OR-groups, when one source starts producing, close the
     * inactive alternatives so the consumer does not wait for them.
     * Mirrors Python {@code StreamProcessor._close_inactive_group_sources}.
     *
     * @param activeSourceKey activeSourceKey
     * @since 0.1.7
     */
    private void closeInactiveGroupSources(String activeSourceKey) {
        for (Set<String> group : sourceGroups) {
            if (!group.contains(activeSourceKey) || group.size() <= 1) {
                continue;
            }
            for (String inactiveSourceKey : group) {
                if (inactiveSourceKey.equals(activeSourceKey)) {
                    continue;
                }
                String sourceId = producerIdFromSourceKey(inactiveSourceKey);
                closeQueuesForSource(sourceId);
            }
        }
    }

    /**
     * Offer END_SENTINEL to processor queues that the given source actually
     * produced data for. Mirrors Python
     * {@code StreamProcessor._close_queues_for_source_key}.
     *
     * @param sourceId sourceId
     * @param sourceKey sourceKey
     * @param sourcePathMap sourcePathMap
     * @since 0.1.7
     */
    private void closeQueuesForSourceKey(String sourceId, String sourceKey,
            Map<String, Set<String>> sourcePathMap) {
        Set<String> handledPaths = sourcePathMap.get(sourceKey);
        if (handledPaths == null) {
            return;
        }
        for (String path : handledPaths) {
            for (BlockingQueue<Object> q : processorQueues.getOrDefault(path, List.of())) {
                closeQueue(q);
            }
        }
    }

    /**
     * Offer END_SENTINEL to every processor queue whose origin path belongs to
     * the given source. Mirrors Python {@code StreamProcessor._close_queues_for_source}.
     *
     * @param sourceId sourceId
     * @since 0.1.7
     */
    private void closeQueuesForSource(String sourceId) {
        for (Map.Entry<String, List<BlockingQueue<Object>>> entry : processorQueues.entrySet()) {
            String path = SessionUtils.extractOriginKey(entry.getKey());
            if (isValueFromSource(path, sourceId)) {
                for (BlockingQueue<Object> q : entry.getValue()) {
                    closeQueue(q);
                }
            }
        }
    }

    /**
     * Offer END_SENTINEL to every processor queue.
     * Mirrors Python {@code StreamProcessor._close_all_queues}.
     *
     * @since 0.1.7
     */
    private void closeAllQueues() {
        for (List<BlockingQueue<Object>> queues : processorQueues.values()) {
            for (BlockingQueue<Object> q : queues) {
                closeQueue(q);
            }
        }
    }

    /**
     * Offer TIMEOUT_SENTINEL to every processor queue. Used when the main loop poll
     * times out so consumers can distinguish an upstream stall / crash from a genuine
     * stream end. Mirrors {@link #closeAllQueues()} but with a different sentinel so
     * {@code hasNext()} raises rather than returns {@code false}.
     *
     * @since 0.1.15
     */
    private void closeAllQueuesWithTimeout() {
        for (List<BlockingQueue<Object>> queues : processorQueues.values()) {
            for (BlockingQueue<Object> q : queues) {
                closeQueueWithTimeout(q);
            }
        }
    }

    private void closeQueue(BlockingQueue<Object> processorQueue) {
        if (closedProcessorQueues.add(processorQueue)) {
            processorQueue.offer(END_SENTINEL);
        }
    }

    private void closeQueueWithTimeout(BlockingQueue<Object> processorQueue) {
        if (closedProcessorQueues.add(processorQueue)) {
            processorQueue.offer(TIMEOUT_SENTINEL);
        }
    }

    /**
     * Receive a stream message for processing.
     * 
     * @param payload the stream payload
     * @since 0.1.7
     */
    public void receive(StreamPayload payload) {
        queue.offer(payload);
    }

    /**
     * Seed source completions restored from an earlier interrupted invocation.
     *
     * @param sourceKeys completed producer-ability keys
     * @since 0.1.7
     */
    public void seedCompletedSources(Set<String> sourceKeys) {
        if (sourceKeys != null) {
            completedSources.addAll(sourceKeys);
        }
    }

    /**
     * generator.
     * 
     * @param schema schema
     * @param streamCallback streamCallback
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generator(Map<String, Object> schema, Consumer<Object> streamCallback) {
        if (schema == null || schema.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map.Entry<List<String>, Object>> inputs = new ArrayList<>();
        List<Map.Entry<List<String>, Object>> paths = DictUtils.extractLeafNodes(schema, null);

        for (Map.Entry<List<String>, Object> pathEntry : paths) {
            List<String> keyPath = pathEntry.getKey();
            Object refPath = pathEntry.getValue();
            String pathStr = DictUtils.formatPath(keyPath);

            if (!(refPath instanceof String) || !((String) refPath).contains("$")) {
                inputs.add(new AbstractMap.SimpleEntry<>(keyPath, refPath));
                continue;
            }

            inputs.add(
                    new AbstractMap.SimpleEntry<>(keyPath, createIterator(pathStr, (String) refPath, streamCallback)));
        }

        return DictUtils.rebuildDict(inputs);
    }

    /**
     * Create a blocking iterator backed by a queue for a specific schema path.
     * 
     * @param kPath kPath
     * @param rPath rPath
     * @param streamCallback streamCallback
     * @return the result
     * @since 0.1.7
     */
    private Iterator<Object> createIterator(String kPath, String rPath, Consumer<Object> streamCallback) {
        BlockingQueue<Object> iterQueue = new LinkedBlockingQueue<>();
        processorQueues.computeIfAbsent(rPath, k -> new ArrayList<>()).add(iterQueue);

        return new Iterator<>() {
            private Object next = null;
            private boolean done = false;
            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    Object msg;
                    if (timeoutSeconds > 0) {
                        msg = iterQueue.poll(timeoutSeconds, TimeUnit.SECONDS);
                    } else {
                        // iterQueue.poll() falls back to the framework default blocking-queue
                        // timeout when no explicit caller timeout is supplied, so iterator
                        // consumers are not hung even after the upstream finishes abnormally.
                        long pollMs = TimeoutConstants.BLOCKING_QUEUE_MS;
                        msg = iterQueue.poll(pollMs, TimeUnit.MILLISECONDS);
                        if (msg == null) {
                            Loggers.PERFORMANCE.warning(
                                    "StreamProcessor iterator queue poll timeout after {}ms, node_id={}, kPath={}",
                                    pollMs, nodeId, kPath);
                            done = true;
                            return false;
                        }
                    }
                    if (msg == null) {
                        // Timeout
                        logger.warning("Receive chunk timeout {}s of [{}.{}]", timeoutSeconds, nodeId, kPath);
                        done = true;
                        return false;
                    }
                    if (msg == END_SENTINEL) {
                        logger.debug("Receive EndFrame chunk of [{}.{}]", nodeId, kPath);
                        done = true;
                        return false;
                    }
                    if (msg == TIMEOUT_SENTINEL) {
                        // Upstream poll timed out; the stream did not end normally. Raise
                        // so the consumer can retry / report rather than silently treating
                        // incomplete data as a complete stream.
                        logger.warning("Receive timeout sentinel of [{}.{}]", nodeId, kPath);
                        done = true;
                        throw new GraphError(
                                StatusCode.STREAM_PROCESSOR_QUEUE_TIMEOUT,
                                Map.of("timeout", TimeoutConstants.BLOCKING_QUEUE_MS,
                                        "node_id", nodeId, "kPath", kPath));
                    }
                    logger.debug("Receive chunk of [{}.{}]", nodeId, kPath);
                    next = msg;
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done = true;
                    return false;
                }
            }

            @Override
            public Object next() {
                if (next == null && !hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                Object value = next;
                next = null;
                if (streamCallback != null) {
                    streamCallback.accept(Map.of(kPath, value));
                }
                return value;
            }
        };
    }

    // ---- Helpers ----

    static boolean isValueFromSource(String path, String sourceId) {
        return path.equals(sourceId) || path.startsWith(sourceId + ".");
    }

    /**
     * getUniqueSourceKey.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    private static String getUniqueSourceKey(StreamPayload payload) {
        String sourceId = getProducerId(payload.getMessage());
        String ability = payload.getSourceAbility().name();
        return sourceId + "-" + ability;
    }

    /**
     * Extract the producer id from a "{producer_id}-{ABILITY}" source key.
     * Mirrors Python {@code StreamProcessor._producer_id_from_source_key}.
     *
     * @param sourceKey sourceKey
     * @return the result
     * @since 0.1.7
     */
    private static String producerIdFromSourceKey(String sourceKey) {
        int idx = sourceKey.lastIndexOf('-');
        return idx > 0 ? sourceKey.substring(0, idx) : sourceKey;
    }

    @SuppressWarnings("unchecked")
    static boolean isEndMessage(Object message) {
        if (!(message instanceof Map)) {
            return false;
        }
        Map<String, Object> msgMap = (Map<String, Object>) message;
        if (msgMap.size() != 1) {
            return false;
        }
        String producerId = msgMap.keySet().iterator().next();
        Object content = msgMap.get(producerId);
        return content instanceof String && ((String) content).startsWith("END_");
    }

    @SuppressWarnings("unchecked")
    static String getProducerId(Object message) {
        if (!(message instanceof Map) || ((Map<?, ?>) message).size() != 1) {
            throw new IllegalArgumentException("message is invalid");
        }
        return ((Map<String, Object>) message).keySet().iterator().next();
    }
}
