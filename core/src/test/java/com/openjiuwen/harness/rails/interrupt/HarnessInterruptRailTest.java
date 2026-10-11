/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

class HarnessInterruptRailTest {

    @Test
    void addPolicyAndGetToolsMirrorPythonStyleApi() {
        TestRail rail = new TestRail();

        rail.addPolicy("ask_user", null);
        rail.addTool("confirm");

        assertThat(rail.getTools()).containsExactlyInAnyOrder("ask_user", "confirm");
    }

    @Test
    void interruptDecisionRaisesFlagInContext() {
        TestRail rail = new TestRail();
        DeepAgent agent = new DeepAgent();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", "ask_user");
        values.put("tool_args", "{\"question\":\"name?\"}");
        CallbackContext ctx = new CallbackContext(agent, values);

        rail.beforeToolCall(ctx);

        assertThat(ctx.get("interrupt_required")).isEqualTo(true);
    }

    @Test
    void approveDecisionOverridesToolArgs() {
        TestRail rail = new TestRail();
        DeepAgent agent = new DeepAgent();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", "ask_user");
        values.put("tool_args", "{\"question\":\"name?\"}");
        values.put("user_input", "Alice");
        CallbackContext ctx = new CallbackContext(agent, values);

        rail.beforeToolCall(ctx);

        assertThat(ctx.get("tool_args")).isEqualTo("{\"response\":\"Alice\"}");
    }

