/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.session.config.SessionConfigAccess;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.workflow.NodeSpec;
import com.openjiuwen.core.workflow.WorkflowSpec;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Covers replay of completed producers while resumed consumers are preparing their stream inputs.
 *
 * @since 2026-11-06
 */
class ActorManagerResumeStartupTest {
    private static final String FINISHED_PRODUCER = "BB";
    private static final List<ComponentAbility> CONSUMER_ABILITIES =
            List.of(ComponentAbility.COLLECT, ComponentAbility.TRANSFORM);

    @ParameterizedTest
    @ValueSource(strings = {"active", "Aa"})
    void restoredEndDoesNotWaitForConsumerReadinessOrProducerLock(String activeProducer) throws Exception {
        // Aa and BB share a String hash, so this also covers a collision in the producer lock stripes.
        try (Fixture fixture = new Fixture(activeProducer)) {
            Future<?> send = fixture.executor.submit(() -> {
                fixture.manager.produce(activeProducer, Map.of("value", "first"), ComponentAbility.STREAM, true);
                fixture.manager.produce(activeProducer, Map.of("value", "second"), ComponentAbility.STREAM, false);
                fixture.manager.endMessage(activeProducer, ComponentAbility.STREAM);
            });

            send.get(5, TimeUnit.SECONDS);
            for (ResumedConsumer consumer : fixture.consumers) {
                Map<ComponentAbility, List<Object>> result = consumer.output.get(5, TimeUnit.SECONDS);
                for (ComponentAbility ability : CONSUMER_ABILITIES) {
                    assertThat(result.get(ability)).containsExactly("first", "second");
                }
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final List<ResumedConsumer> consumers;
        private final ActorManager manager;
        private final ThreadPoolExecutor executor;

        private Fixture(String activeProducer) {
            consumers = List.of(new ResumedConsumer("left", activeProducer),
                    new ResumedConsumer("right", activeProducer));
            StreamGraph graph = new StreamGraph();
            for (ResumedConsumer consumer : consumers) {
                graph.addStreamConsumer(consumer, consumer.id);
            }
            ActorManagerSession session = mock(ActorManagerSession.class);
            when(session.state()).thenReturn(InMemoryState.create());
            when(session.config()).thenReturn(mock(SessionConfigAccess.class));
            manager = new ActorManager(spec(activeProducer), graph, false, session);
            manager.markProducerDone(FINISHED_PRODUCER);
            for (ResumedConsumer consumer : consumers) {
                consumer.manager = manager;
            }
            executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                    work -> new Thread(work, "restored-end-producer"));
        }

        private static WorkflowSpec spec(String activeProducer) {
            List<String> targets = List.of("left", "right");
            Map<String, List<String>> edges = Map.of(FINISHED_PRODUCER, targets, activeProducer, targets);
            Map<String, NodeSpec> nodes = Map.of(
                    FINISHED_PRODUCER, new NodeSpec(List.of(ComponentAbility.STREAM, ComponentAbility.TRANSFORM)),
                    activeProducer, new NodeSpec(List.of(ComponentAbility.STREAM)),
                    "left", new NodeSpec(CONSUMER_ABILITIES), "right", new NodeSpec(CONSUMER_ABILITIES));
            return new WorkflowSpec(edges, nodes, Map.of());
        }

        @Override
        public void close() throws InterruptedException {
            executor.shutdownNow();
            try {
                manager.shutdown();
            } finally {
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    private static final class ResumedConsumer implements StreamConsumer {
        private final String id;
        private final String activeProducer;
        private final CompletableFuture<Map<ComponentAbility, List<Object>>> output = new CompletableFuture<>();
        private ActorManager manager;

        private ResumedConsumer(String id, String activeProducer) {
            this.id = id;
            this.activeProducer = activeProducer;
        }

        @Override
        public void streamCall(CountDownLatch ready, Consumer<Exception> errorCallback) {
            Map<ComponentAbility, Map<String, Object>> inputs = new LinkedHashMap<>();
            for (ComponentAbility ability : CONSUMER_ABILITIES) {
                inputs.put(ability, manager.consume(id, ability,
                        Map.of("old", "${" + FINISHED_PRODUCER + ".value}",
                                "live", "${" + activeProducer + ".value}"), null));
            }
            ready.countDown();
            Map<ComponentAbility, List<Object>> collected = new LinkedHashMap<>();
            for (Map.Entry<ComponentAbility, Map<String, Object>> entry : inputs.entrySet()) {
                assertThat(values(entry.getValue().get("old"))).isEmpty();
                collected.put(entry.getKey(), values(entry.getValue().get("live")));
            }
            output.complete(collected);
        }

        private static List<Object> values(Object value) {
            Iterator<?> iterator = assertInstanceOf(Iterator.class, value);
            List<Object> result = new ArrayList<>();
            iterator.forEachRemaining(result::add);
            return result;
        }

        @Override
        public boolean shouldHandleMessage() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }
    }
}
