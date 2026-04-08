/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Processes stream messages for a single node by managing message routing and
 * generating iterators for consuming stream data.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.base.StreamProcessor}.
 * Uses BlockingQueue instead of asyncio.Queue, and Iterator instead of AsyncGenerator.
 */
public class StreamProcessor {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    /** Sentinel object to mark end of stream */
    public static final Object END_SENTINEL = new Object();

    private final String nodeId;
    private final BlockingQueue<StreamPayload> queue = new LinkedBlockingQueue<>();
    private final Map<String, List<BlockingQueue<Object>>> processorQueues = new HashMap<>();
    private final Set<String> sources;
    private final long timeoutSeconds;

    public StreamProcessor(String nodeId, List<String> sources, long streamGeneratorTimeoutSeconds) {
        this.nodeId = nodeId;
        this.sources = new HashSet<>(sources);
        this.timeoutSeconds = streamGeneratorTimeoutSeconds > 0 ? streamGeneratorTimeoutSeconds : 0;
    }

    /**
     * Main processing loop. Reads from the queue and dispatches to processor queues.
     * Should be run on a virtual thread.
     *
     * @param ability the component ability being processed
     */
    public void run(ComponentAbility ability) {
        Set<String> handleMap = new HashSet<>();
        Map<ComponentAbility, Set<String>> sourceMap = new HashMap<>();

        while (true) {
            StreamPayload payload;
            try {
                payload = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Object message = payload.getMessage();
            ComponentAbility sourceAbility = payload.getSourceAbility();
            String sourceKey = getUniqueSourceKey(payload);

            if (isEndMessage(message)) {
                String sourceId = getProducerId(message);
                handleMap.add(sourceKey);
                for (Map.Entry<String, List<BlockingQueue<Object>>> entry : processorQueues.entrySet()) {
                    String path = SessionUtils.extractOriginKey(entry.getKey());
                    boolean isHandled = false;
                    Set<String> paths = sourceMap.get(sourceAbility);
                    if (paths != null) {
                        isHandled = paths.contains(path);
                    }
                    boolean isAllFinish = handleMap.equals(sources);
                    if ((isHandled || isAllFinish) && isValueFromSource(path, sourceId)) {
                        for (BlockingQueue<Object> q : entry.getValue()) {
                            q.offer(END_SENTINEL);
                        }
                    }
                }
            } else {
                for (Map.Entry<String, List<BlockingQueue<Object>>> entry : processorQueues.entrySet()) {
                    String path = SessionUtils.extractOriginKey(entry.getKey());
                    Object value = (message instanceof Map<?, ?> messageMap)
                            ? SessionUtils.getValueByNestedPath(path, (Map<String, Object>) messageMap)
                            : null;
                    if (value != null) {
                        sourceMap.computeIfAbsent(sourceAbility, k -> new HashSet<>()).add(path);
                        for (BlockingQueue<Object> q : entry.getValue()) {
                            q.offer(value);
                        }
                    }
                }
            }

            if (handleMap.equals(sources)) {
                break;
            }
        }
    }

    /**
     * Receive a stream message for processing.
     *
     * @param payload the stream payload
     */
    public void receive(StreamPayload payload) {
        queue.offer(payload);
    }

    /**
     * Create a generator (iterator) map based on the schema.
     * Each leaf path in the schema that references a stream variable gets a BlockingQueue-backed iterator.
     *
     * @param schema         the input schema map
     * @param streamCallback optional callback for each consumed chunk
     * @return a map of iterators for stream consumption
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

            inputs.add(new AbstractMap.SimpleEntry<>(
                    keyPath,
                    createIterator(pathStr, (String) refPath, streamCallback)));
        }

        return DictUtils.rebuildDict(inputs);
    }

    /**
     * Create a blocking iterator backed by a queue for a specific schema path.
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
                        msg = iterQueue.take();
                    }
                    if (msg == null) {
                        // Timeout
                        logger.warning("Receive chunk timeout {}s of [{}.{}]",
                                timeoutSeconds, nodeId, kPath);
                        done = true;
                        return false;
                    }
                    if (msg == END_SENTINEL) {
                        logger.debug("Receive EndFrame chunk of [{}.{}]", nodeId, kPath);
                        done = true;
                        return false;
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

    private static String getUniqueSourceKey(StreamPayload payload) {
        String sourceId = getProducerId(payload.getMessage());
        String ability = payload.getSourceAbility().name();
        return sourceId + "-" + ability;
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
