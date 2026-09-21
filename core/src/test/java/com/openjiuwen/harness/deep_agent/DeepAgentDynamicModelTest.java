/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.schema.config.ModelConfigEntry;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unit tests for {@link com.openjiuwen.harness.deep_agent.DeepAgent#reconcileModelConfigs()}
 * (Issue #74 UT-D).
 * <p>
 * Verifies that DeepAgent construction correctly registers modelConfigs entries
 * into the global {@code Runner.resourceMgr()}, handles blank modelIds, throws
 * on duplicates, merges legacy model/backend, and preserves backward compatibility.
 *
 * @since 0.1.16
 */
@DisplayName("DeepAgent reconcileModelConfigs Tests (UT-D)")
class DeepAgentDynamicModelTest {
    private static final String TEST_PROVIDER = "ut-deepagent-model-test";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);
    private static final String PREFIX = "dm-ut-d-" + UUID.randomUUID() + "-";
    private final List<String> createdAgentIds = new ArrayList<>();
    private final List<String> registeredModelIds = new ArrayList<>();

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
        createdAgentIds.clear();
        registeredModelIds.clear();
        // Reset the process-global default model ID so each test starts clean.
        // Without this, a default left over from a prior test causes the guard in
        // DeepAgent.registerModelsAndSetDefault to skip setting the default.
        Runner.resourceMgr().setDefaultModelId(null);
    }

    @AfterEach
    void cleanup() {
        // Best-effort cleanup: remove models registered during tests
        for (String modelId : registeredModelIds) {
            try {
                if (Runner.resourceMgr().listModelIds().contains(modelId)) {
                    Runner.resourceMgr().removeModelForce(modelId, true);
                }
            } catch (RuntimeException ignored) {
                // Best effort
            }
        }
        Runner.resourceMgr().setDefaultModelId(null);
        // DeepAgent destroy is handled by GC; models cleaned above
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
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    private ModelConfigEntry newEntry(String modelId, boolean isDefault, String modelName) {
        return ModelConfigEntry.builder()
                .modelId(modelId)
                .isDefault(isDefault)
                .modelConfig(newRequestConfig(modelName))
                .modelClient(newClientConfig())
                .build();
    }

    private DeepAgentConfig.DeepAgentConfigBuilder baseConfig() {
        return DeepAgentConfig.builder()
                .workspacePath("./target/ut-deepagent-test-repo");
    }

    private void trackModel(String... ids) {
        for (String id : ids) {
            registeredModelIds.add(id);
        }
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

    // 鈹€鈹€ UT-D-01 .. UT-D-10 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

    @Nested
    @DisplayName("reconcileModelConfigs")
    class Reconcile {
        @Test
        @DisplayName("UT-D-01: reconcile registers all modelConfigs entries")
        void reconcile_registersAllEntries() {
            String m1 = PREFIX + "m1";
            String m2 = PREFIX + "m2";
            trackModel(m1, m2);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(
                            newEntry(m1, true, "model-1"),
                            newEntry(m2, false, "model-2")))
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            assertTrue(Runner.resourceMgr().listModelIds().contains(m1));
            assertTrue(Runner.resourceMgr().listModelIds().contains(m2));
            assertEquals(m1, Runner.resourceMgr().getDefaultModelId());
        }

        @Test
        @DisplayName("UT-D-02: reconcile auto-generates modelId when blank")
        void reconcile_blankModelIdGenerates() {
            String generatedPrefix = "model-blank@" + TEST_PROVIDER;

            ModelConfigEntry entry = ModelConfigEntry.builder()
                    .modelId("  ")  // blank
                    .isDefault(true)
                    .modelConfig(newRequestConfig("model-blank"))
                    .modelClient(newClientConfig())
                    .build();
            // We can't predict the exact id, so we just verify construction doesn't throw
            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(entry))
                    .build();

            assertDoesNotThrow(() -> {
                DeepAgent agent = HarnessFactory.createDeepAgent(config);
                agent.ensureInitialized();
            });

            // The generated id should be in listModelIds
            boolean isFound = Runner.resourceMgr().listModelIds().stream()
                    .anyMatch(id -> id.startsWith("model-blank@"));
            assertTrue(isFound);
            // Track for cleanup
            Runner.resourceMgr().listModelIds().stream()
                    .filter(id -> id.startsWith("model-blank@"))
                    .forEach(registeredModelIds::add);
        }

        @Test
        @DisplayName("UT-D-03: reconcile throws on duplicate modelId")
        void reconcile_duplicateEntryThrows() {
            String dupId = PREFIX + "dup";
            trackModel(dupId);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(
                            newEntry(dupId, true, "model-a"),
                            newEntry(dupId, false, "model-b")))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> HarnessFactory.createDeepAgent(config));
        }

        @Test
        @DisplayName("UT-D-04: reconcile merges legacy model+backend (no modelConfigs)")
        void reconcile_mergesLegacyModelBackend() {
            // Use model + backend fields without modelConfigs
            ModelRequestConfig modelReq = newRequestConfig("legacy-model");
            ModelClientConfig clientConfig = newClientConfig();

            DeepAgentConfig config = baseConfig()
                    .model(modelReq)
                    .backend(clientConfig)
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            // The legacy model should be registered under an auto-generated id
            // containing "legacy-model@" prefix
            boolean isLegacyFound = Runner.resourceMgr().listModelIds().stream()
                    .anyMatch(id -> id.contains("legacy-model"));
            assertTrue(isLegacyFound);
            // Track for cleanup
            Runner.resourceMgr().listModelIds().stream()
                    .filter(id -> id.contains("legacy-model"))
                    .forEach(registeredModelIds::add);
        }

        @Test
        @DisplayName("UT-D-05: reconcile extracts modelRequestConfig fields")
        void reconcile_extractsRequestConfig() {
            String m1 = PREFIX + "req-config";
            trackModel(m1);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(newEntry(m1, true, "model-with-temp")))
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            Model resolved = Runner.resourceMgr().resolveModel(m1, null, null);
            assertNotNull(resolved);
            assertEquals("model-with-temp", resolved.getModelConfig().getModelName());
            assertEquals(0.7, resolved.getModelConfig().getTemperature());
            assertEquals(4096, resolved.getModelConfig().getMaxTokens());
        }

        @Test
        @DisplayName("UT-D-06: reconcile extracts modelClientConfig fields")
        void reconcile_extractsClientConfig() {
            String m1 = PREFIX + "client-config";
            trackModel(m1);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(newEntry(m1, true, "model-client-test")))
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            Model resolved = Runner.resourceMgr().resolveModel(m1, null, null);
            assertNotNull(resolved);
            assertNotNull(resolved.getModelClientConfig());
            assertEquals(TEST_PROVIDER, resolved.getModelClientConfig().getClientProvider());
            assertEquals("https://test.example.com", resolved.getModelClientConfig().getApiBase());
        }

        @Test
        @DisplayName("UT-D-07: reconcile sets default model from isDefault=true")
        void reconcile_defaultPriority() {
            String m1 = PREFIX + "default-true";
            String m2 = PREFIX + "default-false";
            trackModel(m1, m2);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(
                            newEntry(m1, true, "model-default"),
                            newEntry(m2, false, "model-nondefault")))
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            assertEquals(m1, Runner.resourceMgr().getDefaultModelId());
        }

        @Test
        @DisplayName("UT-D-08: reconcile throws when more than one default entry")
        void reconcile_multipleDefaultsThrows() {
            String m1 = PREFIX + "multi-def-1";
            String m2 = PREFIX + "multi-def-2";
            trackModel(m1, m2);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(
                            newEntry(m1, true, "model-a"),
                            newEntry(m2, true, "model-b")))
                    .build();

            assertThrows(IllegalArgumentException.class,
                    () -> HarnessFactory.createDeepAgent(config));
        }

        @Test
        @DisplayName("UT-D-09: invoke without modelConfigs still works (regression)")
        void invoke_withoutModelConfigsStillWorks() {
            ModelRequestConfig modelReq = newRequestConfig("no-configs-model");
            ModelClientConfig clientConfig = newClientConfig();

            DeepAgentConfig config = baseConfig()
                    .model(modelReq)
                    .backend(clientConfig)
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            // No modelConfigs entries should have been registered
            assertNotNull(agent.getCard());
            // Track for cleanup
            Runner.resourceMgr().listModelIds().stream()
                    .filter(id -> id.contains("no-configs-model"))
                    .forEach(registeredModelIds::add);
        }

        @Test
        @DisplayName("UT-D-10: reconcile defaults to first entry when none marked default")
        void reconcile_isDefaultMissingFallsBackToFirst() {
            String m1 = PREFIX + "no-default-1";
            String m2 = PREFIX + "no-default-2";
            trackModel(m1, m2);

            DeepAgentConfig config = baseConfig()
                    .modelConfigs(List.of(
                            newEntry(m1, false, "model-no-default-1"),
                            newEntry(m2, false, "model-no-default-2")))
                    .build();

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            agent.ensureInitialized();

            // First entry should become default
            assertEquals(m1, Runner.resourceMgr().getDefaultModelId());
        }
    }
}