    @Test
    void interactiveInputFallsBackToRawResponse() {
        InteractiveInput input = new InteractiveInput("Alice");
        input.setUserInputs(null);
        AgentCallbackContext context = new AgentCallbackContext(null);
        context.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, input);
        ToolCall call = ToolCall.builder().id("call-1").name("ask_user").build();
        assertThat(InterruptRailSupport.userInput(context, call, "test")).contains("Alice");
    }

    private static final class TestRail extends BaseInterruptRail {

        private TestRail() {
            super(List.of("ask_user"));
        }

        @Override
        public void beforeToolCall(CallbackContext ctx) {
            super.beforeToolCall(ctx);
            if (Boolean.TRUE.equals(ctx.get("interrupt_required"))) {
                Object userInput = ctx.get("user_input");
                if (userInput == null) {
                    ctx.put("interrupt_required", true);
                } else {
                    ctx.put("tool_args", "{\"response\":\"" + userInput + "\"}");
                    ctx.put("interrupt_required", false);
                }
            }
        }
    }
    /** Native callback, checkpoint and execution regressions. */
    @Nested
    class NativePipeline {
        private static final String AGENT_ID = "native-confirm-resume";
        private static final String TOOL_ID = "native-confirm-tool";

        private final AtomicInteger executions = new AtomicInteger();
        private ReActAgent agent = new ReActAgent(new AgentCard(AGENT_ID, AGENT_ID, "test"));

        @AfterEach
        void cleanup() {
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
            agent.getAgentCallbackManager().clear(AgentCallbackEvent.BEFORE_TOOL_CALL).toCompletableFuture().join();
            Runner.resourceMgr().removeTool(TOOL_ID, AGENT_ID, TagMatchStrategy.ALL, true);
        }

        @Test
        void instanceRailAlsoInterruptsBeforeExecutingTheTool() {
            AgentCallbackContext context = prepare();
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
            agent.registerInstanceRail(new ConfirmInterruptRail(List.of("confirmed_tool")))
                    .toCompletableFuture().join();

            assertInstanceOf(ToolInterruptException.class, execute(context).result());
            assertEquals(0, executions.get());
        }

        @Test
        void asynchronousInterruptionIsNotSwallowed() {
            AgentCallbackContext context = prepare();
            ToolInterruptException expected =
                    new ToolInterruptException(new InterruptRequest("async wait", Map.of(), ""));
            agent.registerCallback(AgentCallbackEvent.BEFORE_TOOL_CALL,
                    ignored -> CompletableFuture.failedFuture(expected), 200).toCompletableFuture().join();

            assertSame(expected, execute(context).result());
            assertEquals(0, executions.get());
        }

        @Test
        void chainedApprovalSurvivesCheckpointAndFreshCallbackContext() throws IOException, ClassNotFoundException {
            AgentCallbackContext context = prepare();
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
            agent.registerRail(new ApprovalRail("first")).toCompletableFuture().join();
            agent.registerRail(new ApprovalRail("second")).toCompletableFuture().join();
            context.fire(AgentCallbackEvent.BEFORE_INVOKE);
            assertInstanceOf(ToolInterruptException.class, execute(context).result());
            InteractiveInput firstApproval = new InteractiveInput();
            firstApproval.update("call-1", "first");
            context.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, firstApproval);
            ToolInterruptException second = assertInstanceOf(ToolInterruptException.class, execute(context).result());
            assertEquals("second", second.getRequest().getMessage());
            assertEquals(0, executions.get());

            ToolCard card = assertInstanceOf(ToolCard.class,
                    agent.getAbilityManager().get("confirmed_tool").orElseThrow());
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
            agent = new ReActAgent(new AgentCard(AGENT_ID, AGENT_ID, "rebuilt"));
            agent.getAbilityManager().add(card);
            agent.registerRail(new ApprovalRail("first")).toCompletableFuture().join();
            agent.registerRail(new ApprovalRail("second")).toCompletableFuture().join();
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

        @Test
        void reconstructedChangedPolicyDoesNotReplayPreviousApproval() throws IOException, ClassNotFoundException {
            AgentCallbackContext context = prepare();
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
            agent.registerRail(new SwitchingPolicyRail(false)).toCompletableFuture().join();
            registerSecondConfirmation();
            assertInstanceOf(ToolInterruptException.class, execute(context).result());
            context.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, response(true));
            ToolInterruptException second = assertInstanceOf(ToolInterruptException.class, execute(context).result());
            ToolCard card = assertInstanceOf(ToolCard.class,
                    agent.getAbilityManager().get("confirmed_tool").orElseThrow());
            agent.getAgentCallbackManager().unregisterAllRails(agent).toCompletableFuture().join();
            agent = new ReActAgent(new AgentCard(AGENT_ID, AGENT_ID, "changed policy"));
            agent.getAbilityManager().add(card);
            SwitchingPolicyRail replacement = new SwitchingPolicyRail(true);
            agent.registerRail(replacement).toCompletableFuture().join();
            registerSecondConfirmation();
            ToolInterruptionState checkpoint = checkpoint(context, second);
            AgentCallbackContext fresh = new AgentCallbackContext(agent);
            fresh.setSession(context.getSession());
            fresh.setContext(context.getContext());

            InteractiveInput approval = response(true);
            approval.update("call-1", Map.of("approved", true, "auto_confirm", true));
            assertNull(new ToolInterruptHandler(null).handleResume(resumeContext(fresh, checkpoint, approval)));
            assertEquals(1, replacement.calls);
            assertEquals(0, executions.get());
            assertEquals(Map.of(), context.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY));
        }

        @ParameterizedTest
        @ValueSource(strings = {"snake", "camel", "typed", "rejected"})
        void autoConfirmationIsSavedOnlyAfterValidApproval(String format) throws IOException, ClassNotFoundException {
            Object payload = Map.of("approved", true, "auto_confirm", true);
            if ("camel".equals(format)) {
                payload = Map.of("approved", true, "autoConfirm", true);
            }
            if ("typed".equals(format)) {
                payload = new ConfirmInterruptRail.ConfirmPayload(true, "", true);
            }
            if ("rejected".equals(format)) {
                payload = Map.of("approved", false, "auto_confirm", true);
            }
            InteractiveInput response = new InteractiveInput();
            response.update("call-1", payload);
            AgentCallbackContext context = prepare();
            ToolInterruptException pending = assertInstanceOf(ToolInterruptException.class, execute(context).result());
            ToolInterruptionState state = checkpoint(context, pending);
            assertNull(new ToolInterruptHandler(null).handleResume(resumeContext(context, state, response)));

            Object saved = context.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
            assertEquals("rejected".equals(format) ? Map.of() : Map.of("confirmed_tool", true), saved);
            assertEquals("rejected".equals(format) ? 0 : 1, executions.get());
        }

        private void registerSecondConfirmation() {
            ConfirmInterruptRail rail = new ConfirmInterruptRail(List.of("confirmed_tool"));
            rail.setPriority(99);
            agent.registerRail(rail).toCompletableFuture().join();
        }

        private static InteractiveInput response(boolean isApproved) {
            InteractiveInput response = new InteractiveInput();
            response.update("call-1", Map.of("approved", isApproved, "feedback", "rejected"));
            return response;
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
                return assertInstanceOf(ToolInterruptionState.class, input.readObject());
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

        private static final class SwitchingPolicyRail extends BaseInterruptRail {
            private final boolean isDenied;
            private int calls;

            private SwitchingPolicyRail(boolean isDenied) {
                super(List.of("confirmed_tool"));
                this.isDenied = isDenied;
                setPriority(110);
            }

            @Override
            protected String railId() {
                return getClass().getName() + ":" + isDenied;
            }

            @Override
            protected InterruptDecision resolveInterrupt(AgentCallbackContext context, ToolCall call, Object input) {
                calls++;
                if (isDenied) {
                    return reject("new policy denied");
                }
                if (input == null) {
                    return interrupt(new InterruptRequest("old approval", Map.of(), ""));
                }
                return approve();
            }
        }

        private static final class ApprovalRail extends BaseInterruptRail {
            private final String id;

            private ApprovalRail(String id) {
                super(List.of("confirmed_tool"));
                this.id = id;
                if ("first".equals(id)) {
                    setPriority(110);
                }
            }

            @Override
            protected String railId() {
                return getClass().getName() + ":" + id;
            }

            @Override
            protected InterruptDecision resolveInterrupt(AgentCallbackContext context, ToolCall call, Object input) {
                if (id.equals(input)) {
                    if ("first".equals(id)) {
                        return approve("{\"approved_by\":\"first\"}");
                    }
                    return approve();
                }
                return interrupt(new InterruptRequest(id, Map.of(), ""));
            }
        }
    }
}
