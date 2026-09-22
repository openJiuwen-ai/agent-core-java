/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.systemtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deep-agent-stream pool exhaustion:
 * with the bounded platform pool (JDK 17), a stream request submitted
 * beyond the pool capacity (max threads plus queue) must fail that single
 * request cleanly — an error frame followed by stream termination —
 * instead of hanging the client iterator until the frame interval
 * timeout. The rejected probe leaves the pool healthy: previously
 * accepted streams still complete after their model gate is released and
 * a fresh stream afterwards completes normally.
 *
 * <p>The blocking model client gates every model call on a shared latch
 * so the saturated stream tasks hold their pool threads in round
 * completion. Class timeout is 240s: the red signature of the missing
 * END_FRAME is the client frame-interval timeout (60s) inside the probe
 * drain, which must stay inside the bound.</p>
 *
 * @since 0.1.16
 */
@Tag("system-test")
@DisplayName("S4 DeepAgent stream pool exhaustion")
@Timeout(240)
class DeepAgentStreamPoolExhaustionSystemTest {
    private static final String PROVIDER = "s4-pool-exhaustion-provider";

    private static final String CARD_ID = "s4-pool-exhaustion-agent";

    private static final String FINAL_ANSWER = "s4-final-answer";

    @BeforeEach
    void resetTenantContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    @DisplayName("probe stream beyond pool capacity: error frame + clean END, pool healthy")
    void probeBeyondCapacity_terminatesCleanly_poolHealthy() throws Exception {
        assumeTrue(Runtime.version().feature() < 21,
                "bounded platform pool only, current JDK feature " + Runtime.version().feature());
        CountDownLatch gate = new CountDownLatch(1);
        BlockingFinalClient client = new BlockingFinalClient(gate, PROVIDER);
        Model model = client.registerSharedModel();
        DeepAgent agent = HarnessFactory.createDeepAgent(agentCard(),
                DeepAgentConfig.builder()
                        .workspacePath("./target/s4-pool-exhaustion-repo")
                        .enableTaskLoop(true)
                        .maxIterations(1)
                        .completionTimeout(240.0)
                        .model(model)
                        .build(),
                null);
        List<Iterator<Object>> blockedStreams = new ArrayList<>();
        try {
            agent.ensureInitialized();
            int capacity = deepAgentStreamCapacity();
            for (int i = 0; i < capacity; i++) {
                blockedStreams.add(agent.stream(conversation("s4-conv-" + i), List.of(StreamMode.OUTPUT)));
            }
            awaitPoolFullyBusy();
            List<Object> probeFrames = drainProbe(agent);
            assertThat(probeFrames).as("probe received the rejection error frame").isNotEmpty();
            gate.countDown();
            for (Iterator<Object> stream : blockedStreams) {
                drainBounded(stream, "s4 blocked stream", 30_000L);
            }
            assertThat(client.modelCallCount()).as("model calls actually served after the gate released")
                    .isGreaterThanOrEqualTo(1);
            assertThat(streamFinalContent(agent, "s4-health-probe"))
                    .as("fresh stream after rejection completes with the final answer")
                    .contains(FINAL_ANSWER);
        } finally {
            gate.countDown();
            agent.destroy();
        }
    }

    private static AgentCard agentCard() {
        return AgentCard.builder()
                .id(CARD_ID)
                .name(CARD_ID)
                .description("S4 stream pool exhaustion agent")
                .build();
    }

    private static Map<String, Object> conversation(String conversationId) {
        return Map.of("query", "s4 request " + conversationId, "conversation_id", conversationId);
    }

    /**
     * Drains the beyond-capacity probe stream. The elapsed bound is the
     * observable contract: a rejected stream must terminate promptly
     * (error frame plus END_FRAME); the pre-fix behavior hangs the
     * iterator on the frame interval timeout for 60s.
     *
     * @param agent the agent holding the saturated stream pool
     * @return the drained probe frames, headed by the rejection error frame
     */
    private static List<Object> drainProbe(DeepAgent agent) {
        Iterator<Object> probe = agent.stream(conversation("s4-probe"), List.of(StreamMode.OUTPUT));
        long startMillis = System.currentTimeMillis();
        List<Object> frames = drainBounded(probe, "s4 probe", 90_000L);
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        assertThat(elapsedMillis).as("rejected probe terminates promptly after the error frame")
                .isLessThan(30_000L);
        return frames;
    }

