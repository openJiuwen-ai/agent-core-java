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
 * Mirrors Python's {@code FeedbackPromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/feedback_prompt_builder.py}.
 *
 * <p>Mirrors Python's {@code test_feedback_prompt_builder} in
 * {@code tests/unit_tests/agent_builder/prompt_builder/test_feedback_prompt_builder.py}.</p>
 */
class FeedbackPromptBuilderTest {
    private static final String MOCK_INTENT = "```json{\"intent\": \"true\","
            + "\"optimized_feedback\": \"optimized feedback\","
            + "\"optimization_directions\": \"other direction\"}```";

    @Test
    void buildGeneralUsesOriginalFeedbackWithoutIntentValidation() {
        RecordingClient client = new RecordingClient(List.of(new AssistantMessage("general result")), List.of());
        FeedbackPromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build("original prompt", "raw feedback", "general").join();

        assertThat(result).contains("general result");
        assertThat(client.capturedInvokes()).hasSize(1);
        assertThat(client.capturedInvokes().getFirst().getFirst().getContentAsString())
                .contains("original prompt")
                .contains("raw feedback");
    }

    @Test
    void buildInsertOptimizesFeedbackAndTagsInsertionPosition() {
        RecordingClient client = new RecordingClient(List.of(
                new AssistantMessage(MOCK_INTENT),
                new AssistantMessage("insert result")
        ), List.of());
        FeedbackPromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build("abcdef", "raw feedback", "insert", 3).join();

        assertThat(result).contains("insert result");
        assertThat(client.capturedInvokes()).hasSize(2);
        assertThat(client.capturedInvokes().get(1).getFirst().getContentAsString())
                .contains("abc" + FeedbackPromptBuilder.INSERT_STR + "def")
                .contains("optimized feedback");
    }

    @Test
    void buildSelectOptimizesFeedbackAndUsesSelectedPromptSegment() {
        RecordingClient client = new RecordingClient(List.of(
                new AssistantMessage(MOCK_INTENT),
                new AssistantMessage("select result")
        ), List.of());
        FeedbackPromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build("abcdef", "raw feedback", "select", 1, 4).join();

        assertThat(result).contains("select result");
        assertThat(client.capturedInvokes()).hasSize(2);
        assertThat(client.capturedInvokes().get(1).getFirst().getContentAsString())
                .contains("abcdef")
                .contains("bcd")
                .contains("optimized feedback");
    }

    @Test
    void invalidModeFallsBackToGeneralTemplate() {
        RecordingClient client = new RecordingClient(List.of(new AssistantMessage("fallback result")), List.of());
        FeedbackPromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build("prompt", "feedback", "unknown-mode").join();

        assertThat(result).contains("fallback result");
        assertThat(client.capturedInvokes()).hasSize(1);
        assertThat(client.capturedInvokes().getFirst().getFirst().getContentAsString())
                .contains("prompt")
                .contains("feedback");
    }

    @Test
    void validatesPromptFeedbackAndIndexBoundsWithPythonStatusCode() {
        FeedbackPromptBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        assertThatThrownBy(() -> builder.isValidPrompt(null, "feedback"))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR));
        assertThatThrownBy(() -> builder.isValidPrompt("prompt", " "))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("prompt or feedback cannot be empty");
        assertThatThrownBy(() -> builder.isIndexWithinBounds("abc", "insert", null, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("start_pos must be provided for int type");
        assertThatThrownBy(() -> builder.isIndexWithinBounds("abc", "select", 2, 2))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("0 <= start_pos < end_pos <= len(prompt)");
    }

    @Test
    void extractsIntentJsonAndFallsBackWhenIntentIsInvalid() {
        FeedbackPromptBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        FeedbackPromptBuilder.IntentResult valid = builder.extractIntentFromResponses(MOCK_INTENT);
        FeedbackPromptBuilder.IntentResult boolValid = builder.extractIntentFromResponses(
                "```json{\"intent\": true, \"optimized_feedback\": \" text \"}```"
        );
        FeedbackPromptBuilder.IntentResult invalid = builder.extractIntentFromResponses(
                "```json{\"intent\": \"false\", \"optimized_feedback\": \"ignored\"}```"
        );
        FeedbackPromptBuilder.IntentResult missing = builder.extractIntentFromResponses("plain text");

        assertThat(valid.intent()).isTrue();
        assertThat(valid.optimizedFeedback()).isEqualTo("optimized feedback");
        assertThat(boolValid.intent()).isTrue();
        assertThat(boolValid.optimizedFeedback()).isEqualTo("text");
        assertThat(invalid.intent()).isFalse();
        assertThat(missing.intent()).isFalse();
    }

    @Test
    void invalidIntentResponseUsesOriginalFeedback() {
        RecordingClient client = new RecordingClient(List.of(
                new AssistantMessage("not json"),
                new AssistantMessage("insert result")
        ), List.of());
        FeedbackPromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build("abcdef", "raw feedback", "insert", 3).join();

        assertThat(result).contains("insert result");
        assertThat(client.capturedInvokes().get(1).getFirst().getContentAsString())
                .contains("raw feedback");
    }

    @Test
    void dynamicBuildReadsArgsAndKwargsLikeBaseInterface() {
        RecordingClient client = new RecordingClient(List.of(new AssistantMessage("dynamic result")), List.of());
        FeedbackPromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build(
                List.of("prompt"),
                Map.of("feedback", "feedback", "mode", "general", "language", "zh-CN")
        ).join();

        assertThat(result).contains("dynamic result");
    }

    @Test
    void streamBuildPublishesChunkContentInOrder() throws Exception {
        RecordingClient client = new RecordingClient(
                List.of(new AssistantMessage(MOCK_INTENT)),
                List.of(chunk("part-1"), chunk("part-2"))
        );
        FeedbackPromptBuilder builder = builderWith(client);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        builder.streamBuild("abcdef", "feedback", "insert", 3, null, "zh-CN").subscribe(subscriber);

        assertThat(subscriber.await()).isTrue();
        assertThat(subscriber.items()).containsExactly("part-1", "part-2");
        assertThat(subscriber.error()).isNull();
    }

    private static FeedbackPromptBuilder builderWith(RecordingClient client) {
        Model.registerClientFactory(ProviderType.OPEN_AI.getValue(), (modelClientConfig, modelConfig) -> client);
        return new FeedbackPromptBuilder(
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
            AssistantMessage response = invokeResponses.isEmpty()
                    ? new AssistantMessage("")
                    : invokeResponses.removeFirst();
            return CompletableFuture.completedFuture(response);
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
