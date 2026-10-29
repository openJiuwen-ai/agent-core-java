/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.ResumeContext;
import com.openjiuwen.core.singleagent.interrupt.ToolCallInterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptHandler;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression tests for interrupted TaskTool execution and same-child resume.
 */
class TaskToolInterruptResumeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskToolInterruptResumeTest.class);
    private static final String SUBAGENT_TYPE = "verification_agent";
    private static final String TASK_DESCRIPTION = "inspect child";
    private static final String INNER_INTERRUPT_ID = "inner-1";

    @Test
    void taskToolInterruptResumesSameChildThroughParentHandler() {
        OutputSchema childState = childInterruptState();
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(childInterruptResult(childState), Map.of("output", "resumed"),
                        Map.of("output", "next")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);
        ToolCall toolCall = taskToolCall();
        ToolInterruptHandler handler = new ToolInterruptHandler(parent);

        Object firstResult = invokeParsedTool(tool, toolCall, session);
        Map<?, ?> firstInterrupt = assertInstanceOf(Map.class, firstResult);
        assertThat(firstInterrupt.get("output")).isEqualTo("waiting");
        assertThat(firstInterrupt.get("agent_id")).isEqualTo("child-agent");
        assertThat(firstInterrupt.get("result_type")).isEqualTo("interrupt");
        assertThat(firstInterrupt.get("state")).isEqualTo(List.of(childState));
        assertThat(firstInterrupt.get("interrupt_ids")).isEqualTo(List.of(INNER_INTERRUPT_ID));
        assertLifecycleCounts(session, 1, 0, 0);

        ToolInterruptHandler.InterruptStateResult interruption = handler.buildInterruptState(
                List.of(firstResult), List.of(toolCall), new AssistantMessage("start"), 0, "start");
        ToolInterruptionState state = interruption.getState();
        Map<String, Object> parentInterrupt = handler.commitInterrupt(
                state, session, null, interruption.getPayloads());
        assertThat(parentInterrupt.get("interrupt_ids")).isEqualTo(List.of(INNER_INTERRUPT_ID));
        assertThat(parentInterrupt.get("state")).isEqualTo(List.of(childState));
        assertThat(state.getInterruptedTools().get("outer-1").isSubAgent()).isTrue();

        InteractiveInput reply = reply("approved");
        ResumeContext resume = resumeContext(state, session, reply, tool);
        assertThat(handler.handleResume(resume)).isNull();
        assertResumedSameChild(parent, child, session, reply);
        assertLifecycleCounts(session, 1, 1, 0);

        Object nextResult = invokeParsedTool(tool, toolCall, session);
        ToolOutput nextOutput = assertInstanceOf(ToolOutput.class, nextResult);
        assertThat(nextOutput.isSuccess()).isTrue();
        assertThat(parent.createAttempts).isEqualTo(2);
        assertLifecycleCounts(session, 2, 2, 0);
    }

    @Test
    void duplicateTaskIsRejectedWhileInterruptedTaskIsPending() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(childInterruptResult(childInterruptState()),
                        childInterruptResult(childInterruptState())));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);

        tool.invoke(taskInputs(), Map.of("session", session));

        assertThatThrownBy(() -> tool.invoke(taskInputs(), Map.of("session", session)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already pending");
        assertThat(parent.createAttempts).isEqualTo(1);
        assertThat(child.invocations).hasSize(1);
        assertLifecycleCounts(session, 1, 0, 0);
    }

    @Test
    void failedSetupDoesNotBlockRetryWithSameTaskKey() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent", List.<Object>of(Map.of("output", "done")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        parent.failNextCreation = true;
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);

        assertThatThrownBy(() -> tool.invoke(taskInputs(), Map.of("session", session)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("creation failed");

        ToolOutput retryOutput = assertInstanceOf(ToolOutput.class,
                tool.invoke(taskInputs(), Map.of("session", session)));
        assertThat(retryOutput.isSuccess()).isTrue();
        assertThat(parent.createAttempts).isEqualTo(2);
        assertLifecycleCounts(session, 1, 1, 0);
    }

    @Test
    void failedPrepareCleansUpAndAllowsNewTask() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent", List.<Object>of(Map.of("output", "done")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        session.failNextPrepare = true;
        Tool tool = taskTool(parent);

        assertThatThrownBy(() -> tool.invoke(taskInputs(), Map.of("session", session)))
                .isInstanceOf(CompletionException.class)
                .hasMessageContaining("prepare failed");
        assertThat(parent.createAttempts).isEqualTo(1);
        assertLifecycleCounts(session, 1, 0, 1);

        ToolOutput retryOutput = assertInstanceOf(ToolOutput.class,
                tool.invoke(taskInputs(), Map.of("session", session)));
        assertThat(retryOutput.isSuccess()).isTrue();
        assertThat(parent.createAttempts).isEqualTo(2);
        assertLifecycleCounts(session, 2, 1, 1);
    }

    @Test
    void failedResumeCleansUpAndAllowsNewTask() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(childInterruptResult(childInterruptState()), new IllegalStateException("boom"),
                        Map.of("output", "next")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);
        tool.invoke(taskInputs(), Map.of("session", session));
        Map<String, Object> resumeInputs = new LinkedHashMap<>(taskInputs());
        InteractiveInput reply = reply("approved");
        resumeInputs.put("query", reply);

        assertThatThrownBy(() -> tool.invoke(resumeInputs, Map.of("session", session)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("execution failed");
        assertThat(child.invocations.get(1).get("query")).isSameAs(reply);
        assertThat(parent.createAttempts).isEqualTo(1);
        assertLifecycleCounts(session, 1, 0, 1);

        ToolOutput nextOutput = assertInstanceOf(ToolOutput.class,
                tool.invoke(taskInputs(), Map.of("session", session)));
        assertThat(nextOutput.isSuccess()).isTrue();
        assertThat(parent.createAttempts).isEqualTo(2);
        assertLifecycleCounts(session, 2, 1, 1);
    }

    @Test
    void rawInteractiveInputSurvivesParentJsonResume() {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(childInterruptResult(childInterruptState()), Map.of("output", "done")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);
        ToolCall toolCall = taskToolCall();
        ToolInterruptHandler handler = new ToolInterruptHandler(parent);
        Object firstResult = invokeParsedTool(tool, toolCall, session);
        ToolInterruptHandler.InterruptStateResult interruption = handler.buildInterruptState(
                List.of(firstResult), List.of(toolCall), new AssistantMessage("start"), 0, "start");
        InteractiveInput rawReply = new InteractiveInput("approved");

        ResumeContext resume = resumeContext(interruption.getState(), session, rawReply, tool);
        assertThat(handler.handleResume(resume)).isNull();

        InteractiveInput childReply = assertInstanceOf(InteractiveInput.class,
                child.invocations.get(1).get("query"));
        assertThat(childReply.getRawInputs()).isEqualTo("approved");
        assertThat(childReply.getUserInputs()).isEmpty();
        assertThat(parent.createAttempts).isEqualTo(1);
    }

    @Test
    void differentDescriptionsRetainIndependentPendingTasks() throws Exception {
        ScriptedChildAgent firstChild = new ScriptedChildAgent("first-child",
                List.<Object>of(childInterruptResult(childInterruptState()), Map.of("output", "first done")));
        ScriptedChildAgent secondChild = new ScriptedChildAgent("second-child",
                List.<Object>of(childInterruptResult(childInterruptState()), Map.of("output", "second done")));
        ScriptedParentAgent parent = new ScriptedParentAgent(List.of(firstChild, secondChild));
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);
        tool.invoke(taskInputs("first task"), Map.of("session", session));
        tool.invoke(taskInputs("second task"), Map.of("session", session));

        tool.invoke(resumeInputs("first task", "first answer"), Map.of("session", session));
        assertThat(firstChild.invocations).hasSize(2);
        assertThat(secondChild.invocations).hasSize(1);
        tool.invoke(resumeInputs("second task", "second answer"), Map.of("session", session));

        assertThat(secondChild.invocations).hasSize(2);
        assertThat(firstChild.invocations.get(1).get("query")).isInstanceOf(InteractiveInput.class);
        assertThat(secondChild.invocations.get(1).get("query")).isInstanceOf(InteractiveInput.class);
        assertThat(parent.createAttempts).isEqualTo(2);
        assertLifecycleCounts(session, 2, 2, 0);
    }

    @Test
    void repeatedInterruptsReuseChildUntilFinalCompletion() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(childInterruptResult(childInterruptState()),
                        childInterruptResult(childInterruptState()), Map.of("output", "done")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);
        tool.invoke(taskInputs(), Map.of("session", session));

        Object secondInterrupt = tool.invoke(resumeInputs(TASK_DESCRIPTION, "first answer"),
                Map.of("session", session));
        assertThat(secondInterrupt).isInstanceOf(Map.class);
        assertLifecycleCounts(session, 1, 0, 0);
        ToolOutput completed = assertInstanceOf(ToolOutput.class,
                tool.invoke(resumeInputs(TASK_DESCRIPTION, "second answer"), Map.of("session", session)));

        assertThat(completed.isSuccess()).isTrue();
        assertThat(parent.createAttempts).isEqualTo(1);
        assertThat(child.invocations).hasSize(3);
        assertThat(child.sessions).containsExactly(session, session, session);
        assertThat(child.invocations.get(1).get("conversation_id"))
                .isEqualTo(child.invocations.get(2).get("conversation_id"));
        assertLifecycleCounts(session, 1, 1, 0);
    }

    @Test
    void concurrentResumeAllowsOnlyOneChildInvocation() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(childInterruptResult(childInterruptState()), Map.of("output", "done")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        Tool tool = taskTool(parent);
        tool.invoke(taskInputs(), Map.of("session", session));
        ThreadPoolExecutor executor = newExecutor(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes;
        try {
            Future<Object> first = executor.submit(() -> invokeAfterStart(tool, session, ready, start));
            Future<Object> second = executor.submit(() -> invokeAfterStart(tool, session, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            shutdownExecutor(executor);
        }

        assertThat(outcomes).anyMatch(ToolOutput.class::isInstance);
        assertThat(outcomes).anyMatch(IllegalStateException.class::isInstance);
        assertThat(child.invocations).hasSize(2);
        assertThat(parent.createAttempts).isEqualTo(1);
        assertLifecycleCounts(session, 1, 1, 0);
    }

    @Test
    void duplicateTaskWaitsForTerminalCleanup() throws Exception {
        ScriptedChildAgent child = new ScriptedChildAgent("child-agent",
                List.<Object>of(Map.of("output", "done"), Map.of("output", "next")));
        ScriptedParentAgent parent = new ScriptedParentAgent(child);
        TrackingSession session = new TrackingSession("parent-session");
        session.blockNextSuspend();
        Tool tool = taskTool(parent);
        ThreadPoolExecutor executor = newExecutor(1);
        Future<Object> completion = executor.submit(() -> tool.invoke(taskInputs(), Map.of("session", session)));
        try {
            assertThat(session.suspendStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> tool.invoke(taskInputs(), Map.of("session", session)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already pending");
            assertThat(parent.createAttempts).isEqualTo(1);
        } finally {
            session.suspendGate.complete(true);
            shutdownExecutor(executor);
        }

        assertThat(completion.get(5, TimeUnit.SECONDS)).isInstanceOf(ToolOutput.class);
        ToolOutput retry = assertInstanceOf(ToolOutput.class,
                tool.invoke(taskInputs(), Map.of("session", session)));
        assertThat(retry.isSuccess()).isTrue();
        assertThat(parent.createAttempts).isEqualTo(2);
    }

    private static Tool taskTool(DeepAgent parent) {
        return TaskTool.createTaskTool(parent, SUBAGENT_TYPE, "cn").get(0);
    }

    private static ToolCall taskToolCall() {
        return ToolCall.builder()
                .id("outer-1")
                .type("function")
                .name("task_tool")
                .arguments("{\"subagent_type\":\"verification_agent\",\"task_description\":\"inspect child\"}")
                .build();
    }

    private static Map<String, Object> taskInputs() {
        return taskInputs(TASK_DESCRIPTION);
    }

    private static Map<String, Object> taskInputs(String taskDescription) {
        return Map.of("subagent_type", SUBAGENT_TYPE, "task_description", taskDescription);
    }

    private static Map<String, Object> resumeInputs(String taskDescription, String answer) {
        Map<String, Object> inputs = new LinkedHashMap<>(taskInputs(taskDescription));
        inputs.put("query", reply(answer));
        return inputs;
    }

    private static Object invokeAfterStart(Tool tool, TrackingSession session, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Concurrent resume did not start");
        }
        try {
            return tool.invoke(resumeInputs(TASK_DESCRIPTION, "approved"), Map.of("session", session));
        } catch (IllegalStateException exception) {
            return exception;
        }
    }

    private static ThreadPoolExecutor newExecutor(int threadCount) {
        AtomicInteger threadIds = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("task-tool-test-" + threadIds.incrementAndGet());
            thread.setUncaughtExceptionHandler((current, failure) ->
                    LOGGER.error("Uncaught test worker failure", failure));
            return thread;
        };
        return new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(4), threadFactory);
    }

    private static void shutdownExecutor(ThreadPoolExecutor executor) throws InterruptedException {
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static Object invokeParsedTool(Tool tool, ToolCall toolCall, AgentSessionApi session) {
        Object parsed = AbilityManager.parseToolArguments(toolCall.getArguments());
        if (!(parsed instanceof Map<?, ?> parsedMap)) {
            throw new AssertionError("Tool arguments should parse to a map");
        }
        Map<String, Object> inputs = copyStringMap(parsedMap);
        return assertDoesNotThrow(() -> tool.invoke(inputs, Map.of("session", session)));
    }

    private static ResumeContext resumeContext(ToolInterruptionState state, TrackingSession session,
            InteractiveInput reply, Tool tool) {
        ResumeContext resume = new ResumeContext();
        resume.setState(state);
        resume.setSession(session);
        resume.setUserInput(reply);
        resume.setExecuteToolCall((context, calls, resumeSession, modelContext) -> {
            assertThat(calls).hasSize(1);
            assertThat(calls.get(0).getArguments())
                    .contains(SUBAGENT_TYPE, TASK_DESCRIPTION, "userInputs");
            return List.of(invokeParsedTool(tool, calls.get(0), resumeSession));
        });
        return resume;
    }

    private static void assertResumedSameChild(ScriptedParentAgent parent, ScriptedChildAgent child,
            TrackingSession session, InteractiveInput reply) {
        assertThat(parent.createAttempts).isEqualTo(1);
        assertThat(parent.createdSessionIds).containsExactly("parent-session_sub_verification_agent");
        assertThat(child.invocations).hasSize(2);
        assertThat(child.sessions).containsExactly(session, session);
        Map<String, Object> resumedInputs = child.invocations.get(1);
        InteractiveInput resumedReply = assertInstanceOf(InteractiveInput.class, resumedInputs.get("query"));
        assertThat(resumedReply.getUserInputs()).isEqualTo(reply.getUserInputs());
        assertThat(resumedInputs.get("conversation_id"))
                .isEqualTo(child.invocations.get(0).get("conversation_id"));
    }

    private static InteractiveInput reply(String value) {
        InteractiveInput reply = new InteractiveInput();
        reply.update(INNER_INTERRUPT_ID, value);
        return reply;
    }

    private static OutputSchema childInterruptState() {
        ToolCallInterruptRequest request = new ToolCallInterruptRequest();
        request.setMessage("Approve child action");
        return new OutputSchema(InterruptConstants.INTERACTION, 0,
                new InteractionOutput(INNER_INTERRUPT_ID, request));
    }

    private static Map<String, Object> childInterruptResult(OutputSchema state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("output", "waiting");
        result.put("result_type", "interrupt");
        result.put("state", List.of(state));
        result.put("interrupt_ids", List.of(INNER_INTERRUPT_ID));
        return result;
    }

    private static void assertLifecycleCounts(TrackingSession session, int prepareCount, int suspendCount,
            int releaseCount) {
        assertThat(session.prepareCount).isEqualTo(prepareCount);
        assertThat(session.suspendCount).isEqualTo(suspendCount);
        assertThat(session.releaseCount).isEqualTo(releaseCount);
    }

    private static Map<String, Object> copyStringMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static final class ScriptedParentAgent extends DeepAgent {
        private final Queue<ScriptedChildAgent> children;
        private final List<String> createdSessionIds = new ArrayList<>();
        private int createAttempts;
        private boolean failNextCreation;

        private ScriptedParentAgent(ScriptedChildAgent child) {
            this(List.of(child));
        }

        private ScriptedParentAgent(List<ScriptedChildAgent> children) {
            super(new AgentCard("parent", "parent", "test"));
            this.children = new ArrayDeque<>(children);
        }

        @Override
        public DeepAgent createSubagent(String subagentType, String subsessionId) {
            createAttempts++;
            if (failNextCreation) {
                failNextCreation = false;
                throw new IllegalArgumentException("setup failed");
            }
            createdSessionIds.add(subsessionId);
            if (children.size() > 1) {
                return children.remove();
            }
            return children.peek();
        }
    }

    private static final class ScriptedChildAgent extends DeepAgent {
        private final Queue<Object> outcomes;
        private final List<Map<String, Object>> invocations = new ArrayList<>();
        private final List<AgentSessionApi> sessions = new ArrayList<>();

        private ScriptedChildAgent(String id, List<Object> outcomes) {
            super(new AgentCard(id, id, "test"));
            this.outcomes = new ArrayDeque<>(outcomes);
        }

        @Override
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            invocations.add(new LinkedHashMap<>(inputs));
            sessions.add(session);
            Object outcome = outcomes.remove();
            if (outcome instanceof IllegalStateException exception) {
                throw exception;
            }
            if (outcome instanceof Map<?, ?> result) {
                return copyStringMap(result);
            }
            throw new AssertionError("Unexpected scripted child outcome");
        }
    }

    private static final class TrackingSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private int prepareCount;
        private int suspendCount;
        private int releaseCount;
        private boolean failNextPrepare;
        private boolean shouldBlockSuspend;
        private CountDownLatch suspendStarted;
        private CompletableFuture<Boolean> suspendGate;

        private TrackingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        private void blockNextSuspend() {
            shouldBlockSuspend = true;
            suspendStarted = new CountDownLatch(1);
            suspendGate = new CompletableFuture<>();
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
            state.put("lastStream", data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }

        @Override
        public CompletableFuture<Boolean> prepareKvc() {
            prepareCount++;
            if (failNextPrepare) {
                failNextPrepare = false;
                return CompletableFuture.failedFuture(new IllegalStateException("prepare failed"));
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> suspendKvc() {
            suspendCount++;
            if (shouldBlockSuspend) {
                shouldBlockSuspend = false;
                suspendStarted.countDown();
                return suspendGate;
            }
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> releaseKvc() {
            releaseCount++;
            return CompletableFuture.completedFuture(true);
        }
    }
}
