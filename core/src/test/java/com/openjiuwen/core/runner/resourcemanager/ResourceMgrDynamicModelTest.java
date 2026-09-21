/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.openjiuwen.core.runner.base.Tag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Unit tests for {@link ResourceMgr} model-management facade (Issue #74 UT-B).
 *
 * @since 0.1.15
 */
@DisplayName("ResourceMgr Dynamic Model Tests (UT-B)")
class ResourceMgrDynamicModelTest {
    private static final String PREFIX = "dm-ut-b-" + UUID.randomUUID() + "-";
    private static final String TEST_PROVIDER = "ut-resmgr-model-test";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);
    private ResourceMgr resourceMgr;

    @BeforeEach
    void setup() {
        resourceMgr = new ResourceMgr();
        ensureFactoryRegistered();
    }

    private static void ensureFactoryRegistered() {
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

    private Model newMockModel(String modelName) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(TEST_PROVIDER)
                .apiKey("test-key")
                .apiBase("https://test.example.com")
                .timeout(30.0)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private Supplier<Model> mockSupplier(String modelName) {
        return () -> newMockModel(modelName);
    }

    private Model awaitModel(String modelId) {
        Object value = resourceMgr.getModel(modelId, null).toCompletableFuture().join();
        return assertInstanceOf(Model.class, value);
    }

    static class StubModelClient extends BaseModelClient {
        StubModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        protected void validateConfig() {
            // Skip network validation for UT
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

    @Nested
    @DisplayName("addModel facade")
    class AddModel {
        @Test
        @DisplayName("UT-B-01: addModel succeeds once")
        void addModel_okOnce() {
            String id = PREFIX + "m1";
            Result<?, ?> result = resourceMgr.addModel(id, mockSupplier("model-1"), List.of(Tag.GLOBAL));

            assertTrue(result.isOk());
            resourceMgr.setDefaultModelId(id);
            assertEquals(id, resourceMgr.getDefaultModelId());
        }

        @Test
        @DisplayName("UT-B-02: addModel duplicate returns Error (not throws)")
        void addModel_duplicateReturnsError() {
            String id = PREFIX + "m1";
            resourceMgr.addModel(id, mockSupplier("model-1"), List.of(Tag.GLOBAL));

            Result<?, ?> result2 = resourceMgr.addModel(id, mockSupplier("model-1-dup"), List.of(Tag.GLOBAL));

            assertTrue(result2.isError());
            Model model = awaitModel(id);
            assertEquals("model-1", model.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-B-03: addModel no double-registration (supplier called per get)")
        void addModel_noDoubleRegistration() {
            String id = PREFIX + "m1";
            AtomicInteger callCount = new AtomicInteger(0);
            Supplier<Model> countingSupplier = () -> {
                callCount.incrementAndGet();
                return newMockModel("counted-model");
            };

            resourceMgr.addModel(id, countingSupplier, List.of(Tag.GLOBAL));

            Model m1 = awaitModel(id);
            Model m2 = awaitModel(id);
            assertNotNull(m1);
            assertNotNull(m2);
            assertEquals(2, callCount.get());
        }
    }

    @Nested
    @DisplayName("updateModel facade")
    class UpdateModel {
        @Test
        @DisplayName("UT-B-04: updateModel overwrites existing")
        void updateModel_overwrites() {
            String id = PREFIX + "m1";
            resourceMgr.addModel(id, mockSupplier("model-1"), List.of(Tag.GLOBAL));
            awaitModel(id);

            Result<?, ?> result = resourceMgr.updateModel(id, mockSupplier("model-1-v2"), List.of(Tag.GLOBAL));

            assertTrue(result.isOk());
            Model updated = awaitModel(id);
            assertEquals("model-1-v2", updated.getModelConfig().getModelName());
        }
    }

    @Nested
    @DisplayName("removeModel facade")
    class RemoveModel {
        @Test
        @DisplayName("UT-B-05: removeModel on last model returns error (not throws)")
        void removeModel_errorWhenLast() {
            String id = PREFIX + "m1";
            resourceMgr.addModel(id, mockSupplier("model-1"), List.of(Tag.GLOBAL));

            Result<?, ?> result = resourceMgr.removeModel(id);

            assertNotNull(result);
            assertTrue(result.isError());
            assertTrue(resourceMgr.listModelIds().contains(id));
        }
    }

    @Nested
    @DisplayName("addModels batch")
    class AddModels {
        @Test
        @DisplayName("UT-B-06: addModels registers multiple entries")
        void addModels_batch() {
            String m1 = PREFIX + "batch-1";
            String m2 = PREFIX + "batch-2";
            List<ResourceMgr.ModelEntry> entries = List.of(
                    new ResourceMgr.ModelEntry(m1, mockSupplier("batch-model-1")),
                    new ResourceMgr.ModelEntry(m2, mockSupplier("batch-model-2")));

            List<Result<?, ?>> results = resourceMgr.addModels(entries, List.of(Tag.GLOBAL));

            assertEquals(2, results.size());
            assertTrue(results.get(0).isOk());
            assertTrue(results.get(1).isOk());
        }
    }

    @Nested
    @DisplayName("resolveModel facade")
    class ResolveModel {
        @Test
        @DisplayName("UT-B-07: resolveModel delegates to ModelManager")
        void resolveModel_delegates() {
            String id = PREFIX + "m1";
            resourceMgr.addModel(id, mockSupplier("resolve-model-1"), List.of(Tag.GLOBAL));
            resourceMgr.setDefaultModelId(id);

            Model resolved = resourceMgr.resolveModel(id, null, null);

            assertNotNull(resolved);
            assertEquals("resolve-model-1", resolved.getModelConfig().getModelName());
        }
    }
}
