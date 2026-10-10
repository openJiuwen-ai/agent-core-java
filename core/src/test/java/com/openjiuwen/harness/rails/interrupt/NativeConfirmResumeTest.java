/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ResumeContext;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptHandler;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests the native rail through the actual callback and tool execution pipeline. */
class NativeConfirmResumeTest {
    private static final String AGENT_ID = "native-confirm-resume";
    private static final String TOOL_ID = "native-confirm-tool";

    private final AtomicInteger executions = new AtomicInteger();
    private final ReActAgent agent = new ReActAgent(new AgentCard(AGENT_ID, AGENT_ID, "test"));

    @AfterEach
    void cleanup() {
        agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        agent.getAgentCallbackManager().clear(AgentCallbackEvent.BEFORE_TOOL_CALL).toCompletableFuture().join();
        Runner.resourceMgr().removeTool(TOOL_ID, AGENT_ID, TagMatchStrategy.ALL, true);
    }

    @Test
    void waitsForApprovalThenExecutesExactlyOnce() {
        AgentCallbackContext context = prepare();
        AbilityManager.ExecutionResult first = execute(context);

        ToolInterruptException interruption = assertInstanceOf(ToolInterruptException.class, first.result());
        assertEquals("Please approve or reject?", interruption.getRequest().getMessage());
        assertEquals(0, executions.get());

        InteractiveInput approval = new InteractiveInput();
        approval.update("call-1", Map.of("approved", true));
        context.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, approval);
        AbilityManager.ExecutionResult resumed = execute(context);

