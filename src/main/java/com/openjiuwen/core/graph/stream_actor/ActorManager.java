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
 * 
 * @since 0.1.7
 */
public class ActorManager {
    private static final LoggerProtocol logger = Loggers.GRAPH;

    private final Map<String, List<String>> streamEdges;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, StreamActor> streams = new LinkedHashMap<>();

    /**
     * StreamTransform.
     * 
     * @since 0.1.7
     */
    private final StreamTransform streamsTransform = new StreamTransform();
    private final boolean isSubGraph;
    private final BlockingQueue<Object> subWorkflowStreamQueue;

    /**
     * Create an ActorManager.
     *
     * @param streamEdges map of producer→[consumer] stream edges
     * @param streamSourceGroups map of consumer→list of source groups (CNF OR-groups)
     * @param graph the stream graph with registered consumers
     * @param isSubGraph whether this is a sub-graph
     * @param session the session for configuration
     * @param compAbilitiesProvider function to get abilities for a component ID
     * @since 0.1.7
     */
    public ActorManager(Map<String, List<String>> streamEdges,
            Map<String, List<java.util.Set<String>>> streamSourceGroups, StreamGraph graph, boolean isSubGraph,
            BaseSession session, java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        this.streamEdges = streamEdges != null ? streamEdges : new HashMap<>();
        this.isSubGraph = isSubGraph;
        this.subWorkflowStreamQueue = isSubGraph ? new LinkedBlockingQueue<>(10 * 1024) : null;

        // Build reverse graph: consumer → [producers]
        Map<String, List<String>> reverseGraph = buildReverseGraph(this.streamEdges);
        long streamGenTimeout = resolveStreamGenTimeout(session);

        for (Map.Entry<String, List<String>> entry : reverseGraph.entrySet()) {
            String consumerId = entry.getKey();
            List<ComponentAbility> consumerStreamAbility = collectConsumerStreamAbilities(consumerId,
                    compAbilitiesProvider);
            List<java.util.Set<String>> groups = resolveSourceGroups(consumerId, entry.getValue(),
                    streamSourceGroups, compAbilitiesProvider);
            StreamConsumer consumer = graph.getNode(consumerId);
            if (consumer != null) {
                streams.put(consumerId, new StreamActor(consumerId, consumer, consumerStreamAbility,
                        groups, streamGenTimeout));
            }
        }
    }

    /**
     * Legacy constructor without stream source groups. Equivalent to passing a
     * null source-groups map; the manager builds flat single-source groups.
     *
     * @param streamEdges map of producer→[consumer] stream edges
     * @param graph the stream graph with registered consumers
     * @param isSubGraph whether this is a sub-graph
     * @param session the session for configuration
     * @param compAbilitiesProvider function to get abilities for a component ID
     * @since 0.1.7
     */
    public ActorManager(Map<String, List<String>> streamEdges, StreamGraph graph, boolean isSubGraph,
            BaseSession session,
            java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        this(streamEdges, null, graph, isSubGraph, session, compAbilitiesProvider);
    }

    /**
     * Get the sub-workflow stream queue.
     * 
     * @return the sub-workflow blocking queue
     * @since 0.1.7
     */
    public BlockingQueue<Object> subWorkflowStream() {
        if (!isSubGraph) {
            throw ErrorHelper.buildError(StatusCode.GRAPH_STREAM_ACTOR_EXECUTION_ERROR, "reason",
                    "only sub graph has sub_workflow_stream");
        }
        return subWorkflowStreamQueue;
    }

    /**
     * getStreamTransform.
     * 
     * @return the result
     * @since 0.1.7
     */
    public StreamTransform getStreamTransform() {
        return streamsTransform;
    }

