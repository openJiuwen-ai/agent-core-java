// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable spike for DeepAgent steering (REQ-SPIKE-STEER-001, ChatGPT-style
 * steering during an agent loop). The business gold standard comes from the
 * requirement, not from the current implementation: an external message
 * arriving while the loop runs must not interrupt the current iteration (A1),
 * must be injected into the next iteration's context (A2), must defer loop
 * exit until the loop converges on a final answer (A3), must preserve FIFO
 * order when multiple messages queue up (A4), and must leave the no-steering
 * baseline unchanged (A5). The steer-after-inner-answer-check case additionally
 * pins the race window between the inner answer-branch steering check and the
 * outer loop exit decision. These assertions are the gold standard and must
 * never be weakened to turn the suite green.
 *
 * @since 0.1.7
 */
class DeepAgentSteeringSpikeTest {
    private static final long JOIN_TIMEOUT_MILLIS = 30000L;
    private static final long LATCH_TIMEOUT_SECONDS = 10L;
    private static final double COMPLETION_TIMEOUT_SECONDS = 10.0;
    private static final String STEERING_PREFIX = "[STEERING] ";

    @FunctionalInterface
    private interface ModelResponder {
        AssistantMessage respond(int callIndex, List<BaseMessage> request);
    }

    private static final class InvokeRun {
        private final AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>();
        private final List<Throwable> failures = new CopyOnWriteArrayList<>();
        private final Thread worker;

        private InvokeRun(DeepAgent agent, Map<String, Object> inputs, AgentSessionApi session, String threadName) {
            worker = new Thread(() -> resultRef.set(agent.invoke(inputs, session)), threadName);
            worker.setUncaughtExceptionHandler((thread, error) -> failures.add(error));
        }

        private void start() {
            worker.start();
        }

        private Map<String, Object> joinAndCollect() throws InterruptedException {
            worker.join(JOIN_TIMEOUT_MILLIS);
            assertThat(worker.isAlive()).as("invoke worker must finish").isFalse();
            assertThat(failures).as("invoke worker must not fail").isEmpty();
            return resultRef.get();
        }
    }

