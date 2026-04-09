  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Manages stream actors for inter-node stream communication in a graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.stream_actor.manager.ActorManager}.
 */
public class ActorManager {

    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Map<String, List<String>> streamEdges;
    private final Map<String, StreamActor> streams = new LinkedHashMap<>();
    private final StreamTransform streamsTransform = new StreamTransform();
    private final boolean subGraph;
    private final BlockingQueue<Object> subWorkflowStreamQueue;

    /**
     * Create an ActorManager.
     *
     * @param streamEdges map of producer→[consumer] stream edges
     * @param graph       the stream graph with registered consumers
     * @param subGraph    whether this is a sub-graph
     * @param session     the session for configuration
     * @param compAbilitiesProvider function to get abilities for a component ID
     */
    public ActorManager(Map<String, List<String>> streamEdges,
                        StreamGraph graph,
                        boolean subGraph,
                        BaseSession session,
                        java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        this.streamEdges = streamEdges != null ? streamEdges : new HashMap<>();
        this.subGraph = subGraph;
        this.subWorkflowStreamQueue = subGraph ? new LinkedBlockingQueue<>(10 * 1024) : null;

        // Build reverse graph: consumer → [producers]
        Map<String, List<String>> reverseGraph = buildReverseGraph(this.streamEdges);

        // Get stream generator timeout from config
        long streamGenTimeout = 1;
        if (session != null && session.config() != null) {
            Object timeout = session.config().getEnv(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY);
            if (timeout instanceof Number) {
                streamGenTimeout = ((Number) timeout).longValue();
            }
        }

        for (Map.Entry<String, List<String>> entry : reverseGraph.entrySet()) {
            String consumerId = entry.getKey();
            List<String> producerIds = entry.getValue();

            // Get consumer stream abilities
            List<ComponentAbility> abilities = compAbilitiesProvider.apply(consumerId);
            List<ComponentAbility> consumerStreamAbility = new ArrayList<>();
            if (abilities != null) {
                for (ComponentAbility a : abilities) {
                    if (a == ComponentAbility.COLLECT || a == ComponentAbility.TRANSFORM) {
                        consumerStreamAbility.add(a);
                    }
                }
            }

            // Build source set
            Set<String> sources = new HashSet<>();
            for (String producerId : producerIds) {
                List<ComponentAbility> producerAbilities = compAbilitiesProvider.apply(producerId);
                if (producerAbilities != null) {
                    for (ComponentAbility a : producerAbilities) {
                        if (a == ComponentAbility.STREAM || a == ComponentAbility.TRANSFORM) {
                            sources.add(producerId + "-" + a.name());
                        }
                    }
                }
            }

            StreamConsumer consumer = graph.getNode(consumerId);
            if (consumer != null) {
                streams.put(consumerId, new StreamActor(
                        consumerId, consumer, consumerStreamAbility,
                        new ArrayList<>(sources), streamGenTimeout));
            }
        }
    }

    /**
     * Get the sub-workflow stream queue.
     *
     * @return the sub-workflow blocking queue
     */
    public BlockingQueue<Object> subWorkflowStream() {
        if (!subGraph) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STREAM_ACTOR_EXECUTION_ERROR,
                    "reason", "only sub graph has sub_workflow_stream");
        }
        return subWorkflowStreamQueue;
    }

    public StreamTransform getStreamTransform() {
        return streamsTransform;
    }

    /**
     * Produce a stream message from a producer node to its consumers.
     *
     * @param producerId     the producing node
     * @param messageContent the message content
     * @param ability        the ability type (STREAM/TRANSFORM)
     * @param firstFrame     whether this is the first frame
     */
    public void produce(String producerId, Object messageContent,
                        ComponentAbility ability, boolean firstFrame) {
        List<String> consumerIds = streamEdges.get(producerId);
        if (consumerIds != null && !consumerIds.isEmpty()) {
            for (String consumerId : consumerIds) {
                StreamActor actor = streams.get(consumerId);
                if (actor != null) {
                    Map<String, Object> message = Map.of(producerId, messageContent);
                    actor.send(message, ability, firstFrame, producerId);
                }
            }
        } else {
            logger.warning("Discard chunk send from [{}] to none consumer", producerId);
        }
    }

    /**
     * Send an end message from a producer node.
     *
     * @param producerId the producing node
     * @param ability    the ability type
     */
    public void endMessage(String producerId, ComponentAbility ability) {
        String endContent = "END_" + producerId;
        produce(producerId, endContent, ability, false);
    }

    /**
     * Consume stream data for a consumer node.
     *
     * @param consumerId     the consuming node
     * @param ability        the ability type (COLLECT/TRANSFORM)
     * @param schema         the input schema
     * @param streamCallback callback for each consumed chunk
     * @return a map of iterators matching the schema
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> consume(String consumerId, ComponentAbility ability,
                                        Object schema, Consumer<Object> streamCallback) {
        StreamActor actor = streams.get(consumerId);
        if (actor != null) {
            Map<String, Object> schemaMap = (schema instanceof Map) ? (Map<String, Object>) schema : null;
            return actor.generator(ability, schemaMap, streamCallback);
        }
        return Map.of();
    }

    /**
     * Wait until all active stream actors finish processing their queued messages.
     */
    public void awaitCompletion() {
        for (StreamActor actor : streams.values()) {
            actor.awaitCompletion();
        }
    }

    /**
     * Shutdown all stream actors.
     */
    public void shutdown() {
        for (StreamActor actor : streams.values()) {
            actor.shutdown();
        }
    }

    // ---- Helpers ----

    private static Map<String, List<String>> buildReverseGraph(Map<String, List<String>> graph) {
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            String source = entry.getKey();
            for (String target : entry.getValue()) {
                reverse.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
            }
        }
        return reverse;
    }
}