    private static String streamFinalContent(DeepAgent agent, String conversationId) {
        List<Object> frames = drainBounded(agent.stream(conversation(conversationId), List.of(StreamMode.OUTPUT)),
                conversationId, 30_000L);
        StringBuilder content = new StringBuilder();
        for (Object frame : frames) {
            if (frame instanceof OutputSchema schema && schema.getPayload() != null) {
                content.append(" ").append(schema.getPayload());
            }
        }
        return content.toString();
    }

    private static List<Object> drainBounded(Iterator<Object> stream, String label, long timeoutMillis) {
        List<Object> frames = new ArrayList<>();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (stream.hasNext()) {
            frames.add(stream.next());
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException(label + " stream drain exceeded " + timeoutMillis + " ms");
            }
        }
        return frames;
    }

    private static void awaitPoolFullyBusy() throws ReflectiveOperationException, InterruptedException {
        ThreadPoolExecutor pool = streamPool();
        long deadline = System.currentTimeMillis() + 10_000L;
        while (pool.getActiveCount() < pool.getMaximumPoolSize()) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("deep-agent-stream pool did not reach full occupancy: "
                        + pool.getActiveCount() + "/" + pool.getMaximumPoolSize());
            }
            TimeUnit.MILLISECONDS.sleep(50L);
        }
    }

    private static int deepAgentStreamCapacity() throws ReflectiveOperationException {
        ThreadPoolExecutor pool = streamPool();
        return pool.getMaximumPoolSize() + pool.getQueue().size() + pool.getQueue().remainingCapacity();
    }

    private static ThreadPoolExecutor streamPool() throws ReflectiveOperationException {
        Field field = DeepAgent.class.getDeclaredField("STREAM_EXECUTOR");
        field.setAccessible(true);
        Object executor = field.get(null);
        if (!(executor instanceof ThreadPoolExecutor pool)) {
            throw new IllegalStateException("deep-agent-stream executor is not a bounded platform pool");
        }
        return pool;
    }

    /**
     * Deterministic blocking model client for S4: every invoke/stream
     * call awaits the shared gate before answering with the fixed final
     * message, so in-flight stream tasks deterministically hold their
     * deep-agent-stream pool threads. The {@code generate*} methods are
     * unsupported, mirroring {@link SequencedToolCallingClient}.
     */
    private static final class BlockingFinalClient extends BaseModelClient {
        private static final long GATE_TIMEOUT_SECONDS = 300L;

        private final CountDownLatch gate;

        private final AtomicInteger modelCalls = new AtomicInteger();

        BlockingFinalClient(CountDownLatch gate, String provider) {
            super(ModelRequestConfig.builder().modelName("fake-model").build(),
                    ModelClientConfig.builder()
                            .clientId(provider + "-client")
                            .clientProvider(provider)
                            .apiKey("test-key")
                            .apiBase("mirror://" + provider)
                            .build());
            this.gate = gate;
        }

        Model registerSharedModel() {
            BlockingFinalClient shared = this;
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return modelClientConfig.getClientProvider();
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return shared;
                }
            });
            return new Model(modelClientConfig, ModelRequestConfig.builder().modelName("fake-model").build());
        }

        int modelCallCount() {
            return modelCalls.get();
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop,
                BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            awaitGate();
            modelCalls.incrementAndGet();
            return finalAnswer();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature,
                Float topP, String model, Integer maxTokens,
                String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) {
            awaitGate();
            modelCalls.incrementAndGet();
            AssistantMessage answer = finalAnswer();
            return List.of(AssistantMessageChunk.builder()
                    .content(answer.getContent())
                    .finishReason(answer.getFinishReason())
                    .build()).iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                String size, String negativePrompt, int n,
                boolean isPromptExtend, boolean isWatermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation is not used in this system test");
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                String voice, String languageType,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation is not used in this system test");
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                String audioUrl, String model, String size,
                String resolution, int duration, boolean isPromptExtend,
                boolean isWatermark, String negativePrompt, Integer seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation is not used in this system test");
        }

        private void awaitGate() {
            try {
                if (!gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("s4 model gate was not released in time");
                }
            } catch (InterruptedException ex) {
                // G.CON.10: no interrupt-flag restore; surface the abnormal
                // interrupt as the captured failure with the cause preserved.
                throw new IllegalStateException("s4 model gate wait was interrupted", ex);
            }
        }

        private AssistantMessage finalAnswer() {
            return AssistantMessage.builder()
                    .content(FINAL_ANSWER)
                    .finishReason("stop")
                    .build();
        }
    }
}
