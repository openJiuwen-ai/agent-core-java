/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.eventbus.DefaultEventBus;
import com.openjiuwen.core.common.eventbus.events.ModelChangeEvent;
import com.openjiuwen.core.common.eventbus.events.ModelRemovedEvent;
import com.openjiuwen.core.common.eventbus.events.ModelUpdatedEvent;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Unit tests for {@link ModelMgr}: registration, update, removal, default-model
 * management, resolution priority, and EventBus event publishing.
 * <p>
 * Covers Issue #74 UT-A test cases.
 *
 * @since 0.1.16
 */
@DisplayName("ModelMgr Dynamic Model Tests (UT-A)")
class ModelMgrTest {
    private static final String TEST_PROVIDER = "ut-model-mgr-test";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    /** Unique prefix to avoid polluting the global Runner.resourceMgr(). */
    private static final String PREFIX = "dm-ut-a-" + UUID.randomUUID() + "-";
    private DefaultEventBus eventBus;
    private ModelMgr modelMgr;

    @BeforeEach
    void setup() {
        eventBus = new DefaultEventBus();
        modelMgr = new ModelMgr(eventBus);
    }

    // ── Mock helpers ──────────────────────────────────────────────

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
        ensureFactoryRegistered();
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

    /**
     * Minimal stub BaseModelClient that does not make network calls.
     * Required because {@link Model} constructor calls
     * {@code createModelClient()} which delegates to the factory.
     */
    static class StubModelClient extends BaseModelClient {
        StubModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        protected void validateConfig() {
            // Skip network-related validation for UT
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

    // ── UT-A-01 .. UT-A-14 ─────────────────────────────────────────

    @Nested
    @DisplayName("Registration & Events")
    class Registration {
        @Test
        @DisplayName("UT-A-01: addModel registers and publishes ModelUpdatedEvent")
        void addModel_registersAndPublishesEvent() {
            String id = PREFIX + "m1";
            AtomicReference<String> captured = new AtomicReference<>();
            eventBus.subscribe(ModelUpdatedEvent.class, e -> captured.set(e.getModelId()));

            modelMgr.addModel(id, mockSupplier("model-1"));

            assertTrue(modelMgr.hasModel(id));
            assertEquals(id, captured.get());
        }

        @Test
        @DisplayName("UT-A-02: addModel duplicate throws IllegalArgumentException")
        void addModel_duplicateThrows() {
            String id = PREFIX + "m1";
            modelMgr.addModel(id, mockSupplier("model-1"));

            assertThrows(IllegalArgumentException.class,
                    () -> modelMgr.addModel(id, mockSupplier("model-1-dup")));
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {
        @Test
        @DisplayName("UT-A-03: updateModel overwrites without throwing")
        void updateModel_overwritesWithoutThrow() {
            String id = PREFIX + "m1";
            modelMgr.addModel(id, mockSupplier("model-1"));
            Model original = modelMgr.getModel(id);

            modelMgr.updateModel(id, mockSupplier("model-1-updated"));
            Model updated = modelMgr.getModel(id);

            assertNotNull(updated);
            // Different supplier → different Model instance
            assertNotSame(original, updated);
            assertEquals("model-1-updated", updated.getModelConfig().getModelName());
        }
    }

    @Nested
    @DisplayName("Removal & Default Reassignment")
    class Removal {
        @Test
        @DisplayName("UT-A-04: removeModel on last model throws IllegalArgumentException")
        void removeModel_keepsLastModel() {
            String id = PREFIX + "m1";
            modelMgr.addModel(id, mockSupplier("model-1"));

            assertThrows(IllegalArgumentException.class, () -> modelMgr.removeModel(id));
        }

        @Test
        @DisplayName("UT-A-05: removeModel reassigns default to remaining model")
        void removeModel_reassignsDefault() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.addModel(m2, mockSupplier("model-2"));
            modelMgr.setDefaultModelId(m1);

            modelMgr.removeModel(m1);

            assertEquals(m2, modelMgr.getDefaultModelId());
        }

        @Test
        @DisplayName("UT-A-06: removeModel publishes ModelRemovedEvent")
        void removeModel_publishesRemovedEvent() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.addModel(m2, mockSupplier("model-2"));

            AtomicReference<String> captured = new AtomicReference<>();
            eventBus.subscribe(ModelRemovedEvent.class, e -> captured.set(e.getModelId()));

            modelMgr.removeModel(m1);

            assertEquals(m1, captured.get());
        }
    }

    @Nested
    @DisplayName("Default Model")
    class DefaultModel {
        @Test
        @DisplayName("UT-A-07: setDefaultModelId on unregistered id throws")
        void setDefaultModelId_unregisteredThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> modelMgr.setDefaultModelId("ghost-" + UUID.randomUUID()));
        }

