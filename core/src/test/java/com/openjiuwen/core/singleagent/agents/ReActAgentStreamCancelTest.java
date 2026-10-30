/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that closing the stream iterator during tool execution stops the
 * agent instead of feeding the cancelled tool message back into the model.
 */
class ReActAgentStreamCancelTest {

    private static final long TOOL_DURATION_MS = 3000;
    private static final long SETTLE_MS = 8000;

    /**
     * Fake model that records every call and answers with a tool call until the
     * cancelled tool message shows up, then returns a final answer.
     */
    private static final class CountingClient implements Model.ModelClient {
        final AtomicInteger calls = new AtomicInteger();
        /** Signals that the first model response (a tool call) has been produced. */
        final CountDownLatch firstCallDone = new CountDownLatch(1);
        final String toolName;

        private CountingClient(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(policy(messages));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            calls.incrementAndGet();
            AssistantMessage message = policy(messages);
            AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                    .content(message.getContent())
                    .toolCalls(message.getToolCalls())
                    .finishReason(message.getFinishReason())
                    .build();
            return List.of(chunk).iterator();
        }

        private AssistantMessage policy(List<BaseMessage> messages) {
            BaseMessage last = messages.get(messages.size() - 1);
            if (last instanceof ToolMessage) {
                return textMessage("final answer");
            }
            firstCallDone.countDown();
            return toolCallMessage();
        }

        private AssistantMessage toolCallMessage() {
            ToolCall call = ToolCall.builder()
                    .id("call_" + UUID.randomUUID())
                    .name(toolName)
                    .arguments("{}")
                    .index(0)
                    .build();
            return AssistantMessage.builder()
                    .content("")
                    .finishReason("tool_calls")
                    .toolCalls(List.of(call))
                    .build();
        }
    }

    private static AssistantMessage textMessage(String content) {
        return AssistantMessage.builder()
                .content(content)
                .toolCalls(List.of())
                .finishReason("stop")
                .build();
    }

    private static LocalFunction slowTool(String name, boolean parallelSafe) {
        ToolCard card = ToolCard.builder()
                .id(name)
                .name(name)
                .description(name)
                .parallelSafe(parallelSafe)
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(),
                        "required", List.of()))
                .build();
        LocalFunction.ContextFunction fn = (inputs, kwargs) -> {
            try {
                Thread.sleep(TOOL_DURATION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("tool interrupted", e);
            }
            return "slow tool finished";
        };
        return new LocalFunction(card, fn);
    }

    private static ReActAgent newAgent(String id, CountingClient client) {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id(id).name(id).description("stream cancel").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(4)
                .promptTemplate(List.of(Map.of("role", "system", "content", "sys")))
                .build());
        agent.setLlm(new Model(client));
        return agent;
    }

    private static void registerTool(ReActAgent agent, LocalFunction tool) {
        Runner.resourceMgr().addTool(tool, null);
        agent.getAbilityManager().add(List.of(tool.getCard()));
    }

    private static int cancelDuringTool(boolean parallelSafe) throws Exception {
        String toolName = "slow_tool_" + parallelSafe + "_" + UUID.randomUUID();
        CountingClient client = new CountingClient(toolName);
        ReActAgent agent = newAgent("cancel_agent_" + UUID.randomUUID(), client);
        registerTool(agent, slowTool(toolName, parallelSafe));

        Map<String, Object> inputs = Map.of(
                "query", "go",
                "conversation_id", "s-cancel-" + UUID.randomUUID());
        AgentSession session = AgentSession.createAgentSession(
                String.valueOf(inputs.get("conversation_id")), null, agent.getCard());
        session.preRun(Map.of("inputs", inputs));

        Iterator<Object> iterator = agent.stream(inputs, session, List.of());
        // Wait until the first model response was produced, so the tool is
        // running when the stream is closed.
        assertThat(client.firstCallDone.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300);
        if (iterator instanceof AutoCloseable closeable) {
            closeable.close();
        }
        Thread.sleep(SETTLE_MS);
        return client.calls.get();
    }

    /**
     * Closing the stream while a parallel-safe tool runs must stop the loop:
     * the cancelled tool result must not trigger another model call.
     */
    @Test
    void streamCloseDuringParallelSafeToolStopsModelCalls() throws Exception {
        int calls = cancelDuringTool(true);
        assertThat(calls)
                .as("parallelSafe=true: LLM calls after cancel (1 = stopped, 2 = kept running) = %s", calls)
                .isEqualTo(1);
    }

    /**
     * Same expectation for a non-parallel-safe tool: cancel must stop the loop.
     */
    @Test
    void streamCloseDuringSerialToolStopsModelCalls() throws Exception {
        int calls = cancelDuringTool(false);
        assertThat(calls)
                .as("parallelSafe=false: LLM calls after cancel (1 = stopped, 2 = kept running) = %s", calls)
                .isEqualTo(1);
    }
}
