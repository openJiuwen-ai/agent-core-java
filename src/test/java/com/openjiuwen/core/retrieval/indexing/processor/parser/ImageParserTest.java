/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ImageParser} behavior covered from
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.
 */
class ImageParserTest {

    @TempDir
    Path tempDir;

    @Test
    void initCreatesParser() {
        ImageParser parser = new ImageParser();

        assertThat(parser).isNotNull();
    }

    @Test
    void parseImageSuccessReturnsCaptionAndSavedImagePath() throws Exception {
        Path image = Files.write(tempDir.resolve("sample.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        String savedPath = "/images/saved_cat.png";
        String caption = "A cat sitting on a mat";
        ImageParser parser = new TestableImageParser(new StubCaptioner(List.of(caption), false), savedPath);

        List<Document> documents = parser.parse(image.toString(), "doc_img_1", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getId_()).isEqualTo("doc_img_1");
        assertThat(documents.getFirst().getText()).contains(caption);
        assertThat(documents.getFirst().getMetadata()).containsEntry("image_path", savedPath);
    }

    @Test
    void parseImageExposesImagePathInMetadata() throws Exception {
        Path image = Files.write(tempDir.resolve("photo.jpg"), new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        String expectedImagePath = "/kb/images/photo.jpg";
        ImageParser parser = new TestableImageParser(new StubCaptioner(List.of("A photo"), false), expectedImagePath);

        List<Document> documents = parser.parse(image.toString(), "img_doc", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getMetadata()).containsExactly(Map.entry("image_path", expectedImagePath));
    }

    @Test
    void parseImageMultipleCaptionsJoinsNonEmptyCaptions() throws Exception {
        Path image = Files.write(tempDir.resolve("sample.jpg"), new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        ImageParser parser = new TestableImageParser(
                new StubCaptioner(List.of("A dog", "", "Running in a park"), false),
                "/images/dog.jpg"
        );

        List<Document> documents = parser.parse(image.toString(), "doc_img_2", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getId_()).isEqualTo("doc_img_2");
        assertThat(documents.getFirst().getText()).isEqualTo("A dog\nRunning in a park");
        assertThat(documents.getFirst().getMetadata()).containsEntry("image_path", "/images/dog.jpg");
    }

    @Test
    void parseImageFileNotFoundReturnsEmptyList() {
        ImageParser parser = new ImageParser();

        List<Document> documents = parser.parse(tempDir.resolve("nonexistent_image.png").toString(), "doc_img_nf")
                .join();

        assertThat(documents).isEmpty();
    }

    @Test
    void parseImageWithCaptionExceptionReturnsDocumentWithEmptyTextAndImagePath() throws Exception {
        Path image = Files.write(tempDir.resolve("sample.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        String savedPath = "/images/tmp.png";
        ImageParser parser = new TestableImageParser(new StubCaptioner(List.of(), true), savedPath);

        List<Document> documents = parser.parse(image.toString(), "doc_img_exc", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).isEmpty();
        assertThat(documents.getFirst().getMetadata()).containsEntry("image_path", savedPath);
    }

    @Test
    void supportsRegisteredImageExtensions() {
        ImageParser parser = new ImageParser();

        assertThat(parser.supports("sample.PNG")).isTrue();
        assertThat(parser.supports("sample.txt")).isFalse();
    }

    /**
     * Mirrors Python's injected captioner test seam for {@code ImageParser} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.
     */
    private static final class TestableImageParser extends ImageParser {
        private final ImageCaptioner captioner;
        private final String savedPath;

        private TestableImageParser(ImageCaptioner captioner, String savedPath) {
            this.captioner = captioner;
            this.savedPath = savedPath;
        }

        @Override
        protected ImageCaptioner createImageCaptioner(BaseModelClient llmClient) {
            return captioner;
        }

        @Override
        protected String copyImage(ImageCaptioner imageCaptioner, String imagePath) {
            return savedPath;
        }
    }

    /**
     * Mirrors Python's patched {@code ImageCaptioner} collaborator for {@code ImageParser} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/image_parser.py}.
     */
    private static final class StubCaptioner extends ImageCaptioner {
        private final List<String> captions;
        private final boolean throwOnCaption;

        private StubCaptioner(List<String> captions, boolean throwOnCaption) {
            super((BaseModelClient) null);
            this.captions = captions;
            this.throwOnCaption = throwOnCaption;
        }

        @Override
        public CompletableFuture<List<String>> captionImages(List<String> imageLocs) {
            if (throwOnCaption) {
                return CompletableFuture.failedFuture(new IllegalStateException("Captioning error"));
            }
            return CompletableFuture.completedFuture(captions);
        }
    }
}
