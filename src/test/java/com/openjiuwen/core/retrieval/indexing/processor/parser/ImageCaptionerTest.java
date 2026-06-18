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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ImageCaptioner} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
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
    void captionImagesPreservesOrderAndUsesEmptyCaptionForMissingFiles() throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.png"), "first");
        Path second = Files.writeString(tempDir.resolve("second.jpg"), "second");
        RecordingModelClient client = new RecordingModelClient();
        ImageCaptioner captioner = new ImageCaptioner(new Model(client));

        List<String> captions = captioner.captionImages(List.of(
                first.toString(),
                tempDir.resolve("missing.png").toString(),
                second.toString()
        )).join();

        assertThat(captions).containsExactly("caption text", "", "caption text");
        assertThat(client.invokeCount).isEqualTo(2);
    }

    /**
     * Mirrors Python's injected {@code Model} collaborator in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/captioner.py}.
     */
    private static final class RecordingModelClient implements Model.ModelClient {
        private List<BaseMessage> messages = List.of();
        private int invokeCount;

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            this.messages = messages;
            invokeCount++;
            return CompletableFuture.completedFuture(new AssistantMessage("caption text"));
        }
    }
}
