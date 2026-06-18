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
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Mirrors Python's {@code BadCasePromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/badcase_prompt_builder.py}.
 */
class BadCasePromptBuilderTest {

    @Test
    void parseFeedbackSummaryReturnsLastSummaryOrOriginalContent() {
        BadCasePromptBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        assertThat(builder.parseFeedbackSummary(new AssistantMessage(
                "<intent>true</intent><summary>first</summary><summary>last</summary>"
        ))).isEqualTo("last");
        assertThat(builder.parseFeedbackSummary(new AssistantMessage("plain response")))
                .isEqualTo("plain response");
        assertThat(builder.parseFeedbackSummary(new AssistantMessage(
                "<intent>false</intent><summary>needs clearer constraint</summary>"
        ))).isEqualTo("needs clearer constraint");
    }

    @Test
    void buildBadCaseStringFormatsCasesWithPythonLikeMapString() {
        BadCasePromptBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        String result = builder.buildBadCaseString(List.of(evaluatedCase()));

        assertThat(result).contains("[question]: {'question': 'Q'}");
        assertThat(result).contains("[expected answer]: {'answer': 'A'}");
        assertThat(result).contains("[assistant answer]: {'answer': 'B'}");
        assertThat(result).contains("[reason]: mismatch");
    }

    @Test
    void validateInputRaisesPythonStatusCodes() {
        BadCasePromptBuilder builder = builderWith(new RecordingClient(List.of(), List.of()));

        assertThatThrownBy(() -> builder.validateInput(null, List.of(evaluatedCase())))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR));
        assertThatThrownBy(() -> builder.validateInput(" ", List.of(evaluatedCase())))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR));
        assertThatThrownBy(() -> builder.validateInput("prompt", List.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("The cases cannot be empty");
        assertThatThrownBy(() -> builder.validateInput("prompt", elevenCases()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("The number of cases cannot exceed 10");
    }

    @Test
    void buildAnalyzesBadCasesThenInvokesOptimizeTemplate() {
        RecordingClient client = new RecordingClient(List.of(
                new AssistantMessage("<intent>true</intent><summary>make instructions stricter</summary>"),
                new AssistantMessage("optimized prompt")
        ), List.of());
        BadCasePromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build("original prompt", List.of(evaluatedCase()), "en-US").join();

        assertThat(result).contains("optimized prompt");
        assertThat(client.capturedInvokes()).hasSize(2);
        assertThat(client.capturedInvokes().get(0).getFirst().getContentAsString())
                .contains("<bad_cases>")
                .contains("{'question': 'Q'}");
        assertThat(client.capturedInvokes().get(1).getFirst().getContentAsString())
                .contains("<feedback>")
                .contains("make instructions stricter")
                .contains("<original_prompt>");
    }

    @Test
    void dynamicBuildReadsArgsAndKwargsLikeBaseInterface() {
        RecordingClient client = new RecordingClient(List.of(
                new AssistantMessage("<summary>feedback</summary>"),
                new AssistantMessage("optimized")
        ), List.of());
        BadCasePromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build(
                List.of("prompt", List.of(evaluatedCase())),
                Map.of("language", "zh-CN")
        ).join();

        assertThat(result).contains("optimized");
    }

    @Test
    void streamBuildPublishesChunkContentInOrder() throws Exception {
        RecordingClient client = new RecordingClient(
                List.of(new AssistantMessage("<summary>feedback</summary>")),
                List.of(chunk("part-1"), chunk("part-2"))
        );
        BadCasePromptBuilder builder = builderWith(client);
        CollectingSubscriber subscriber = new CollectingSubscriber();

        builder.streamBuild("prompt", List.of(evaluatedCase())).subscribe(subscriber);

        assertThat(subscriber.await()).isTrue();
        assertThat(subscriber.items()).containsExactly("part-1", "part-2");
        assertThat(subscriber.error()).isNull();
    }

    @Test
    void typedBuildAcceptsPromptTemplateInput() {
        RecordingClient client = new RecordingClient(List.of(
                new AssistantMessage("<summary>feedback</summary>"),
                new AssistantMessage("optimized")
        ), List.of());
        BadCasePromptBuilder builder = builderWith(client);

        Optional<String> result = builder.build(
                com.openjiuwen.core.foundation.prompt.PromptTemplate.builder().content("template prompt").build(),
                List.of(evaluatedCase())
        ).join();

        assertThat(result).contains("optimized");
        assertThat(client.capturedInvokes().get(0).getFirst().getContentAsString())
                .contains("template prompt");
    }

    private static BadCasePromptBuilder builderWith(RecordingClient client) {
        Model.registerClientFactory(ProviderType.OPEN_AI.getValue(), (modelClientConfig, modelConfig) -> client);
        return new BadCasePromptBuilder(
                ModelRequestConfig.builder().modelName("unit-model").build(),
                ModelClientConfig.builder().clientProvider(ProviderType.OPEN_AI).apiKey("test").build());
    }

    private static EvaluatedCase evaluatedCase() {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("question", "Q");
        Map<String, Object> label = new LinkedHashMap<>();
        label.put("answer", "A");
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("answer", "B");
        return new EvaluatedCase(new Case(inputs, label), answer, 0.0d, "mismatch");
    }

    private static List<EvaluatedCase> elevenCases() {
        List<EvaluatedCase> result = new ArrayList<>();
        for (int i = 0; i < BadCasePromptBuilder.MAX_CASES_LIMIT + 1; i++) {
            result.add(evaluatedCase());
        }
        return result;
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