    /**
     * Produce a stream message from a producer node to its consumers.
     * 
     * @param producerId the producing node
     * @param messageContent the message content
     * @param ability the ability type (STREAM/TRANSFORM)
     * @param firstFrame whether this is the first frame
     * @since 0.1.7
     */
    public void produce(String producerId, Object messageContent, ComponentAbility ability, boolean firstFrame) {
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
     * @param ability the ability type
     * @since 0.1.7
     */
    public void endMessage(String producerId, ComponentAbility ability) {
        String endContent = "END_" + producerId;
        produce(producerId, endContent, ability, false);
    }

    /**
     * consume.
     * 
     * @param consumerId consumerId
     * @param ability ability
     * @param schema schema
     * @param streamCallback streamCallback
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> consume(String consumerId, ComponentAbility ability, Object schema,
            Consumer<Object> streamCallback) {
        StreamActor actor = streams.get(consumerId);
        if (actor != null) {
            Map<String, Object> schemaMap = (schema instanceof Map) ? (Map<String, Object>) schema : null;
            return actor.generator(ability, schemaMap, streamCallback);
        }
        return Map.of();
    }

    /**
     * Wait until all active stream actors finish processing their queued messages.
     * 
     * @since 0.1.7
     */
    public void awaitCompletion() {
        for (StreamActor actor : streams.values()) {
            actor.awaitCompletion();
        }
    }

    /**
     * Shutdown all stream actors.
     * 
     * @since 0.1.7
     */
    public void shutdown() {
        for (StreamActor actor : streams.values()) {
            actor.shutdown();
        }
    }

    // ---- Helpers ----

    /**
     * buildReverseGraph.
     *
     * @param graph graph
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * Resolve the stream generator timeout from the session config, defaulting
     * to 1 when unset or non-numeric.
     *
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private static long resolveStreamGenTimeout(BaseSession session) {
        if (session == null || session.config() == null) {
            return 1L;
        }
        Object timeout = session.config().getEnv(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY);
        if (timeout instanceof Number) {
            return ((Number) timeout).longValue();
        }
        return 1L;
    }

    /**
     * Collect the COLLECT/TRANSFORM abilities a consumer declares as its
     * stream-consuming abilities.
     *
     * @param consumerId consumerId
     * @param compAbilitiesProvider compAbilitiesProvider
     * @return the result
     * @since 0.1.7
     */
    private static List<ComponentAbility> collectConsumerStreamAbilities(String consumerId,
            java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        List<ComponentAbility> abilities = compAbilitiesProvider.apply(consumerId);
        List<ComponentAbility> consumerStreamAbility = new ArrayList<>();
        if (abilities == null) {
            return consumerStreamAbility;
        }
        for (ComponentAbility a : abilities) {
            if (a == ComponentAbility.COLLECT || a == ComponentAbility.TRANSFORM) {
                consumerStreamAbility.add(a);
            }
        }
        return consumerStreamAbility;
    }

    /**
     * Resolve the source groups for a consumer. Prefer the pre-computed
     * stream_source_groups (mirrors Python WorkflowSpec.stream_source_groups);
     * fall back to one flat group per producer ability for legacy callers.
     *
     * @param consumerId consumerId
     * @param producerIds producerIds
     * @param streamSourceGroups streamSourceGroups
     * @param compAbilitiesProvider compAbilitiesProvider
     * @return the result
     * @since 0.1.7
     */
    private static List<java.util.Set<String>> resolveSourceGroups(String consumerId, List<String> producerIds,
            Map<String, List<java.util.Set<String>>> streamSourceGroups,
            java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        List<java.util.Set<String>> groups = null;
        if (streamSourceGroups != null) {
            groups = streamSourceGroups.get(consumerId);
        }
        if (groups == null || groups.isEmpty()) {
            return buildFlatSourceGroups(producerIds, compAbilitiesProvider);
        }
        return new ArrayList<>(groups);
    }

    /**
     * Build one flat single-source group per producer ability as a fallback when
     * no pre-computed source groups are supplied. Mirrors the legacy behavior
     * of {@link ActorManager#ActorManager(Map, StreamGraph, boolean, BaseSession,
     * java.util.function.Function)}.
     *
     * @param producerIds producerIds
     * @param compAbilitiesProvider compAbilitiesProvider
     * @return the result
     * @since 0.1.7
     */
    private static List<java.util.Set<String>> buildFlatSourceGroups(List<String> producerIds,
            java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        Set<String> flatSources = collectFlatSources(producerIds, compAbilitiesProvider);
        List<java.util.Set<String>> groups = new ArrayList<>();
        for (String source : new java.util.TreeSet<>(flatSources)) {
            Set<String> single = new HashSet<>();
            single.add(source);
            groups.add(single);
        }
        return groups;
    }

    /**
     * Collect STREAM/TRANSFORM source keys (producer-id + ability name) from
     * every producer in the list. Returns the union set across all producers.
     *
     * @param producerIds producerIds
     * @param compAbilitiesProvider compAbilitiesProvider
     * @return the result
     * @since 0.1.7
     */
    private static Set<String> collectFlatSources(List<String> producerIds,
            java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider) {
        Set<String> flatSources = new HashSet<>();
        for (String producerId : producerIds) {
            appendProducerStreamSources(producerId, compAbilitiesProvider, flatSources);
        }
        return flatSources;
    }

    /**
     * Append the STREAM/TRANSFORM source keys of a single producer to the
     * given sink set.
     *
     * @param producerId producerId
     * @param compAbilitiesProvider compAbilitiesProvider
     * @param sink sink
     * @since 0.1.7
     */
    private static void appendProducerStreamSources(String producerId,
            java.util.function.Function<String, List<ComponentAbility>> compAbilitiesProvider,
            Set<String> sink) {
        List<ComponentAbility> producerAbilities = compAbilitiesProvider.apply(producerId);
        if (producerAbilities == null) {
            return;
        }
        for (ComponentAbility a : producerAbilities) {
            if (a == ComponentAbility.STREAM || a == ComponentAbility.TRANSFORM) {
                sink.add(producerId + "-" + a.name());
            }
        }
    }
}
