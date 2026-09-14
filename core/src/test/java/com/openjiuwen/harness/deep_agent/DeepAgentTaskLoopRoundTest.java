/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