    @Test
    void steerDuringModelCallReachesNextRequest() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        DeepAgent agent = newSpikeAgent(4, List.of());
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                modelEntered.countDown();
                awaitRelease(releaseModel);
                return AssistantMessage.builder().content("first answer").build();
            }
            return AssistantMessage.builder().content("done after steer").build();
        });
        AgentSessionApi session = new DeepAgentSession("t1-session", null, agent.getCard());
        InvokeRun run = new InvokeRun(agent, Map.of("query", "initial question", "conversation_id", "t1-session"),
                session, "t1-invoke");
        run.start();

        assertThat(modelEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        agent.steer("focus on tests", session);
        releaseModel.countDown();
        run.joinAndCollect();

        List<List<BaseMessage>> snapshot = List.copyOf(captured);
        assertThat(snapshot).as("loop must issue a second model request after steering")
                .hasSizeGreaterThanOrEqualTo(2);
        assertNoSteering(snapshot.get(0));
        assertSingleSteeringMessage(snapshot.get(1), "focus on tests");
        assertSteeringAfterText(snapshot.get(1), "first answer");
    }

    @Test
    void steerDuringToolExecutionDoesNotInterruptTool() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch toolEntered = new CountDownLatch(1);
        CountDownLatch releaseTool = new CountDownLatch(1);
        AtomicBoolean isToolCompleted = new AtomicBoolean(false);
        DeepAgent agent = newSpikeAgent(3,
                List.of(blockingCompletionTool("blocking_status", toolEntered, releaseTool, isToolCompleted)));
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                return AssistantMessage.builder().content("").toolCalls(List.of(ToolCall.builder()
                        .id("blocking-call").name("blocking_status").arguments("{}").build())).build();
            }
            return AssistantMessage.builder().content("done after tool").build();
        });
        AgentSessionApi session = new DeepAgentSession("t2-session", null, agent.getCard());
        InvokeRun run = new InvokeRun(agent, Map.of("query", "run tool then finish", "conversation_id", "t2-session"),
                session, "t2-invoke");
        run.start();

        assertThat(toolEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        agent.steer("answer briefly", session);
        releaseTool.countDown();
        run.joinAndCollect();

        assertThat(isToolCompleted.get()).as("steering must not interrupt the running tool").isTrue();
        List<List<BaseMessage>> snapshot = List.copyOf(captured);
        assertThat(snapshot).as("loop must issue a second model request after the tool finished")
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(snapshot.get(1)).as("second request must carry the completed tool result")
                .anyMatch(message -> String.valueOf(message.getContent()).contains("tool done"));
        assertSingleSteeringMessage(snapshot.get(1), "answer briefly");
    }

    @Test
    void steeringDefersAnswerExitUntilConverge() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        DeepAgent agent = newSpikeAgent(4, List.of());
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                modelEntered.countDown();
                awaitRelease(releaseModel);
                return AssistantMessage.builder().content("answer before steer").build();
            }
            return AssistantMessage.builder().content("answer after steer").build();
        });
        AgentSessionApi session = new DeepAgentSession("t3-session", null, agent.getCard());
        InvokeRun run = new InvokeRun(agent, Map.of("query", "produce a final answer", "conversation_id", "t3-session"),
                session, "t3-invoke");
        run.start();

        assertThat(modelEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        agent.steer("incorporate the late constraint", session);
        releaseModel.countDown();
        Map<String, Object> result = run.joinAndCollect();

        assertThat(String.valueOf(result.get("output")))
                .as("steering must defer loop exit until convergence on the final answer")
                .isEqualTo("answer after steer");
    }

    @Test
    void fifoOrderMultipleSteering() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        DeepAgent agent = newSpikeAgent(4, List.of());
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                modelEntered.countDown();
                awaitRelease(releaseModel);
                return AssistantMessage.builder().content("first answer").build();
            }
            return AssistantMessage.builder().content("done after steering").build();
        });
        AgentSessionApi session = new DeepAgentSession("t4-session", null, agent.getCard());
        InvokeRun run = new InvokeRun(agent, Map.of("query", "queue two messages", "conversation_id", "t4-session"),
                session, "t4-invoke");
        run.start();

        assertThat(modelEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        agent.steer("first message", session);
        agent.steer("second message", session);
        releaseModel.countDown();
        run.joinAndCollect();

        assertThat(List.copyOf(captured)).as("loop must issue a second model request").hasSizeGreaterThanOrEqualTo(2);
        // The line separator inside the expected payload is the FIFO contract itself:
        // drained steering messages are joined with "\n" into one user message.
        assertSingleSteeringMessage(List.copyOf(captured).get(1), "first message\nsecond message");
    }

    @Test
    void noSteeringBaselineUnchanged() {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        DeepAgent agent = newSpikeAgent(3, List.of());
        installModel(agent, captured, (callIndex, request) ->
                AssistantMessage.builder().content("plain answer").build());
        AgentSessionApi session = new DeepAgentSession("t5-session", null, agent.getCard());

        Map<String, Object> result = agent.invoke(
                Map.of("query", "just answer", "conversation_id", "t5-session"), session);

        assertThat(captured).as("baseline without steering must call the model exactly once").hasSize(1);
        assertThat(String.valueOf(result.get("output"))).isEqualTo("plain answer");
        assertNoSteering(captured.get(0));
    }

    @Test
    void steerAfterRunFeedsNextRunFirstIteration() {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        DeepAgent agent = newSpikeAgent(3, List.of());
        installModel(agent, captured, (callIndex, request) ->
                AssistantMessage.builder().content("answer " + callIndex).build());
        AgentSessionApi session = new DeepAgentSession("t6-session", null, agent.getCard());
        Map<String, Object> inputs = Map.of("query", "run twice", "conversation_id", "t6-session");
        agent.invoke(inputs, session);

        agent.steer("carry this into run two", session);
        Map<String, Object> secondResult = agent.invoke(inputs, session);

        assertThat(List.copyOf(captured)).as("the two runs together must issue two model requests").hasSize(2);
        assertThat(String.valueOf(secondResult.get("output"))).isEqualTo("answer 2");
        assertSingleSteeringMessage(List.copyOf(captured).get(1), "carry this into run two");
    }

    @Test
    void singleArgSteerNeverReachesNamedSessionRun() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        DeepAgent agent = newSpikeAgent(4, List.of());
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                modelEntered.countDown();
                awaitRelease(releaseModel);
                return AssistantMessage.builder().content("first answer").build();
            }
            return AssistantMessage.builder().content("done").build();
        });
        AgentSessionApi session = new DeepAgentSession("t7-session", null, agent.getCard());
        InvokeRun run = new InvokeRun(agent, Map.of("query", "single arg steering", "conversation_id", "t7-session"),
                session, "t7-invoke");
        run.start();

        assertThat(modelEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        agent.steer("late guidance");
        releaseModel.countDown();
        run.joinAndCollect();

        boolean hasSeenSteering = false;
        for (List<BaseMessage> request : List.copyOf(captured)) {
            hasSeenSteering = hasSeenSteering || !steeringMessages(request).isEmpty();
        }
        assertThat(hasSeenSteering)
                .as("single-argument steer must reach the named-session run's model requests")
                .isTrue();
    }

    @Test
    void multiSessionIsolation() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        DeepAgent agent = newSpikeAgent(3, List.of());
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                modelEntered.countDown();
                awaitRelease(releaseModel);
                return AssistantMessage.builder().content("session a first answer").build();
            }
            return AssistantMessage.builder().content("isolated answer").build();
        });
        AgentSessionApi sessionA = new DeepAgentSession("t8-session-a", null, agent.getCard());
        AgentSessionApi sessionB = new DeepAgentSession("t8-session-b", null, agent.getCard());
        InvokeRun runA = new InvokeRun(agent,
                Map.of("query", "resolve session-a-query", "conversation_id", "t8-session-a"), sessionA, "t8-invoke-a");
        InvokeRun runB = new InvokeRun(agent,
                Map.of("query", "resolve session-b-query", "conversation_id", "t8-session-b"), sessionB, "t8-invoke-b");
        runA.start();
        assertThat(modelEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        runB.start();

        agent.steer("guidance only for session a", sessionA);
        releaseModel.countDown();
        runA.joinAndCollect();
        runB.joinAndCollect();

        List<List<BaseMessage>> sessionBRequests = new ArrayList<>();
        for (List<BaseMessage> request : List.copyOf(captured)) {
            if (requestBelongsTo(request, "session-b-query")) {
                sessionBRequests.add(request);
            }
        }
        // Timing-insensitive isolation: whichever order B's exit decision takes relative
        // to A's steer enqueue, B must converge on exactly one model call with no
        // steering leak (A's steer lives in A's bucket, invisible to B's exit check
        // and B's inner steering drain).
        assertThat(sessionBRequests).as("session b must issue its own model requests").hasSize(1);
        for (List<BaseMessage> request : sessionBRequests) {
            assertNoSteering(request);
        }
        List<List<BaseMessage>> sessionARequests = new ArrayList<>();
        for (List<BaseMessage> request : List.copyOf(captured)) {
            if (requestBelongsTo(request, "session-a-query")) {
                sessionARequests.add(request);
            }
        }
        assertThat(sessionARequests).as("session a must continue after its own steering")
                .hasSizeGreaterThanOrEqualTo(2);
        assertSingleSteeringMessage(sessionARequests.get(1), "guidance only for session a");
    }

    @Test
    void steeringDoesNotExceedMaxIterations() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        DeepAgent agent = newSpikeAgent(2, List.of(quickTool("spike_probe")));
        installModel(agent, captured, (callIndex, request) -> {
            if (callIndex == 1) {
                modelEntered.countDown();
                awaitRelease(releaseModel);
            }
            return AssistantMessage.builder().content("").toolCalls(List.of(ToolCall.builder()
                    .id("probe-" + callIndex).name("spike_probe").arguments("{}").build())).build();
        });
        AgentSessionApi session = new DeepAgentSession("t9-session", null, agent.getCard());
        InvokeRun run = new InvokeRun(agent, Map.of("query", "keep probing", "conversation_id", "t9-session"),
                session, "t9-invoke");
        run.start();

        assertThat(modelEntered.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        agent.steer("keep going anyway", session);
        releaseModel.countDown();
        Map<String, Object> result = run.joinAndCollect();

        assertThat(captured).as("steering must not break the maxIterations cap on model calls").hasSize(2);
        String terminationText = String.valueOf(result.get("output")) + " " + String.valueOf(result.get("rounds"));
        assertThat(terminationText).as("termination must carry MAX_ITERATIONS semantics").contains("Max iterations");
    }

    @Test
    void steerAfterInnerAnswerCheckDefersExitUntilConverge() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        DeepAgent agent = newSpikeAgent(4, List.of());
        installModel(agent, captured, (callIndex, request) -> AssistantMessage.builder()
                .content(callIndex == 1 ? "answer before steer" : "answer after steer")
                .build());
        AgentSessionApi session = new DeepAgentSession("t10-session", null, agent.getCard());
        // The rail steers from afterTaskIteration, which CoreTaskLoopEventExecutor fires
        // after the inner invoke returned but before the outer loop's exit decision:
        // exactly the race window where a late steer used to strand in the queue.
        agent.addRail(new LateAnswerWindowSteerRail());
        InvokeRun run = new InvokeRun(agent,
                Map.of("query", "produce a final answer", "conversation_id", "t10-session"),
                session, "t10-invoke");
        run.start();
        Map<String, Object> result = run.joinAndCollect();

        assertThat(String.valueOf(result.get("output")))
                .as("a steer landing in the post-answer-check race window must defer exit until convergence")
                .isEqualTo("answer after steer");
        List<List<BaseMessage>> snapshot = List.copyOf(captured);
        assertThat(snapshot).as("the deferred steer must drive a second model request")
                .hasSizeGreaterThanOrEqualTo(2);
        assertSingleSteeringMessage(snapshot.get(1), "late constraint");
        assertThat(result.get("rounds")).as("outer loop result must carry a rounds list")
                .isInstanceOf(List.class);
        assertThat((List<?>) result.get("rounds"))
                .as("the steering-deferred continuation must run at least two outer rounds")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * Steers exactly once from the afterTaskIteration hook: round 1 only, guarded by
     * a flag because the hook fires for every outer round.
     */
    private static final class LateAnswerWindowSteerRail extends DeepAgentRail {
        private final AtomicBoolean steered = new AtomicBoolean(false);

        @Override
        public void afterTaskIteration(CallbackContext ctx) {
            if (steered.get() || !(ctx.get("round") instanceof Number round) || round.intValue() != 1) {
                return;
            }
            if (!(ctx.get("session") instanceof AgentSessionApi session) || ctx.getAgent() == null) {
                return;
            }
            if (steered.compareAndSet(false, true)) {
                ctx.getAgent().steer("late constraint", session);
            }
        }
    }

    @Test
    void crossSessionSteerInExitWindowIsolatedAndPreserved() throws Exception {
        List<List<BaseMessage>> captured = Collections.synchronizedList(new ArrayList<>());
        DeepAgent agent = newSpikeAgent(4, List.of());
        // Model answers per session marker, so requests are attributable without a
        // shared call counter (sessions run sequentially here).
        installModel(agent, captured, (callIndex, request) -> AssistantMessage.builder()
                .content(requestBelongsTo(request, "t11-b-query") ? "session b answer" : "session a answer")
                .build());
        AgentSessionApi sessionA = new DeepAgentSession("t11-session-a", null, agent.getCard());
        AgentSessionApi sessionB = new DeepAgentSession("t11-session-b", null, agent.getCard());
        // The rail steers for session A (which is NOT running) from inside session B's
        // afterTaskIteration hook: exactly the exit-decision race window the review
        // flagged, where a global steering check used to extend B's loop and let B's
        // next round drain — and leak — A's message.
        agent.addRail(new CrossSessionSteerRail(sessionA, "cross session guidance"));
        InvokeRun runB = new InvokeRun(agent,
                Map.of("query", "resolve t11-b-query", "conversation_id", "t11-session-b"),
                sessionB, "t11-invoke-b");
        runB.start();
        Map<String, Object> resultB = runB.joinAndCollect();

        // B: one model call, no steering leak, no extra outer round from A's steer.
        assertThat(requestsFor(captured, "t11-b-query"))
                .as("session b must converge on exactly one model call despite a cross-session steer")
                .hasSize(1);
        assertNoSteering(requestsFor(captured, "t11-b-query").get(0));
        assertThat(String.valueOf(resultB.get("output")))
                .as("session b must return its own converged answer")
                .isEqualTo("session b answer");

        // A: the steered message is preserved in A's own bucket and feeds A's next run
        // instead of being lost (drained by B) or stranded.
        Map<String, Object> resultA = agent.invoke(
                Map.of("query", "resolve t11-a-query", "conversation_id", "t11-session-a"), sessionA);
        List<List<BaseMessage>> aRequests = requestsFor(captured, "t11-a-query");
        assertThat(aRequests).as("session a must issue its own model request").isNotEmpty();
        assertSingleSteeringMessage(aRequests.get(0), "cross session guidance");
        assertThat(String.valueOf(resultA.get("output")))
                .as("session a must converge on its own answer after consuming its steering")
                .isEqualTo("session a answer");
    }

    /**
     * Steers for a fixed target session (not the firing session) exactly once, from
     * the afterTaskIteration hook of whichever round fires first.
     */
    private static final class CrossSessionSteerRail extends DeepAgentRail {
        private final AtomicBoolean steered = new AtomicBoolean(false);
        private final AgentSessionApi targetSession;
        private final String message;

        CrossSessionSteerRail(AgentSessionApi targetSession, String message) {
            this.targetSession = targetSession;
            this.message = message;
        }

        @Override
        public void afterTaskIteration(CallbackContext ctx) {
            if (ctx.getAgent() == null) {
                return;
            }
            if (steered.compareAndSet(false, true)) {
                ctx.getAgent().steer(message, targetSession);
            }
        }
    }

    private static List<List<BaseMessage>> requestsFor(List<List<BaseMessage>> captured, String marker) {
        List<List<BaseMessage>> matches = new ArrayList<>();
        for (List<BaseMessage> request : List.copyOf(captured)) {
            if (requestBelongsTo(request, marker)) {
                matches.add(request);
            }
        }
        return matches;
    }

    private static DeepAgent newSpikeAgent(int maxIterations, List<Object> tools) {
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath("./repo").enableTaskLoop(true).maxIterations(maxIterations)
                .completionTimeout(COMPLETION_TIMEOUT_SECONDS).tools(tools).build());
        agent.ensureInitialized();
        return agent;
    }

    @SuppressWarnings("unchecked")
    private static Model installModel(DeepAgent agent, List<List<BaseMessage>> captured, ModelResponder responder) {
        Model model = mock(Model.class);
        AtomicInteger callCounter = new AtomicInteger();
        // develop's ReActAgent calls the async CompletionStage overload; the legacy
        // 10-argument synchronous overload is stubbed as a defensive fallback so the
        // fixture keeps capturing whichever entry point the runtime selects.
        when(model.invoke(any(List.class), any(ModelInvokeOptions.class)))
                .thenAnswer(invocation -> {
                    int callIndex = callCounter.incrementAndGet();
                    List<BaseMessage> request = List.copyOf((List<BaseMessage>) invocation.getArgument(0));
                    captured.add(request);
                    return CompletableFuture.completedFuture(responder.respond(callIndex, request));
                });
        try {
            when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        int callIndex = callCounter.incrementAndGet();
                        List<BaseMessage> request = List.copyOf((List<BaseMessage>) invocation.getArgument(0));
                        captured.add(request);
                        return responder.respond(callIndex, request);
                    });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stub model invoke", ex);
        }
        agent.getAgent().setLlm(model);
        return model;
    }

    private static Tool blockingCompletionTool(String name, CountDownLatch entered, CountDownLatch release,
            AtomicBoolean isCompleted) {
        return new Tool(ToolCard.builder().id(name).name(name).description("blocking spike tool")
                .inputParams(Map.of("type", "object", "properties", Map.of())).build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                entered.countDown();
                awaitRelease(release);
                isCompleted.set(true);
                return "tool done";
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of().iterator();
            }
        };
    }

    private static Tool quickTool(String name) {
        return new Tool(ToolCard.builder().id(name).name(name).description("non-blocking spike tool")
                .inputParams(Map.of("type", "object", "properties", Map.of())).build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return "probe done";
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of().iterator();
            }
        };
    }

    private static void awaitRelease(CountDownLatch release) {
        boolean isReleased = false;
        try {
            isReleased = release.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            // Restore the interrupted status so the owning thread can cooperate with shutdown.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting release latch", ex);
        }
        if (!isReleased) {
            throw new IllegalStateException("Release latch timed out");
        }
    }

    private static List<BaseMessage> steeringMessages(List<BaseMessage> request) {
        List<BaseMessage> found = new ArrayList<>();
        for (BaseMessage message : request) {
            if (String.valueOf(message.getContent()).contains(STEERING_PREFIX)) {
                found.add(message);
            }
        }
        return found;
    }

    private static void assertSingleSteeringMessage(List<BaseMessage> request, String expectedPayload) {
        List<BaseMessage> found = steeringMessages(request);
        assertThat(found).as("request must contain exactly one steering message").hasSize(1);
        assertThat(String.valueOf(found.get(0).getContent()))
                .as("steering message content must match the FIFO-joined payload")
                .isEqualTo(STEERING_PREFIX + expectedPayload);
    }

    private static void assertNoSteering(List<BaseMessage> request) {
        assertThat(steeringMessages(request)).as("request must not contain steering").isEmpty();
    }

    private static void assertSteeringAfterText(List<BaseMessage> request, String anchorText) {
        int anchorIndex = indexContaining(request, anchorText);
        int steeringIndex = indexContaining(request, STEERING_PREFIX);
        assertThat(anchorIndex).as("request must contain the anchor message: " + anchorText)
                .isGreaterThanOrEqualTo(0);
        assertThat(steeringIndex).as("steering must be injected after the previous iteration's message")
                .isGreaterThan(anchorIndex);
    }

    private static boolean requestBelongsTo(List<BaseMessage> request, String marker) {
        return indexContaining(request, marker) >= 0;
    }

    private static int indexContaining(List<BaseMessage> request, String expectedText) {
        for (int index = 0; index < request.size(); index++) {
            if (String.valueOf(request.get(index).getContent()).contains(expectedText)) {
                return index;
            }
        }
        return -1;
    }
}
