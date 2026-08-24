/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.workflow.NodeSpec;
import com.openjiuwen.core.workflow.WorkflowSpec;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python focused stream manager behavior in
 * {@code openjiuwen/core/graph/stream_actor/manager.py}.
 */
class ActorManagerTest {

    @Test
    @DisplayName("default source groups use sorted producer ability singleton groups")
    void defaultSourceGroupsUseSortedProducerAbilitySingletonGroups() {
        ActorManager manager = newManager(
                Map.of("b", List.of("sink"), "a", List.of("sink")),
                Map.of(),
                Map.of(
                        "a", new NodeSpec(List.of(ComponentAbility.STREAM)),
                        "b", new NodeSpec(List.of(ComponentAbility.TRANSFORM)),
                        "sink", new NodeSpec(List.of(ComponentAbility.COLLECT))));

        assertFalse(manager.shouldSanitizeStreamSource("sink", "a"));
        assertFalse(manager.shouldSanitizeStreamSource("sink", "b"));
        assertTrue(manager.shouldSanitizeStreamSource("sink", "ghost"));
    }

    @Test
    @DisplayName("active alternative in configured group sanitizes inactive producer")
    void activeAlternativeInConfiguredGroupSanitizesInactiveProducer() {
        ActorManager manager = newManager(
                Map.of("left", List.of("sink"), "right", List.of("sink")),
                Map.of("sink", List.of(List.of("left-stream", "right-transform"))),
                Map.of(
                        "left", new NodeSpec(List.of(ComponentAbility.STREAM)),
                        "right", new NodeSpec(List.of(ComponentAbility.TRANSFORM)),
                        "sink", new NodeSpec(List.of(ComponentAbility.COLLECT))));

        assertFalse(manager.shouldSanitizeStreamSource("sink", "left"));

        manager.activeProduceAbility("right", ComponentAbility.TRANSFORM);

        assertTrue(manager.shouldSanitizeStreamSource("sink", "left"));
        assertFalse(manager.shouldSanitizeStreamSource("sink", "right"));
    }

    @Test
    @DisplayName("consume sends end message for finished inactive producer abilities")
    void consumeSendsEndMessageForFinishedInactiveProducerAbilities() throws Exception {
        SimpleSession session = new SimpleSession();
        ActorManager manager = newManager(
                Map.of("source", List.of("sink")),
                Map.of(),
                Map.of(
                        "source", new NodeSpec(List.of(ComponentAbility.STREAM)),
                        "sink", new NodeSpec(List.of(ComponentAbility.COLLECT))),
                session);
        manager.markProducerDone("source");

        Map<String, Object> generated = manager.consume("sink", ComponentAbility.COLLECT, Map.of(), null);

        assertEquals(Map.of(), generated);
        assertTrue(activeProducerIds(manager).get("source").contains(ComponentAbility.STREAM));
        assertEquals(List.of("source"), session.state().getWorkflowState("finished_stream_nodes"));
    }

    @Test
    @DisplayName("sub workflow stream exists only for sub graph")
    void subWorkflowStreamExistsOnlyForSubGraph() {
        ActorManager mainGraphManager = newManager(
                Map.of(),
                Map.of(),
                Map.of(),
                new SimpleSession(),
                false);

        assertThrows(RuntimeException.class, mainGraphManager::subWorkflowStream);

        ActorManager subGraphManager = newManager(
                Map.of(),
                Map.of(),
                Map.of(),
                new SimpleSession(),
                true);

        assertNotNull(subGraphManager.subWorkflowStream());
        assertSame(subGraphManager.subWorkflowStream(), subGraphManager.subWorkflowStream());
    }

    @Test
    @DisplayName("stream transform delegates custom transformer and default schema resolution")
    void streamTransformDelegatesCustomTransformerAndDefaultSchemaResolution() {
        StreamTransform transform = new StreamTransform();
        Map<String, Object> origin = Map.of("source", Map.of("answer", 42));

        Map<String, Object> custom = transform.getByDefinedTransformer(
                origin,
                data -> Map.of("value", ((Map<?, ?>) data.get("source")).get("answer")));
        Map<String, Object> bySchema = transform.getByDefaultTransformer(
                origin,
                Map.of("value", "${source.answer}"));

        assertEquals(Map.of("value", 42), custom);
        assertEquals(Map.of("value", 42), bySchema);
    }

    private static ActorManager newManager(
            Map<String, List<String>> streamEdges,
            Map<String, List<List<String>>> streamSourceGroups,
            Map<String, NodeSpec> compConfigs) {
        return newManager(streamEdges, streamSourceGroups, compConfigs, new SimpleSession());
    }

    private static ActorManager newManager(
            Map<String, List<String>> streamEdges,
            Map<String, List<List<String>>> streamSourceGroups,
            Map<String, NodeSpec> compConfigs,
            SimpleSession session) {
        return newManager(streamEdges, streamSourceGroups, compConfigs, session, false);
    }

    private static ActorManager newManager(
            Map<String, List<String>> streamEdges,
            Map<String, List<List<String>>> streamSourceGroups,
            Map<String, NodeSpec> compConfigs,
            SimpleSession session,
            boolean subGraph) {
        WorkflowSpec spec = new WorkflowSpec(streamEdges, compConfigs, streamSourceGroups);
        StreamGraph graph = new StreamGraph();
        for (String consumerId : reverseConsumers(streamEdges)) {
            graph.addStreamConsumer(new PassiveConsumer(), consumerId);
        }
        return new ActorManager(spec, graph, subGraph, session);
    }

    private static Set<String> reverseConsumers(Map<String, List<String>> streamEdges) {
        java.util.LinkedHashSet<String> consumers = new java.util.LinkedHashSet<>();
        for (List<String> targets : streamEdges.values()) {
            consumers.addAll(targets);
        }
        return consumers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<ComponentAbility>> activeProducerIds(ActorManager manager) throws Exception {
        Field field = ActorManager.class.getDeclaredField("activeProducerIds");
        field.setAccessible(true);
        return (Map<String, Set<ComponentAbility>>) field.get(manager);
    }

    private static final class PassiveConsumer implements StreamConsumer {
        @Override
        public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback) {
            latch.countDown();
        }

        @Override
        public boolean shouldHandleMessage() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }

    private static final class SimpleSession implements ActorManagerSession {
        private final SimpleConfig config = new SimpleConfig();
        private final WorkflowCommitState state = InMemoryState.create();

        @Override
        public SessionConfigAccess config() {
            return config;
        }

        @Override
        public WorkflowCommitState state() {
            return state;
        }
    }

    private static final class SimpleConfig implements SessionConfigAccess {
        private final Map<String, Object> env = new LinkedHashMap<>();

        private SimpleConfig() {
            env.put(SessionConstants.STREAM_INPUT_GEN_TIMEOUT_KEY, -1.0d);
        }

        @Override
        public Object getEnv(String key) {
            return env.get(key);
        }
    }
}
