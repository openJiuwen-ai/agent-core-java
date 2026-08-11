/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepAgentStreamTaskLoopExecutorTest {

    private ExecutorService testExecutor;

    @AfterEach
    void tearDown() {
        DeepAgent.clearStreamTaskLoopExecutorOverride();
        if (testExecutor != null) {
            testExecutor.shutdownNow();
            testExecutor = null;
        }
    }

    @Test
    @DisplayName("streamTaskLoop 使用有界池且 tap 路径仍可产出 answer chunk")
    void streamTaskLoopShouldStreamViaBoundedExecutor() throws Exception {
        DeepAgent agent = createStreamingTaskLoopAgent();
        List<Object> chunks = new ArrayList<>();
        agent.stream(Map.of("query", "bounded pool stream", "conversation_id", "bounded-stream-session"))
                .forEachRemaining(chunks::add);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().filter(OutputSchema.class::isInstance).map(OutputSchema.class::cast)
                .anyMatch(schema -> "answer".equals(schema.getType()) || "llm_output".equals(schema.getType())))
                .isTrue();
    }

    @Test
    @DisplayName("deep-agent-stream 池满时 stream 返回 error chunk 而非空挂")
    void streamTaskLoopShouldEmitErrorWhenExecutorRejects() throws Exception {
        testExecutor = OpenJiuwenExecutors.newBoundedModulePool("deep-agent-stream-test", 1, 1, true);
        DeepAgent.setStreamTaskLoopExecutorOverride(testExecutor);

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch holdWorker = new CountDownLatch(1);
        testExecutor.submit(() -> {
            workerStarted.countDown();
            try {
                holdWorker.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        testExecutor.submit(() -> {
            try {
                holdWorker.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        DeepAgent agent = createStreamingTaskLoopAgent();
        Iterator<Object> stream = agent.stream(
                Map.of("query", "should reject", "conversation_id", "reject-stream-session"));

        List<Object> chunks = new ArrayList<>();
        stream.forEachRemaining(chunks::add);

        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk).isInstanceOf(OutputSchema.class);
            OutputSchema schema = (OutputSchema) chunk;
            assertThat(schema.getType()).isEqualTo("error");
        });

        holdWorker.countDown();
    }

    private static DeepAgent createStreamingTaskLoopAgent() throws Exception {
        DeepAgent agent = HarnessFactory.createDeepAgent(
                DeepAgentConfig.builder().workspacePath("./repo").enableTaskLoop(true).maxIterations(1).build());
        agent.ensureInitialized();
        installStreamingModel(agent);
        return agent;
    }

    private static void installStreamingModel(DeepAgent agent) throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("answer")
                        .usageMetadata(UsageMetadata.builder().inputTokens(1).outputTokens(1).totalTokens(2).build())
                        .build());
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("delta").build(),
                        AssistantMessageChunk.builder().content("answer")
                                .usageMetadata(
                                        UsageMetadata.builder().inputTokens(1).outputTokens(1).totalTokens(2).build())
                                .build()).iterator());
        agent.getAgent().setLlm(model);
    }
}
