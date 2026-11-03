/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code BasePromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/base.py}.
 */
class BasePromptBuilderTest {

    @AfterEach
    void unregisterOpenAiInvoker() {
        Model.unregisterInvoker(ProviderType.OPEN_AI.getValue());
    }

    @Test
    void constructorCreatesModelFromConfigsLikePythonInitializer() {
        Model.registerInvoker(ProviderType.OPEN_AI.getValue(), (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("unit-model").build();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiKey("key")
                .apiBase("https://example.invalid")
                .build();

        RecordingPromptBuilder builder = new RecordingPromptBuilder(requestConfig, clientConfig);

        assertNotNull(builder.getModel());
        assertSame(requestConfig, builder.getModel().getModelConfig());
        assertSame(clientConfig, builder.getModel().getModelClientConfig());
    }

    @Test
    void abstractBuildReceivesDynamicArgsAndKwargs() {
        Model.registerInvoker(ProviderType.OPEN_AI.getValue(), (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        RecordingPromptBuilder builder = new RecordingPromptBuilder(
                ModelRequestConfig.builder().modelName("unit-model").build(),
                ModelClientConfig.builder().clientProvider(ProviderType.OPEN_AI).build());

        Optional<String> result = builder.build(
                List.of("topic"),
                Map.of("language", "en")
        ).join();

        assertTrue(result.isPresent());
        assertEquals("1:en", result.get());
        assertEquals(List.of("topic"), builder.lastArgs);
        assertEquals(Map.of("language", "en"), builder.lastKwargs);
    }

    @Test
    void noArgBuildMirrorsEmptyPythonArgsAndKwargs() {
        Model.registerInvoker(ProviderType.OPEN_AI.getValue(), (messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        RecordingPromptBuilder builder = new RecordingPromptBuilder(
                ModelRequestConfig.builder().modelName("unit-model").build(),
                ModelClientConfig.builder().clientProvider(ProviderType.OPEN_AI).build());

        assertEquals(Optional.of("0:missing"), builder.build().join());
        assertTrue(builder.lastArgs.isEmpty());
        assertTrue(builder.lastKwargs.isEmpty());
    }

    private static final class RecordingPromptBuilder extends BasePromptBuilder {
        private List<Object> lastArgs = List.of();
        private Map<String, Object> lastKwargs = Map.of();

        private RecordingPromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
            super(modelConfig, modelClientConfig);
        }

        @Override
        public CompletableFuture<Optional<String>> build(List<Object> args, Map<String, Object> kwargs) {
            lastArgs = List.copyOf(args);
            lastKwargs = Map.copyOf(kwargs);
            return CompletableFuture.completedFuture(Optional.of(args.size() + ":"
                    + kwargs.getOrDefault("language", "missing")));
        }

        @Override
        public Flow.Publisher<?> streamBuild(List<Object> args, Map<String, Object> kwargs) {
            SubmissionPublisher<Object> publisher = new SubmissionPublisher<>();
            publisher.close();
            return publisher;
        }
    }
}
