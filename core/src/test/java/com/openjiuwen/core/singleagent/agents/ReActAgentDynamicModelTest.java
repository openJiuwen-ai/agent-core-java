/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.modelclients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Unit tests for {@link ReActAgent#getLlm(AgentCallbackContext)} dynamic model
 * resolution (Issue #74 UT-E).
 * <p>
 * This test class is in the same package as {@link ReActAgent} to access the
 * {@code protected getLlm(AgentCallbackContext)} method.
 *
 * @since 0.1.16
 */
@DisplayName("ReActAgent getLlm(ctx) Dynamic Model Tests (UT-E)")
class ReActAgentDynamicModelTest {
    private static final String TEST_PROVIDER = "ut-react-dyn-test";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);
    private static final String PREFIX = "dm-ut-e-" + UUID.randomUUID() + "-";
    private final List<String> registeredModelIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeAll
    static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return TEST_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new StubModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    @BeforeEach
    void setup() {
        registeredModelIds.clear();
    }

    @AfterEach
    void cleanup() {
        for (String modelId : registeredModelIds) {
            try {
                if (Runner.resourceMgr().listModelIds().contains(modelId)) {
                    Runner.resourceMgr().removeModelForce(modelId, true);
                }
            } catch (RuntimeException ignored) {
                // Best effort cleanup
            }
        }
    }

    private ModelClientConfig newClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(TEST_PROVIDER)
                .apiKey("test-key")
                .apiBase("https://test.example.com")
                .timeout(30.0)
                .build();
    }

    private ModelRequestConfig newRequestConfig(String modelName) {
        return ModelRequestConfig.builder()
                .modelName(modelName)
                .build();
    }

    private Model newMockModel(String modelName) {
        return new Model(newClientConfig(), newRequestConfig(modelName));
    }

    private void registerModel(String modelId, String modelName) {
        Supplier<Model> supplier = () -> newMockModel(modelName);
        Runner.resourceMgr().addModel(modelId, supplier, List.of(Tag.GLOBAL));
        registeredModelIds.add(modelId);
    }

    private ReActAgent newAgent() {
        AgentCard card = AgentCard.builder()
                .id("ut-e-agent-" + UUID.randomUUID())
                .name("ut-e-agent")
                .description("UT-E test agent")
                .build();
        return new ReActAgent(card);
    }

    private AgentCallbackContext newCtx(String dynamicModelId) {
        return AgentCallbackContext.builder()
                .dynamicModelId(dynamicModelId)
                .build();
    }

    static class StubModelClient extends BaseModelClient {
        StubModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        protected void validateConfig() {
            // Skip network validation
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return new AssistantMessage("stub");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean isPromptExtend, boolean isWatermark, int seed,
                Map<String, Object> kwargs) {
            return new ImageGenerationResponse();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            return new AudioGenerationResponse();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                String model, String size, String resolution, int duration, boolean isPromptExtend, boolean isWatermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            return new VideoGenerationResponse();
        }
    }

    // 鈹€鈹€ UT-E-01 .. UT-E-05 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Nested
    @DisplayName("getLlm(ctx) dynamic resolution")
    class GetLlmCtx {
        @Test
        @DisplayName("UT-E-01: getLlm(ctx) resolves dynamic model when registered")
        void getLlmCtx_resolvesDynamic() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            registerModel(m1, "model-1");
            registerModel(m2, "model-2");
            Runner.resourceMgr().setDefaultModelId(m1);

            ReActAgent agent = newAgent();
            AgentCallbackContext ctx = newCtx(m2);

            Model resolved = agent.getLlm(ctx);

            assertNotNull(resolved);
            assertEquals("model-2", resolved.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-E-02: getLlm(ctx) with null dynamicModelId uses default")
        void getLlmCtx_nullUsesDefault() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            registerModel(m1, "model-1");
            registerModel(m2, "model-2");
            Runner.resourceMgr().setDefaultModelId(m1);

            ReActAgent agent = newAgent();
            AgentCallbackContext ctx = newCtx(null);

            Model resolved = agent.getLlm(ctx);

            assertNotNull(resolved);
            assertEquals("model-1", resolved.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-E-03: getLlm(ctx) with unknown dynamicModelId falls back to default")
        void getLlmCtx_unknownFallsBackDefault() {
            String m1 = PREFIX + "m1";
            registerModel(m1, "model-1");
            Runner.resourceMgr().setDefaultModelId(m1);

            ReActAgent agent = newAgent();
            AgentCallbackContext ctx = newCtx("ghost-" + UUID.randomUUID());

            Model resolved = agent.getLlm(ctx);

            assertNotNull(resolved);
            // Should fall back to default model
            assertEquals("model-1", resolved.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-E-04: getLlm(ctx) with no registered models falls back to lazy load")
        void getLlmCtx_noMgrModelFallsBackLazy() {
            // With null dynamicModelId, getLlm(ctx) should resolve via ModelMgr (default path).
            // If no models registered, resolveModel throws IllegalStateException,
            // proving the fallback path was attempted.
            ReActAgent agent = newAgent();
            AgentCallbackContext ctx = newCtx(null);

            try {
                Model resolved = agent.getLlm(ctx);
                assertNotNull(resolved);
            } catch (IllegalStateException e) {
                // Expected when no models registered and no config 鈥?this proves
                // the fallback to getLlm() was attempted
                assertTrue(e.getMessage().contains("model_client_config") || e.getMessage().contains("no model"));
            }
        }

        @Test
        @DisplayName("UT-E-05: getLlm(ctx) is thread-safe under concurrent access")
        void getLlmCtx_threadSafeConcurrent() throws Exception {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            registerModel(m1, "model-1");
            registerModel(m2, "model-2");
            Runner.resourceMgr().setDefaultModelId(m1);

            ReActAgent agent = newAgent();

            int threads = 2;
            int iterations = 50;
            ExecutorService pool = new java.util.concurrent.ThreadPoolExecutor(
                    threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>());
            CountDownLatch start = new CountDownLatch(1);
            ConcurrentHashMap<String, AtomicInteger> resultCounts = new ConcurrentHashMap<>();
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                final String modelId = (t % 2 == 0) ? m1 : m2;
                final String expectedName = (t % 2 == 0) ? "model-1" : "model-2";
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            AgentCallbackContext ctx = newCtx(modelId);
                            Model resolved = agent.getLlm(ctx);
                            assertNotNull(resolved, "Thread should never get null model");
                            String actualName = resolved.getModelConfig().getModelName();
                            assertEquals(expectedName, actualName,
                                    "Thread " + Thread.currentThread().getName()
                                            + " got wrong model: " + actualName + " (expected " + expectedName + ")");
                            resultCounts.computeIfAbsent(actualName, k -> new AtomicInteger()).incrementAndGet();
                        }
                    } catch (AssertionError e) {
                        // Re-throw to fail the test
                        throw e;
                    } catch (InterruptedException e) {
                        // Thread interrupted 鈥?record as a test failure
                        throw new AssertionError("Thread interrupted", e);
                    }
                }));
            }

            start.countDown();
            pool.shutdown();
            boolean isDone = pool.awaitTermination(30, TimeUnit.SECONDS);

            assertTrue(isDone, "All threads should complete within timeout");
            // Collect futures to propagate assertion failures from worker threads
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof AssertionError ae) {
                        throw ae;
                    }
                    throw new AssertionError("Worker thread failed", e.getCause());
                }
            }
            assertEquals(2, resultCounts.size(),
                    "Both model names should appear in results: " + resultCounts.keySet());
            assertEquals(iterations, resultCounts.get("model-1").get(),
                    "model-1 should have been resolved exactly " + iterations + " times");
            assertEquals(iterations, resultCounts.get("model-2").get(),
                    "model-2 should have been resolved exactly " + iterations + " times");
        }
    }
}
