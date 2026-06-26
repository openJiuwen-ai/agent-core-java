/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.stream.AsyncStreamQueue;
import com.openjiuwen.core.workflow.NodeSpec;
import com.openjiuwen.core.workflow.WorkflowSpec;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Mirrors Python's {@code ActorManager} in
 * {@code openjiuwen/core/graph/stream_actor/manager.py}.
 */
public class ActorManager {

    private static final LoggerProtocol LOGGER = Loggers.GRAPH;
    private static final int SUB_WORKFLOW_STREAM_MAX_SIZE = 10 * 1024;
    private static final double DEFAULT_STREAM_GENERATOR_TIMEOUT_SECONDS = 1.0d;

    private final Map<String, List<String>> streamEdges;
    private final Map<String, StreamActor> streams = new LinkedHashMap<>();
    private final StreamTransform streamsTransform = new StreamTransform();
    private final Map<String, Set<ComponentAbility>> activeProducerIds = new LinkedHashMap<>();
    private final Map<String, List<String>> consumerDict;
    private final Map<String, Set<ComponentAbility>> producerAbilities = new LinkedHashMap<>();
    private final Map<String, List<Set<String>>> streamSourceGroups = new LinkedHashMap<>();
    private final ActorManagerSession workflowSession;
    private final boolean subGraph;
    private final AsyncStreamQueue subWorkflowStream;

    public ActorManager(
            WorkflowSpec workflowSpec,
            StreamGraph graph,
            boolean subGraph,
            ActorManagerSession session) {
        this.streamEdges = copyStringListMap(workflowSpec.getStreamEdges());
        this.consumerDict = buildReverseGraph(this.streamEdges);
        this.workflowSession = session;

        for (Map.Entry<String, List<String>> entry : consumerDict.entrySet()) {
            String consumerId = entry.getKey();
            List<String> producerIds = entry.getValue();
            List<ComponentAbility> consumerStreamAbility = streamAbilitiesForConsumer(workflowSpec, consumerId);
            LinkedHashSet<String> sources = new LinkedHashSet<>();

            for (String producerId : producerIds) {
                Set<ComponentAbility> abilities = producerAbilities.computeIfAbsent(
                        producerId, ignored -> new LinkedHashSet<>());
                for (ComponentAbility ability : abilitiesForNode(workflowSpec, producerId)) {
                    if (ability == ComponentAbility.STREAM || ability == ComponentAbility.TRANSFORM) {
                        abilities.add(ability);
                        sources.add(sourceKey(producerId, ability));
                    }
                }
            }

            List<List<String>> sourceGroups = sourceGroupsForConsumer(workflowSpec, consumerId, sources);
            streamSourceGroups.put(consumerId, toSetGroups(sourceGroups));
            streams.put(consumerId, new StreamActor(
                    consumerId,
                    graph.getNode(consumerId),
                    consumerStreamAbility,
                    sourceGroups,
                    streamGeneratorTimeoutSeconds(session)));
        }

        this.subGraph = subGraph;
        this.subWorkflowStream = subGraph ? new AsyncStreamQueue(SUB_WORKFLOW_STREAM_MAX_SIZE) : null;
    }

    /**
     * Returns the sub-workflow stream queue.
     *
     * @return sub-workflow stream queue
     */
    public AsyncStreamQueue subWorkflowStream() {
        if (!subGraph) {
            throw ErrorHelper.buildError(
                    StatusCode.GRAPH_STREAM_ACTOR_EXECUTION_ERROR,
                    "reason",
                    "only sub graph has sub_workflow_stream");
        }
        return subWorkflowStream;
    }

    /**
     * Records that a producer emitted a stream frame with the given ability.
     *
     * @param producerId producer node id
     * @param ability component ability
     */
    public void activeProduceAbility(String producerId, ComponentAbility ability) {
        Set<ComponentAbility> abilities = activeProducerIds.computeIfAbsent(producerId, ignored -> new LinkedHashSet<>());
        abilities.add(ability);
    }

    /**
     * Marks a producer as done in workflow state.
     *
     * @param producerId producer node id
     */
    public void markProducerDone(String producerId) {
        WorkflowCommitState state = workflowSession.state();
        List<String> finishedStreamNodes = mutableStringList(state.getWorkflowState("finished_stream_nodes"));
        if (!finishedStreamNodes.contains(producerId)) {
            finishedStreamNodes.add(producerId);
        }
        state.updateAndCommitWorkflowState(Map.of("finished_stream_nodes", finishedStreamNodes));
    }

