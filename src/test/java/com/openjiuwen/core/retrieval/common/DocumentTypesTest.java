/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTypesTest {

    @TempDir
    Path tempDir;

    @Test
    void documentRequiresTextAndPreservesMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "test");

        Document doc = new Document("doc_1", "Test document", metadata);

        assertThat(doc.getId_()).isEqualTo("doc_1");
        assertThat(doc.getText()).isEqualTo("Test document");
        assertThat(doc.getMetadata()).containsExactly(Map.entry("source", "test"));

        assertThatThrownBy(Document::new)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("missing_text");
    }

    @Test
    void textChunkMirrorsDefaultsAndFactoryBehavior() {
        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1");
        assertThat(chunk.getId_()).isEqualTo("chunk_1");
        assertThat(chunk.getText()).isEqualTo("Test chunk");
        assertThat(chunk.getDocId()).isEqualTo("doc_1");
        assertThat(chunk.getMetadata()).isEmpty();
        assertThat(chunk.getEmbedding()).isNull();

        Document doc = new Document("doc_1", "Test document", Map.of("source", "test"));
        TextChunk derived = TextChunk.fromDocument(doc, "Chunk text");
        assertThat(derived.getId_()).isNotBlank();
        assertThat(derived.getDocId()).isEqualTo("doc_1");
        assertThat(derived.getMetadata()).containsExactly(Map.entry("source", "test"));

        assertThatThrownBy(TextChunk::new)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("missing_required_fields");
    }

    @Test
    void multimodalDocumentSupportsMixedContentAndCopiesCaches() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", "Hello");
        doc.addField("image", "https://example.com/a.png");
        doc.addField("video", "https://example.com/v.mp4");

        List<Map<String, Object>> content = doc.getContent();
        assertThat(content).hasSize(3);
        assertThat(content.get(0)).containsExactly(
                Map.entry("type", "text"),
                Map.entry("text", "Hello")
        );
        assertThat(content.get(1).get("uuid")).isNotNull();

        Map<String, Object> dashscope = doc.getDashscopeInput();
        assertThat(dashscope).containsExactly(
                Map.entry("text", "Hello"),
                Map.entry("video", "https://example.com/v.mp4"),
                Map.entry("image", "https://example.com/a.png")
        );
        dashscope.put("text", "mutated");
        assertThat(doc.getDashscopeInput().get("text")).isEqualTo("Hello");
    }

    @Test
    void multimodalDocumentLoadsTextAndMediaFiles(@TempDir Path localTemp) throws Exception {
        Path textFile = localTemp.resolve("note.txt");
        Files.writeString(textFile, "Hello from file", StandardCharsets.UTF_8);
        Path imageFile = localTemp.resolve("image.png");
        Files.write(imageFile, new byte[]{1, 2, 3});
        Path audioFile = localTemp.resolve("sound.wav");
        Files.write(audioFile, new byte[]{4, 5, 6});

        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("text", null, textFile, "");
        doc.addField("image", null, imageFile, "");
        doc.addField("audio", null, audioFile, "");

        assertThat(doc.getContent().get(0).get("text")).isEqualTo("Hello from file");
        assertThat(((Map<?, ?>) doc.getContent().get(1).get("image_url")).get("url")).asString().startsWith("data:image/png;base64,");
        assertThat(((Map<?, ?>) doc.getContent().get(2).get("input_audio")).get("data")).asString().startsWith("data:audio/wav;base64,");
    }

    @Test
    void multimodalDocumentRejectsInvalidSourcesAndDashscopeUnsupportedModes() {
        MultimodalDocument doc = new MultimodalDocument();

        assertThatThrownBy(() -> doc.addField("invalid", "x"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("unknown_kind");
        assertThatThrownBy(() -> doc.addField("image"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("no_image_source_provided");
        assertThatThrownBy(() -> doc.addField("audio", "https://example.com/a.wav"))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("invalid_audio_data_provided");
        assertThatThrownBy(() -> doc.addField("image", "https://example.com/a.png", tempDir.resolve("x.png"), ""))
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("too_many_image_source_provided");

        MultimodalDocument audioDoc = new MultimodalDocument();
        audioDoc.addField("audio", "data:audio/wav;base64,AAAA");
        assertThatThrownBy(audioDoc::getDashscopeInput)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("unsupported_format");

        MultimodalDocument videoDoc = new MultimodalDocument();
        videoDoc.addField("video", "data:video/mp4;base64,AAAA");
        assertThatThrownBy(videoDoc::getDashscopeInput)
                .isInstanceOf(ValidationError.class)
                .hasMessageContaining("only support video in url format");
    }

    @Test
    void multimodalDocumentSupportsDashscopeMultiImagesAndStrip() {
        MultimodalDocument doc = new MultimodalDocument();
        doc.addField("image", "https://example.com/a.png");
        doc.addField("image", "https://example.com/b.png");

        assertThat(doc.getDashscopeInput()).containsExactly(
                Map.entry("multi_images", List.of("https://example.com/a.png", "https://example.com/b.png"))
        );
        assertThat(doc.strip()).isSameAs(doc);
        assertThat(new MultimodalDocument().strip()).isNull();
    }
}
