/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deepagent;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streaming resume regression: a resume input on stream() must run in the background and
 * forward inner chunks incrementally, and a re-interrupt must surface exactly once.
 */
class DeepAgentStreamingResumeTest {
    private static final AgentCard CARD = new AgentCard("resume-stream", "resume-stream", "streaming resume");

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void streamedResumeDeliversFirstChunkBeforeInnerRoundCompletes() {
        // Given an inner agent that emits a first chunk and then blocks until released
        ScriptedReactAgent inner = new ScriptedReactAgent();
        agent = newAgent(inner);
        CountDownLatch release = new CountDownLatch(1);
        inner.gateAfterFirstChunk(release);

        // When a resume invocation streams
        Iterator<Object> chunks = agent.stream(Map.of(
                "query", resumeQuery(),
                "conversation_id", UUID.randomUUID().toString()), null, List.of(StreamMode.OUTPUT));

        // Then the first inner chunk arrives while the inner round is still blocked
        assertThat(chunks.hasNext()).isTrue();
        Object first = chunks.next();
        assertThat(first).isInstanceOf(OutputSchema.class);
        assertThat(((OutputSchema) first).getType()).isEqualTo("tool_call");
        assertThat(inner.isRoundDone()).isFalse();
        assertThat(inner.isStreamUsed()).isTrue();
        assertThat(inner.isInvokeUsed()).isFalse();

        // And after release the remaining chunks drain, ending with two answer chunks
        // (inner answer plus the DeepAgent top-level answer)
        release.countDown();
        List<Object> all = new ArrayList<>();
        all.add(first);
        chunks.forEachRemaining(all::add);
        long answers = all.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(chunk -> "answer".equals(chunk.getType()))
                .count();
        assertThat(answers).isEqualTo(2);
        assertThat(all.stream().filter(OutputSchema.class::isInstance).map(OutputSchema.class::cast)
                .anyMatch(chunk -> "tool_output".equals(chunk.getType()))).isTrue();
    }

    @Test
    void streamedResumeReInterruptSurfacesInteractionChunkExactlyOnce() {
        // Given an inner agent whose resumed round interrupts again (tool needs confirmation)
        ScriptedReactAgent inner = new ScriptedReactAgent();
        agent = newAgent(inner);
        inner.interruptOnStream();

        // When the resume invocation streams
        List<Object> all = new ArrayList<>();
        agent.stream(Map.of(
                        "query", resumeQuery(),
                        "conversation_id", UUID.randomUUID().toString()), null, List.of(StreamMode.OUTPUT))
                .forEachRemaining(all::add);

        // Then the interaction chunk appears exactly once and no bogus tool-payload answer follows
        long interactions = all.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(chunk -> InterruptConstants.INTERACTION.equals(chunk.getType()))
                .count();
        assertThat(interactions).isEqualTo(1);
        assertThat(all.stream().filter(OutputSchema.class::isInstance).map(OutputSchema.class::cast)
                .anyMatch(chunk -> "answer".equals(chunk.getType()))).isFalse();
    }

    private static DeepAgent newAgent(ReActAgent inner) {
        DeepAgent configuredAgent = new DeepAgent(CARD);
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        configuredAgent.configure(config);
        configuredAgent.setReactAgent(inner, true);
        return configuredAgent;
    }

    private static InteractiveInput resumeQuery() {
        InteractiveInput query = new InteractiveInput();
        query.update("write-call-1", Map.of("approved", true));
        return query;
    }

    private static final class ScriptedReactAgent extends ReActAgent {
        private final AtomicBoolean streamUsed = new AtomicBoolean();
        private final AtomicBoolean invokeUsed = new AtomicBoolean();
        private final AtomicBoolean roundDone = new AtomicBoolean();
        private volatile CountDownLatch gate;
        private volatile boolean isInterrupting;

        ScriptedReactAgent() {
            super(new AgentCard("inner-scripted", "inner-scripted", "scripted inner agent"));
        }

        void gateAfterFirstChunk(CountDownLatch release) {
            this.gate = release;
        }

        void interruptOnStream() {
            this.isInterrupting = true;
        }

        boolean isStreamUsed() {
            return streamUsed.get();
        }

        boolean isInvokeUsed() {
            return invokeUsed.get();
        }

        boolean isRoundDone() {
            return roundDone.get();
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            invokeUsed.set(true);
            return Map.of("output", "sync-answer", "result_type", "answer");
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            streamUsed.set(true);
            if (isInterrupting) {
                return List.<Object>of(new OutputSchema(InterruptConstants.INTERACTION, 0, Map.of(
                        "tool_name", "write",
                        "message", "Please approve or reject?"))).iterator();
            }
            List<OutputSchema> tail = List.of(
                    new OutputSchema("tool_output", 1, Map.of("content", "file written")),
                    new OutputSchema("answer", 2, Map.of("output", "done", "result_type", "answer")));
            return new GatedIterator(new OutputSchema("tool_call", 0, Map.of("name", "write")), tail, this);
        }

        private void awaitGate() {
            CountDownLatch latch = gate;
            if (latch == null) {
                return;
            }
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("test gate was not released in time");
                }
            } catch (InterruptedException ex) {
                throw new AssertionError("test gate interrupted", ex);
            }
            roundDone.set(true);
        }
    }

    private static final class GatedIterator implements Iterator<Object> {
        private final OutputSchema first;
        private final Iterator<OutputSchema> tail;
        private final ScriptedReactAgent owner;
        private boolean isFirstServed;

        GatedIterator(OutputSchema first, List<OutputSchema> tail, ScriptedReactAgent owner) {
            this.first = first;
            this.tail = tail.iterator();
            this.owner = owner;
        }

        @Override
        public boolean hasNext() {
            return !isFirstServed || tail.hasNext();
        }

        @Override
        public Object next() {
            if (!isFirstServed) {
                isFirstServed = true;
                return first;
            }
            owner.awaitGate();
            return tail.next();
        }
    }
}