        @Test
        @DisplayName("UT-A-08: getDefaultModel returns first registered when default unset")
        void getDefaultModel_firstWhenUnset() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.addModel(m2, mockSupplier("model-2"));

            assertNull(modelMgr.getDefaultModelId());
            assertTrue(modelMgr.getDefaultModel().isPresent());
        }
    }

    @Nested
    @DisplayName("List & Snapshot")
    class ListAndSnapshot {
        @Test
        @DisplayName("UT-A-09: listModelIds returns a defensive copy")
        void listModelIds_snapshotCopy() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.addModel(m2, mockSupplier("model-2"));

            List<String> ids = modelMgr.listModelIds();
            assertEquals(2, ids.size());

            // Modify the returned list — should not affect internal state
            ids.clear();

            assertEquals(2, modelMgr.listModelIds().size());
        }
    }

    @Nested
    @DisplayName("resolveModel")
    class ResolveModel {
        @Test
        @DisplayName("UT-A-10: resolveModel returns dynamic model when registered")
        void resolveModel_dynamicWins() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.addModel(m2, mockSupplier("model-2"));
            modelMgr.setDefaultModelId(m1);

            Model resolved = modelMgr.resolveModel(m2, null, null);

            assertNotNull(resolved);
            assertEquals("model-2", resolved.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-A-11: resolveModel falls back to default on unknown dynamic id")
        void resolveModel_fallbackDefaultOnUnknown() {
            String m1 = PREFIX + "m1";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.setDefaultModelId(m1);

            Model resolved = modelMgr.resolveModel("ghost-" + UUID.randomUUID(), null, null);

            assertNotNull(resolved);
            assertEquals("model-1", resolved.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-A-12: resolveModel constructs new Model from fallback config when no models registered")
        void resolveModel_fallbackNewModel() {
            ModelMgr emptyMgr = new ModelMgr(new DefaultEventBus());
            ModelClientConfig clientConfig = ModelClientConfig.builder()
                    .clientProvider(TEST_PROVIDER)
                    .apiKey("fb-key")
                    .apiBase("https://fallback.example.com")
                    .timeout(30.0)
                    .build();
            ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                    .modelName("fallback-model")
                    .build();

            ensureFactoryRegistered();

            Model resolved = emptyMgr.resolveModel(null, clientConfig, requestConfig);

            assertNotNull(resolved);
            assertEquals("fallback-model", resolved.getModelConfig().getModelName());
        }

        @Test
        @DisplayName("UT-A-13: resolveModel throws IllegalStateException when no candidate available")
        void resolveModel_noCandidateThrows() {
            ModelMgr emptyMgr = new ModelMgr(new DefaultEventBus());

            assertThrows(IllegalStateException.class,
                    () -> emptyMgr.resolveModel(null, null, null));
        }

        @Test
        @DisplayName("UT-A-14: resolveModel with blank dynamic id uses default")
        void resolveModel_blankDynamicUsesDefault() {
            String m1 = PREFIX + "m1";
            modelMgr.addModel(m1, mockSupplier("model-1"));
            modelMgr.setDefaultModelId(m1);

            Model resolved = modelMgr.resolveModel("   ", null, null);

            assertNotNull(resolved);
            assertEquals("model-1", resolved.getModelConfig().getModelName());
        }
    }

    // ── Event supertype dispatch ─────────────────────────────────

    @Test
    @DisplayName("UT-A-extra: ModelUpdatedEvent and ModelRemovedEvent both extend ModelChangeEvent")
    void modelChangeEvents_dispatchToSupertype() {
        List<ModelChangeEvent> events = new ArrayList<>();
        eventBus.subscribe(ModelChangeEvent.class, events::add);

        String m1 = PREFIX + "m1";
        String m2 = PREFIX + "m2";
        modelMgr.addModel(m1, mockSupplier("model-1"));
        modelMgr.addModel(m2, mockSupplier("model-2"));
        modelMgr.removeModel(m1);

        // Should have received at least: ModelUpdatedEvent(m1), ModelUpdatedEvent(m2), ModelRemovedEvent(m1)
        assertTrue(events.size() >= 3);
        assertTrue(events.stream().anyMatch(e -> e instanceof ModelUpdatedEvent && e.getModelId().equals(m1)));
        assertTrue(events.stream().anyMatch(e -> e instanceof ModelRemovedEvent && e.getModelId().equals(m1)));
    }
}