    /**
     * Returns true when a stream source is known to be inactive for this run.
     *
     * @param consumerId consumer node id
     * @param producerId producer node id
     * @return whether this source should be sanitized
     */
    public boolean shouldSanitizeStreamSource(String consumerId, String producerId) {
        List<Set<String>> sourceGroups = streamSourceGroups.getOrDefault(consumerId, List.of());
        List<Set<String>> matchedGroups = new ArrayList<>();
        for (Set<String> group : sourceGroups) {
            for (String sourceKey : group) {
                if (sourceKeyMatchesProducer(sourceKey, producerId)) {
                    matchedGroups.add(group);
                    break;
                }
            }
        }
        if (matchedGroups.isEmpty()) {
            return true;
        }

        for (Set<String> group : matchedGroups) {
            if (group.size() == 1) {
                return false;
            }
            if (groupHasActiveAlternative(group, producerId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns stream transform helpers.
     *
     * @return stream transform helpers
     */
    public StreamTransform streamTransform() {
        return streamsTransform;
    }

    /**
     * Sends a stream frame from producer to every configured stream consumer.
     *
     * @param producerId producer node id
     * @param messageContent message content
     * @param ability producer ability
     * @param firstFrame whether this is the first frame
     */
    public void produce(String producerId, Object messageContent, ComponentAbility ability, boolean firstFrame) {
        activeProduceAbility(producerId, ability);
        List<String> consumerIds = streamEdges.get(producerId);
        if (consumerIds != null && !consumerIds.isEmpty()) {
            for (String consumerId : consumerIds) {
                StreamActor actor = getActor(consumerId);
                Map<String, Object> message = new LinkedHashMap<>();
                message.put(producerId, messageContent);
                actor.send(message, ability, firstFrame, producerId);
            }
            return;
        }

        LOGGER.warning("Discard chunk send from [{}] to none consumer", producerId);
    }

    /**
     * Sends an end message for a producer/ability pair.
     *
     * @param producerId producer node id
     * @param ability producer ability
     */
    public void endMessage(String producerId, ComponentAbility ability) {
        produce(producerId, "END_" + producerId, ability, false);
    }

    /**
     * Creates consumer stream inputs and closes already-finished inactive producers.
     *
     * @param consumerId consumer node id
     * @param ability consumer ability
     * @param schema stream schema
     * @param streamCallback optional callback invoked by the underlying stream processor
     * @return generated stream input map
     */
    public Map<String, Object> consume(
            String consumerId,
            ComponentAbility ability,
            Map<String, Object> schema,
            Consumer<Map<String, Object>> streamCallback) {
        StreamActor actor = getActor(consumerId);
        Map<String, Object> consumeIter = actor.generator(ability, schema, streamCallback);
        List<String> producerIds = consumerDict.getOrDefault(consumerId, List.of());
        List<String> finishedStreamNodes = mutableStringList(
                workflowSession.state().getWorkflowState("finished_stream_nodes"));

        for (String producerId : producerIds) {
            if (!finishedStreamNodes.contains(producerId)) {
                continue;
            }
            Set<ComponentAbility> allAbilities = producerAbilities.getOrDefault(producerId, Set.of());
            Set<ComponentAbility> activeAbilities = activeProducerIds.getOrDefault(producerId, Set.of());
            if (activeAbilities.isEmpty()) {
                for (ComponentAbility producerAbility : allAbilities) {
                    endMessage(producerId, producerAbility);
                    activeProduceAbility(producerId, producerAbility);
                }
            }
        }
        return consumeIter;
    }

    /**
     * Shuts down every managed stream actor.
     */
    public void shutdown() {
        for (StreamActor actor : streams.values()) {
            actor.shutdown();
        }
    }

    private StreamActor getActor(String consumerId) {
        return streams.get(consumerId);
    }

    private boolean groupHasActiveAlternative(Set<String> group, String producerId) {
        for (String sourceKey : group) {
            SourceRef sourceRef = splitSourceKey(sourceKey);
            if (sourceRef.producerId().equals(producerId)) {
                continue;
            }
            if (activeProducerIds.getOrDefault(sourceRef.producerId(), Set.of()).contains(sourceRef.ability())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sourceKeyMatchesProducer(String sourceKey, String producerId) {
        return splitSourceKey(sourceKey).producerId().equals(producerId);
    }

    private static SourceRef splitSourceKey(String sourceKey) {
        int splitIndex = sourceKey.lastIndexOf('-');
        if (splitIndex < 0) {
            throw new IllegalArgumentException("Unknown component ability: " + sourceKey);
        }
        String producerId = sourceKey.substring(0, splitIndex);
        String abilityName = sourceKey.substring(splitIndex + 1);
        for (ComponentAbility ability : ComponentAbility.values()) {
            if (ability.getAbilityName().equals(abilityName)) {
                return new SourceRef(producerId, ability);
            }
        }
        throw new IllegalArgumentException("Unknown component ability: " + abilityName);
    }

    private static List<ComponentAbility> streamAbilitiesForConsumer(WorkflowSpec workflowSpec, String consumerId) {
        List<ComponentAbility> abilities = new ArrayList<>();
        for (ComponentAbility ability : abilitiesForNode(workflowSpec, consumerId)) {
            if (ability == ComponentAbility.COLLECT || ability == ComponentAbility.TRANSFORM) {
                abilities.add(ability);
            }
        }
        return Collections.unmodifiableList(abilities);
    }

    private static List<ComponentAbility> abilitiesForNode(WorkflowSpec workflowSpec, String nodeId) {
        NodeSpec nodeSpec = workflowSpec.getCompConfigs().get(nodeId);
        if (nodeSpec == null || nodeSpec.getAbilities() == null) {
            return List.of();
        }
        return nodeSpec.getAbilities();
    }

    private static List<List<String>> sourceGroupsForConsumer(
            WorkflowSpec workflowSpec,
            String consumerId,
            LinkedHashSet<String> sources) {
        List<List<String>> configuredGroups = workflowSpec.getStreamSourceGroups().get(consumerId);
        if (configuredGroups != null && !configuredGroups.isEmpty()) {
            return normalizeSourceGroups(configuredGroups);
        }

        List<String> sortedSources = new ArrayList<>(sources);
        sortedSources.sort(Comparator.naturalOrder());
        List<List<String>> singletonGroups = new ArrayList<>();
        for (String source : sortedSources) {
            singletonGroups.add(List.of(source));
        }
        return Collections.unmodifiableList(singletonGroups);
    }

    private static List<List<String>> normalizeSourceGroups(List<List<String>> sourceGroups) {
        List<List<String>> normalized = new ArrayList<>();
        for (List<String> group : sourceGroups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            normalized.add(Collections.unmodifiableList(new ArrayList<>(group)));
        }
        return Collections.unmodifiableList(normalized);
    }

    private static List<Set<String>> toSetGroups(List<List<String>> sourceGroups) {
        List<Set<String>> normalized = new ArrayList<>();
        for (List<String> group : sourceGroups) {
            if (group == null || group.isEmpty()) {
                continue;
            }
            normalized.add(Collections.unmodifiableSet(new LinkedHashSet<>(group)));
        }
        return Collections.unmodifiableList(normalized);
    }

    private static double streamGeneratorTimeoutSeconds(ActorManagerSession session) {
        Object timeout = session.config().getEnv(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY);
        if (timeout instanceof Number number) {
            return number.doubleValue();
        }
        if (timeout instanceof String value) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                return DEFAULT_STREAM_GENERATOR_TIMEOUT_SECONDS;
            }
        }
        return DEFAULT_STREAM_GENERATOR_TIMEOUT_SECONDS;
    }

    private static Map<String, List<String>> buildReverseGraph(Map<String, List<String>> graph) {
        Map<String, List<String>> reverseGraph = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            String source = entry.getKey();
            for (String target : entry.getValue()) {
                reverseGraph.computeIfAbsent(target, ignored -> new ArrayList<>()).add(source);
            }
        }
        return reverseGraph;
    }

    private static String sourceKey(String producerId, ComponentAbility ability) {
        return producerId + "-" + ability.getAbilityName();
    }

    private static Map<String, List<String>> copyStringListMap(Map<String, List<String>> rawMap) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (rawMap == null) {
            return copied;
        }
        for (Map.Entry<String, List<String>> entry : rawMap.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return copied;
    }

    private static List<String> mutableStringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private record SourceRef(String producerId, ComponentAbility ability) {
    }
}
