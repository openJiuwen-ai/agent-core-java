/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code MetaTemplateBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/meta_template_builder.py}.
 */
class MetaTemplateBuilderTest {

    @Test
    void registerGetAndPopCustomTemplates() {
        MetaTemplateBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        builder.registerMetaTemplate("custom_general", "this is a string meta template");
        Optional<PromptTemplate> stringTemplate = builder.getMetaTemplate("META_TEMPLATE_custom_general");
        assertThat(stringTemplate).isPresent();
        assertThat(stringTemplate.orElseThrow().getContent()).isEqualTo("this is a string meta template");
        assertThat(builder.popMetaTemplate("META_TEMPLATE_custom_general")).isPresent();

        PromptTemplate promptTemplate = PromptTemplate.builder().content("this is a prompt template").build();
        builder.registerMetaTemplate("custom_general", promptTemplate);
        Optional<PromptTemplate> copiedTemplate = builder.getMetaTemplate("META_TEMPLATE_custom_general");
        assertThat(copiedTemplate).isPresent();
        assertThat(copiedTemplate.orElseThrow().getContent()).isEqualTo(promptTemplate.getContent());

        assertThatThrownBy(() -> builder.registerMetaTemplate("custom_general", List.of("invalid")))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR));
    }

    @Test
    void buildUsesDefaultGeneralTemplate() {
        RecordingClient client = new RecordingClient(List.of(), List.of());
        MetaTemplateBuilder builder = builderWith(client);

        Optional<String> result = builder.build("travel assistant").join();

        String expected = PromptZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE.toMessages()
                .getFirst()
                .getContentAsString()
                + PromptZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE.format(Map.of(
                "instruction",
                "travel assistant",
                "tools",
                "None"
        )).toMessages().getFirst().getContentAsString();
        assertThat(result.orElseThrow()).contains(expected);
        assertThat(client.capturedInvokes()).hasSize(1);
    }

    @Test
    void buildUsesPlanTemplateAndPythonNoneForMissingTools() {
        MetaTemplateBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        Optional<String> result = builder.build(
                "travel assistant",
                null,
                "plan",
                null,
                "zh-CN"
        ).join();

        String expected = PromptZh.PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE.toMessages()
                .getFirst()
                .getContentAsString()
                + PromptZh.PROMPT_BUILD_PLAN_META_USER_TEMPLATE.format(Map.of(
                "instruction",
                "travel assistant",
                "tools",
                "None"
        )).toMessages().getFirst().getContentAsString();
        assertThat(result.orElseThrow()).contains(expected);
    }

    @Test
    void buildInvalidTypeFallsBackToGeneralTemplate() {
        MetaTemplateBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        Optional<String> result = builder.build(
                "travel assistant",
                null,
                "bad-type",
                null,
                "zh-CN"
        ).join();

        assertThat(result.orElseThrow()).contains(
                PromptZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE.toMessages().getFirst().getContentAsString());
    }

    @Test
    void customTemplateRequiresRegisteredTemplateName() {
        MetaTemplateBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        assertThatThrownBy(() -> builder.build(
                "travel assistant",
                null,
                "other",
                null,
                "zh-CN"
        )).isInstanceOf(BaseError.class);

        builder.registerMetaTemplate("custom_general", "{{instruction}} :: {{tools}}");
        assertThatThrownBy(() -> builder.build(
                "travel assistant",
                null,
                "other",
                "not_defined",
                "zh-CN"
        )).isInstanceOf(BaseError.class);

        Optional<String> result = builder.build(
                "travel assistant",
                null,
                "other",
                "custom_general",
                "zh-CN"
        ).join();
        assertThat(result.orElseThrow()).contains("travel assistant :: None");
    }

    @Test
    void validatesPromptAndToolTypes() {
        MetaTemplateBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        assertThatThrownBy(() -> builder.isValidPrompt(null, null))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR));
        assertThatThrownBy(() -> builder.isValidPrompt(" ", null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("prompt cannot be empty");
        assertThatThrownBy(() -> builder.isValidPrompt("prompt", List.of("not-tool-info")))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("each tool must be an instance of ToolInfo");
    }

    @Test
    void dynamicBuildReadsArgsAndKwargsLikeBaseInterface() {
        MetaTemplateBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        Optional<String> result = builder.build(
                List.of("prompt"),
                Map.of("template_type", "general", "language", "zh-CN")
        ).join();

        assertThat(result.orElseThrow()).contains("prompt");
    }

    @Test
    void streamBuildPublishesChunkContentInOrder() throws Exception {
        RecordingClient client = new RecordingClient(
                List.of(),
                List.of(chunk("part-1"), chunk("part-2"))
        );
        MetaTemplateBuilder builder = builderWith(client);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        builder.streamBuild("prompt", null, "general", null, "zh-CN").subscribe(subscriber);

        assertThat(subscriber.await()).isTrue();
        assertThat(subscriber.items()).containsExactly("part-1", "part-2");
        assertThat(subscriber.error()).isNull();
    }

    private static MetaTemplateBuilder builderWith(RecordingClient client) {
        Model.registerClientFactory(ProviderType.OPEN_AI.getValue(), (modelClientConfig, modelConfig) -> client);
        return new MetaTemplateBuilder(
                ModelRequestConfig.builder().modelName("unit-model").build(),
                ModelClientConfig.builder().clientProvider(ProviderType.OPEN_AI).apiKey("test").build());
    }

    private static AssistantMessageChunk chunk(String content) {
        return AssistantMessageChunk.builder().content(content).build();
    }

    private static final class RecordingClient implements Model.ModelClient {
        private final ArrayDeque<AssistantMessage> invokeResponses;
        private final List<AssistantMessageChunk> streamChunks;
        private final List<List<BaseMessage>> capturedInvokes = new ArrayList<>();

        private RecordingClient(List<AssistantMessage> invokeResponses, List<AssistantMessageChunk> streamChunks) {
            this.invokeResponses = new ArrayDeque<>(invokeResponses);
            this.streamChunks = new ArrayList<>(streamChunks);
        }

        private List<List<BaseMessage>> capturedInvokes() {
            return capturedInvokes;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            capturedInvokes.add(new ArrayList<>(messages));
            if (!invokeResponses.isEmpty()) {
                return CompletableFuture.completedFuture(invokeResponses.removeFirst());
            }
            StringBuilder content = new StringBuilder();
            for (BaseMessage message : messages) {
                content.append(message.getContentAsString());
            }
            return CompletableFuture.completedFuture(new AssistantMessage(content.toString()));
        }

        @Override
        public java.util.Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            capturedInvokes.add(new ArrayList<>(messages));
            return streamChunks.iterator();
        }
    }

    private static final class CollectingSubscriber implements Flow.Subscriber<String> {
        private final List<String> items = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private Throwable error;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            completed.countDown();
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }

        private boolean await() throws InterruptedException {
            return completed.await(5, TimeUnit.SECONDS);
        }

        private List<String> items() {
            return items;
        }

        private Throwable error() {
            return error;
        }
    }
}
