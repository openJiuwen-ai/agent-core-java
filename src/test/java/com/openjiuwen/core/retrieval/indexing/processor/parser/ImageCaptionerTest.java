/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ImageCaptioner} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
 *
 * <p>Mirrors Python's {@code TestImageCaptioner} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_captioner.py}.</p>
 */
class ImageCaptionerTest {

    @TempDir
    Path tempDir;

    @Test
    void cpImageCopiesFileAndFailsForMissingSource() throws Exception {
        Path image = Files.writeString(tempDir.resolve("source.jfif"), "image");
        Path target = tempDir.resolve("images");

        String copied = ImageCaptioner.cpImage(image.toString(), target.toString());

        assertThat(Path.of(copied)).exists();
        assertThat(Path.of(copied).getFileName().toString()).isEqualTo("source.jfif");
        assertThatThrownBy(() -> ImageCaptioner.cpImage(tempDir.resolve("missing.png").toString(), target.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Image not found at");
    }

    @Test
    void llmCallReturnsEmptyWhenClientIsMissing() throws Exception {
        Path image = Files.writeString(tempDir.resolve("image.png"), "image");

        assertThat(new ImageCaptioner().llmCallAsync(image.toString()).join()).isEmpty();
    }

    @Test
    void llmCallBuildsMultimodalMessageAndUsesResponseContent() throws Exception {
        Path image = Files.writeString(tempDir.resolve("image.unknown"), "image");
        RecordingModelClient client = new RecordingModelClient();
        ImageCaptioner captioner = new ImageCaptioner(new Model(
                client,
                null,
                ModelRequestConfig.builder().modelName("gpt-4o-mini").build()
        ));

        String caption = captioner.llmCallAsync(image.toString()).join();

        assertThat(caption).isEqualTo("caption text");
        assertThat(client.messages).hasSize(1);
        Object content = client.messages.getFirst().getContent();
        assertThat(content).isInstanceOf(List.class);
        List<?> contentItems = (List<?>) content;
        assertThat(contentItems).hasSize(2);
        assertThat(contentItems.getFirst())
                .isInstanceOf(Map.class)
                .asString()
                .contains("semantic retrieval");
        assertThat(contentItems.get(1).toString())
                .contains("image_url")
                .contains("data:image/png;base64,");
    }

    @Test
    void captionImagesCallsLlmForEachImage() throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.png"), "first");
        Path second = Files.writeString(tempDir.resolve("second.jpg"), "second");
        RecordingModelClient client = new RecordingModelClient("caption for first.png", "caption for second.jpg");
        ImageCaptioner captioner = new ImageCaptioner(new Model(client));

        List<String> captions = captioner.captionImages(List.of(first.toString(), second.toString())).join();

        assertThat(captions).containsExactly("caption for first.png", "caption for second.jpg");
        assertThat(client.invokeCount).isEqualTo(2);
    }

    @Test
    void captionImagesHandlesLlmExceptionsAndContinues() throws Exception {
        Path first = Files.writeString(tempDir.resolve("good.png"), "first");
        Path bad = Files.writeString(tempDir.resolve("bad.png"), "bad");
        Path third = Files.writeString(tempDir.resolve("good2.png"), "third");
        RecordingModelClient client = new RecordingModelClient(
                CompletableFuture.completedFuture(new AssistantMessage("ok:good.png")),
                CompletableFuture.failedFuture(new RuntimeException("boom")),
                CompletableFuture.completedFuture(new AssistantMessage("ok:good2.png"))
        );
        ImageCaptioner captioner = new ImageCaptioner(new Model(client));

        List<String> captions = captioner.captionImages(List.of(first.toString(), bad.toString(), third.toString()))
                .join();

        assertThat(captions).hasSize(3);
        assertThat(captions.get(0)).startsWith("ok:good.png");
        assertThat(captions.get(1)).isEmpty();
        assertThat(captions.get(2)).startsWith("ok:good2.png");
        assertThat(client.invokeCount).isEqualTo(3);
    }

    /**
     * Mirrors Python's injected {@code Model} collaborator in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
     */
    private static final class RecordingModelClient implements Model.ModelClient {
        private List<BaseMessage> messages = List.of();
        private int invokeCount;
        private final Deque<CompletionStage<AssistantMessage>> responses = new ArrayDeque<>();

        private RecordingModelClient() {
        }

        private RecordingModelClient(String... captions) {
            for (String caption : captions) {
                responses.add(CompletableFuture.completedFuture(new AssistantMessage(caption)));
            }
        }

        @SafeVarargs
        private RecordingModelClient(CompletionStage<AssistantMessage>... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            this.messages = messages;
            invokeCount++;
            if (responses.isEmpty()) {
                return CompletableFuture.completedFuture(new AssistantMessage("caption text"));
            }
            return responses.removeFirst();
        }
    }
}
