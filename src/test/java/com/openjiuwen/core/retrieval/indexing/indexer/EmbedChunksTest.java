/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedChunksTest {

    @TempDir
    Path tempDir;

    @Test
    void fallsBackToTextOnlyWhenModelHasNoMultimodalSupport() throws Exception {
        RecordingEmbedding embedding = new RecordingEmbedding();
        Path image = Files.writeString(tempDir.resolve("img.png"), "png");
        List<TextChunk> chunks = List.of(
                chunk("c1", "caption", "d1", Map.of("image_path", image.toString())),
                chunk("c2", "plain", "d1", Map.of())
        );

        EmbedChunks.computeChunkEmbeddings(chunks, embedding, BaseCallback.class, false).join();

        assertThat(embedding.embedDocumentsCalls).hasSize(1);
        assertThat(embedding.lastTexts).containsExactly("caption", "plain");
        assertThat(embedding.lastKwargs).containsEntry("callback_cls", BaseCallback.class);
        assertThat(embedding.multimodalDocuments).isEmpty();
        assertThat(chunks.get(0).getEmbedding()).containsExactly(1.0, 2.0);
        assertThat(chunks.get(1).getEmbedding()).containsExactly(3.0, 4.0);
    }

    @Test
    void usesMultimodalForExistingImagePathsAndBatchesRemainingText() throws Exception {
        MultimodalRecordingEmbedding embedding = new MultimodalRecordingEmbedding();
        Path image = Files.writeString(tempDir.resolve("img.png"), "png");
        List<TextChunk> chunks = new ArrayList<>();
        chunks.add(chunk("c1", "image text", "d1", Map.of("image_path", image.toString())));
        chunks.add(chunk("c2", "plain text", "d1", Map.of()));

        EmbedChunks.computeChunkEmbeddings(chunks, embedding, null, false).join();

        assertThat(embedding.multimodalDocuments).hasSize(1);
        assertThat(embedding.multimodalDocuments.get(0).getContent()).hasSize(2);
        assertThat(embedding.lastTexts).containsExactly("plain text");
        assertThat(chunks.get(0).getEmbedding()).containsExactly(9.0, 8.0);
        assertThat(chunks.get(1).getEmbedding()).containsExactly(1.0, 2.0);
    }

    @Test
    void useCaptionForImagesForcesTextOnlyEvenWhenMultimodalExists() throws Exception {
        AsyncMultimodalEmbedding embedding = new AsyncMultimodalEmbedding();
        Path image = Files.writeString(tempDir.resolve("img.png"), "png");
        List<TextChunk> chunks = List.of(
                chunk("c1", "caption only", "d1", Map.of("image_path", image.toString()))
        );

        EmbedChunks.computeChunkEmbeddings(chunks, embedding, null, true).join();

        assertThat(embedding.multimodalCallCount).isZero();
        assertThat(embedding.lastTexts).containsExactly("caption only");
        assertThat(chunks.get(0).getEmbedding()).containsExactly(1.0, 2.0);
    }

    private static TextChunk chunk(String id, String text, String docId, Map<String, Object> metadata) {
        return new TextChunk(id, text, docId, new LinkedHashMap<>(metadata), null);
    }

    private static class RecordingEmbedding extends Embedding {
        final List<List<String>> embedDocumentsCalls = new ArrayList<>();
        final List<MultimodalDocument> multimodalDocuments = new ArrayList<>();
        List<String> lastTexts = List.of();
        Map<String, Object> lastKwargs = Map.of();

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs) {
            embedDocumentsCalls.add(new ArrayList<>(texts));
            lastTexts = new ArrayList<>(texts);
            lastKwargs = new LinkedHashMap<>(kwargs);
            List<List<Double>> embeddings = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                embeddings.add(List.of((double) (i * 2 + 1), (double) (i * 2 + 2)));
            }
            return CompletableFuture.completedFuture(embeddings);
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class MultimodalRecordingEmbedding extends RecordingEmbedding {
        public List<Float> embedMultimodal(MultimodalDocument document) {
            multimodalDocuments.add(document);
            return List.of(9.0f, 8.0f);
        }
    }

    private static final class AsyncMultimodalEmbedding extends RecordingEmbedding {
        int multimodalCallCount;

        public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document) {
            multimodalCallCount++;
            multimodalDocuments.add(document);
            return CompletableFuture.completedFuture(List.of(7.0, 6.0));
        }
    }
}
