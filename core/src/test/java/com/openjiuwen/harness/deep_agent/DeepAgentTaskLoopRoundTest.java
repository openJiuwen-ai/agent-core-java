/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Task-loop round numbers must be 1-based and increment, matching
 * {@code eventHandler.prepareRound} rather than the stub {@code getRoundCounter}.
 */
class DeepAgentTaskLoopRoundTest {

    @TempDir
    private Path tempDir;

    private DeepAgent agent;

    @AfterEach
    void shutdownAgent() {
        if (agent != null) {
            agent.shutdown();
        }
    }

    @Test
    void invokeResumeRestoresAndCommitsNewCallerSession() {
        assertInvokeResume(true);
    }

    @Test
    void invokeResumeRestoresAndCommitsWithoutCallerSession() {
        assertInvokeResume(false);
    }

    @Test
    void invokeResumeKeepsPreparedSessionLifecycleOwner() {
        AgentCard card = uniqueCard("prepared-resume");
        String sessionId = UUID.randomUUID().toString();
        DeepAgentSession session = new DeepAgentSession(sessionId, null, card);
        session.preRun(Map.of());
        session.updateState(Map.of("pending", "first"));
        agent = new DeepAgent(card, DeepAgentConfig.builder().enableTaskLoop(true).build(), null);
        agent.setReactAgent(new SessionUpdatingAgent(), true);
        InteractiveInput query = new InteractiveInput();
        query.update("confirm", "first");
        try {
            Map<String, Object> result = agent.invoke(Map.of("query", query, "conversation_id", sessionId), session);
            assertThat(result).containsEntry("output", "first");
            assertThat(session.getState("pending")).isEqualTo("second");
            assertThat(session.isPostRunDone()).isFalse();
        } finally {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    private void assertInvokeResume(boolean hasCaller) {
        AgentCard card = uniqueCard("invoke-resume");
        String sessionId = UUID.randomUUID().toString();
        DeepAgentSession seed = new DeepAgentSession(sessionId, null, card);
        seed.preRun(Map.of());
        seed.updateState(Map.of("pending", "first"));
        seed.postRun();
        agent = new DeepAgent(card, DeepAgentConfig.builder().enableTaskLoop(true).build(), null);
        agent.setReactAgent(new SessionUpdatingAgent(), true);
        try {
            for (String value : List.of("first", "second")) {
                InteractiveInput query = new InteractiveInput();
                query.update("confirm", value);
                AgentSession caller = hasCaller ? new AgentSession(sessionId, null, card) : null;
                Map<String, Object> result = agent.invoke(Map.of("query", query, "conversation_id", sessionId), caller);
                assertThat(result).containsEntry("output", value);
                if (caller != null) {
                    assertThat(caller.isPreRunDone()).isTrue();
                    assertThat(caller.isPostRunDone()).isTrue();
                    assertThat(caller.getState("completed")).isEqualTo(value);
                }
                DeepAgentSession restored = new DeepAgentSession(sessionId, null, card);
                restored.preRun(Map.of());
                assertThat(restored.getState("completed")).isEqualTo(value);
                assertThat(restored.getState("pending")).isEqualTo("first".equals(value) ? "second" : "");
            }
        } finally {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
    }

    private static final class SessionUpdatingAgent {
        /**
         * Checks restored state and writes the state consumed by the following resume.
         *
         * @param inputs invocation containing the confirmation
         * @param session effective session owning the resume
         * @return completed response
         * @since 0.1.17
         */
        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            assertThat(session).isNotNull();
            InteractiveInput query = assertInstanceOf(InteractiveInput.class, inputs.get("query"));
            Object expected = query.getUserInputs().get("confirm");
            assertThat(session.getState("pending")).isEqualTo(expected);
            session.updateState(Map.of("completed", expected, "pending", "first".equals(expected) ? "second" : ""));
            return CompletableFuture.completedFuture(Map.of("output", expected, "result_type", "answer"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskLoopRoundsAreOneBasedAndIncrement() {
        agent = HarnessFactory.createDeepAgent(
                uniqueCard("round-counter"),
                DeepAgentConfig.builder()
                        .workspacePath(tempDir.toString())
                        .enableTaskLoop(true)
                        .maxIterations(4)
                        .build(),
                null);
        installEchoModel(agent, "model:", 3, 5);
        agent.getLoopController().enqueueFollowUp("continue");

        Map<String, Object> result = agent.invoke(Map.of("query", "Start task loop."));

        List<Map<String, Object>> rounds = (List<Map<String, Object>>) result.get("rounds");
        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(0)).containsEntry("round", 1).containsEntry("is_follow_up", false);
        assertThat(rounds.get(1)).containsEntry("round", 2).containsEntry("is_follow_up", true);
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(prefix).description("round test").build();
    }

    private static void installEchoModel(DeepAgent agent, String prefix, int inputTokens, int outputTokens) {
        Model model = Mockito.mock(Model.class);
        when(model.invoke(any(List.class), any(ModelInvokeOptions.class)))
                .thenAnswer(invocation -> {
                    String text = extractLastMessageText(invocation.getArgument(0));
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            echoMessage(prefix + text, inputTokens, outputTokens));
                });
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> echoMessage(
                        prefix + extractLastMessageText(invocation.getArgument(0)),
                        inputTokens,
                        outputTokens));
        agent.getAgent().setLlm(model);
    }

    private static AssistantMessage echoMessage(String content, int inputTokens, int outputTokens) {
        return AssistantMessage.builder()
                .content(content)
                .usageMetadata(UsageMetadata.builder()
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .totalTokens(inputTokens + outputTokens)
                        .build())
                .build();
    }

    private static String extractLastMessageText(Object rawMessages) {
        if (rawMessages instanceof List<?> messages && !messages.isEmpty()) {
            Object last = messages.get(messages.size() - 1);
            if (last instanceof BaseMessage baseMessage && baseMessage.getContent() != null) {
                return String.valueOf(baseMessage.getContent());
            }
        }
        return String.valueOf(rawMessages);
    }
}
