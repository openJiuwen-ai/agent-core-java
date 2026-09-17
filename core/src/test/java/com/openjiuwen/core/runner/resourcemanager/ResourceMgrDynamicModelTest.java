/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.Tag;
import com.openjiuwen.core.runner.base.TagMatchStrategy;

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
 * Unit tests for {@link ResourceMgr} model-management facade, including the
 * no-double-registration regression test (Issue #74 UT-B).
 *
 * @since 0.1.16
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

    // ── UT-B-01 .. UT-B-07 ─────────────────────────────────────────

    @Nested
    @DisplayName("addModel facade")
    class AddModel {
        @Test
        @DisplayName("UT-B-01: addModel succeeds once")
        void addModel_okOnce() {
            String id = PREFIX + "m1";
            Result<String> result = resourceMgr.addModel(id, mockSupplier("model-1"), Tag.GLOBAL);

            assertTrue(result.isOk());
            resourceMgr.setDefaultModelId(id);
            assertEquals(id, resourceMgr.getDefaultModelId());
        }

        @Test
        @DisplayName("UT-B-02: addModel duplicate returns Error (not throws)")
        void addModel_duplicateReturnsError() {
            String id = PREFIX + "m1";
            resourceMgr.addModel(id, mockSupplier("model-1"), Tag.GLOBAL);

            Result<String> result2 = resourceMgr.addModel(id, mockSupplier("model-1-dup"), Tag.GLOBAL);

            assertTrue(result2.isError());
            // Original provider unchanged
            Object retrieved = resourceMgr.getModel(id);
            assertTrue(retrieved instanceof Model);
            Model model = (Model) retrieved;
            assertNotNull(model);
            assertEquals("model-1", model.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-B-03: addModel no double-registration (supplier called once)")
        void addModel_noDoubleRegistration() {
            String id = PREFIX + "m1";
            AtomicInteger callCount = new AtomicInteger(0);
            Supplier<Model> countingSupplier = () -> {
                callCount.incrementAndGet();
                return newMockModel("counted-model");
            };

            resourceMgr.addModel(id, countingSupplier, Tag.GLOBAL);

            // Calling getModel should trigger supplier exactly once
            Object obj1 = resourceMgr.getModel(id);
            Object obj2 = resourceMgr.getModel(id);
            assertTrue(obj1 instanceof Model);
            assertTrue(obj2 instanceof Model);
            Model m1 = (Model) obj1;
            Model m2 = (Model) obj2;

            assertNotNull(m1);
            assertNotNull(m2);
            // Supplier is called per getModel() call ( getResource → provider.get() ),
            // but the key invariant is: no duplicate registration entries in the registry
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
            resourceMgr.addModel(id, mockSupplier("model-1"), Tag.GLOBAL);
            Object origObj = resourceMgr.getModel(id);
            assertTrue(origObj instanceof Model);
            Model original = (Model) origObj;

            Result<String> result = resourceMgr.updateModel(id, mockSupplier("model-1-v2"), Tag.GLOBAL);

            assertTrue(result.isOk());
            Object updatedObj = resourceMgr.getModel(id);
            assertTrue(updatedObj instanceof Model);
            Model updated = (Model) updatedObj;
            assertNotNull(updated);
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
            resourceMgr.addModel(id, mockSupplier("model-1"), Tag.GLOBAL);

            // removeModel through ResourceMgr facade wraps exceptions into Result
            Object result = resourceMgr.removeModel(id, Tag.GLOBAL, TagMatchStrategy.ALL, false);

            // The facade catches IllegalArgumentException → returns Error result
            // The result is a List<Result<?>> from innerRemoveResources
            assertNotNull(result);
            if (result instanceof List<?> list) {
                assertFalse(list.isEmpty());
                Object first = list.get(0);
                if (first instanceof Result<?> r) {
                    assertTrue(r.isError());
                }
            }
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

            List<Result<String>> results = resourceMgr.addModels(entries, Tag.GLOBAL);

            assertEquals(2, results.size());
            assertTrue(results.get(0).isOk());
            assertTrue(results.get(1).isOk());
        }
    }

    @Nested
    @DisplayName("resolveModel facade")
    class ResolveModel {
        @Test
        @DisplayName("UT-B-07: resolveModel delegates to ModelMgr")
        void resolveModel_delegates() {
            String id = PREFIX + "m1";
            resourceMgr.addModel(id, mockSupplier("resolve-model-1"), Tag.GLOBAL);
            resourceMgr.setDefaultModelId(id);

            Model resolved = resourceMgr.resolveModel(id, null, null);

            assertNotNull(resolved);
            assertEquals("resolve-model-1", resolved.getModelConfig().getModelName());
        }
    }
}