        assertEquals("executed", resumed.result());
        assertEquals(1, executions.get());
    }

    @Test
    void rejectionSkipsExecutionAndPreservesFeedback() {
        AgentCallbackContext context = prepare();
        assertInstanceOf(ToolInterruptException.class, execute(context).result());
        InteractiveInput rejection = new InteractiveInput();
        rejection.update("call-1", Map.of("approved", false, "feedback", "do not execute"));
        context.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, rejection);

        AbilityManager.ExecutionResult result = execute(context);

        assertEquals("do not execute", result.result());
        assertEquals(0, executions.get());
    }

    @Test
    void toolInterruptionRaisedByCallbackIsNotSwallowed() {
        AgentCallbackContext context = prepare();
        ToolInterruptException expected = new ToolInterruptException(
                new InterruptRequest("wait", Map.of(), ""));
        agent.registerCallback(AgentCallbackEvent.BEFORE_TOOL_CALL,
                ignored -> {
                    throw expected;
                }, 200).toCompletableFuture().join();

        assertSame(expected, execute(context).result());
        assertEquals(0, executions.get());
    }

    @Test
    void instanceRailAlsoInterruptsBeforeExecutingTheTool() {
        AgentCallbackContext context = prepare();
        agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        agent.registerInstanceRail(new ConfirmInterruptRail(List.of("confirmed_tool"))).toCompletableFuture().join();

        assertInstanceOf(ToolInterruptException.class, execute(context).result());
        assertEquals(0, executions.get());
    }

    @Test
    void asynchronousInterruptionIsNotSwallowed() {
        AgentCallbackContext context = prepare();
        ToolInterruptException expected = new ToolInterruptException(new InterruptRequest("async wait", Map.of(), ""));
        agent.registerCallback(AgentCallbackEvent.BEFORE_TOOL_CALL,
                ignored -> CompletableFuture.failedFuture(expected), 200).toCompletableFuture().join();

        assertSame(expected, execute(context).result());
        assertEquals(0, executions.get());
    }

    @Test
    void chainedApprovalSurvivesCheckpointAndFreshCallbackContext() throws IOException, ClassNotFoundException {
        AgentCallbackContext context = prepare();
        agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
        agent.registerRail(new FirstApprovalRail()).toCompletableFuture().join();
        agent.registerRail(new SecondApprovalRail()).toCompletableFuture().join();
        context.fire(AgentCallbackEvent.BEFORE_INVOKE);
        assertInstanceOf(ToolInterruptException.class, execute(context).result());
        InteractiveInput firstApproval = new InteractiveInput();
        firstApproval.update("call-1", "first");
        context.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, firstApproval);
        ToolInterruptException second = assertInstanceOf(ToolInterruptException.class, execute(context).result());
        assertEquals("second", second.getRequest().getMessage());
        assertEquals(0, executions.get());

        ToolInterruptionState checkpoint = checkpoint(context, second);
        AgentCallbackContext fresh = new AgentCallbackContext(agent);
        fresh.setSession(context.getSession());
        fresh.setContext(context.getContext());
        InteractiveInput secondApproval = new InteractiveInput();
        secondApproval.update("call-1", "second");
        ResumeContext resume = resumeContext(fresh, checkpoint, secondApproval);

        assertNull(new ToolInterruptHandler(null).handleResume(resume));
        assertEquals(1, executions.get());
        assertEquals(Map.of(), checkpoint.getInterruptedTools());
        assertEquals("{\"approved_by\":\"first\"}", checkpoint.getAiMessage().getToolCalls().get(0).getArguments());
    }

    private ToolInterruptionState checkpoint(AgentCallbackContext context, ToolInterruptException interruption)
            throws IOException, ClassNotFoundException {
        ToolCall call = interruption.getToolCall().orElseThrow();
        AssistantMessage message = AssistantMessage.builder()
                .toolCalls(List.of(call)).build();
        ToolInterruptionState state = new ToolInterruptHandler(null).buildInterruptState(
                List.of(interruption), List.of(call), message, 0, "start").getState();
        state.copyRailStateFrom(ToolInterruptionState.railState(context));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ToolInterruptionState) input.readObject();
        }
    }

    private ResumeContext resumeContext(AgentCallbackContext context, ToolInterruptionState state, Object input) {
        ResumeContext resume = new ResumeContext();
        resume.setState(state);
        resume.setUserInput(input);
        resume.setCtx(context);
        resume.setSession(context.getSession());
        resume.setContext(context.getContext());
        resume.setExecuteToolCall((callback, calls, session, model) ->
                agent.executeToolCall(callback, calls, session, model).stream()
                        .map(AbilityManager.ExecutionResult::result).toList());
        return resume;
    }

    private AgentCallbackContext prepare() {
        ToolCard card = ToolCard.builder().id(TOOL_ID).name("confirmed_tool").inputParams(Map.of()).build();
        LocalFunction tool = new LocalFunction(card, ignored -> {
            executions.incrementAndGet();
            return "executed";
        });
        Runner.resourceMgr().addTool(tool, AGENT_ID);
        agent.getAbilityManager().add(card);
        agent.registerRail(new ConfirmInterruptRail(List.of("confirmed_tool"))).toCompletableFuture().join();
        AgentCallbackContext context = new AgentCallbackContext(agent);
        context.setSession(new AgentSession("native-confirm-session", null, agent.getCard()));
        ModelContext modelContext = mock(ModelContext.class);
        when(modelContext.addMessages(any(BaseMessage.class))).thenReturn(CompletableFuture.completedFuture(null));
        context.setContext(modelContext);
        context.fire(AgentCallbackEvent.BEFORE_INVOKE);
        return context;
    }

    private AbilityManager.ExecutionResult execute(AgentCallbackContext context) {
        ToolCall call = ToolCall.builder().id("call-1").name("confirmed_tool").arguments("{}").build();
        return agent.executeToolCall(context, List.of(call), context.getSession(), context.getContext()).get(0);
    }

    private static final class FirstApprovalRail extends BaseInterruptRail {
        private FirstApprovalRail() {
            super(List.of("confirmed_tool"));
            setPriority(110);
        }

        @Override
        protected InterruptDecision resolveInterrupt(AgentCallbackContext context, ToolCall call, Object input) {
            if ("first".equals(input)) {
                return approve("{\"approved_by\":\"first\"}");
            }
            return interrupt(new InterruptRequest("first", Map.of(), ""));
        }
    }

    private static final class SecondApprovalRail extends BaseInterruptRail {
        private SecondApprovalRail() {
            super(List.of("confirmed_tool"));
        }

        @Override
        protected InterruptDecision resolveInterrupt(AgentCallbackContext context, ToolCall call, Object input) {
            if (input != null) {
                return approve();
            }
            return interrupt(new InterruptRequest("second", Map.of(), ""));
        }
    }
}
