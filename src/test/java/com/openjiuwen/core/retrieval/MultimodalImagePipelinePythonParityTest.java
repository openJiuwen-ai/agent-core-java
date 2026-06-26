/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.EmbedChunks;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageCaptioner;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser;
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

/**
 * Mirrors Python's multimodal image pipeline tests in
 * {@code tests/unit_tests/core/retrieval/test_multimodal_image_pipeline.py}.
 */
public class MultimodalImagePipelinePythonParityTest {

    @TempDir
    Path tempDir;

    @Test
    void parseReturnsDocumentWithImagePathInMetadata() throws Exception {
        Path image = jpg("photo.jpg");
        String savedPath = "/kb/images/photo.jpg";
        ImageParser parser = new TestableImageParser(new StubCaptioner(List.of("A photograph")), savedPath);

        List<Document> documents = parser.parse(image.toString(), "img_doc_1", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getMetadata()).containsEntry("image_path", savedPath);
        assertThat(documents.get(0).getText()).contains("photograph");
    }

    @Test
    void imageChunkUsesEmbedMultimodal() throws Exception {
        Path image = jpg("image.jpg");
        TextChunk chunk = chunk("c1", "A photo", "d1", Map.of("image_path", image.toString()));
        MultimodalEmbedding embedding = new MultimodalEmbedding(List.of(vector(0.2)), List.of(vector(0.1)));

        EmbedChunks.computeChunkEmbeddings(List.of(chunk), embedding, null, false).join();

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(vector(0.2));
        assertThat(embedding.multimodalDocuments).hasSize(1);
        assertThat(embedding.multimodalDocuments.get(0)).isInstanceOf(MultimodalDocument.class);
        assertThat(embedding.embedDocumentsCalls).isEmpty();
    }

    @Test
    void useCaptionForImagesUsesEmbedDocumentsOnly() throws Exception {
        Path image = jpg("caption.jpg");
        TextChunk chunk = chunk("c1", "A photo caption", "d1", Map.of("image_path", image.toString()));
        MultimodalEmbedding embedding = new MultimodalEmbedding(List.of(vector(0.4)), List.of(vector(0.3)));

        EmbedChunks.computeChunkEmbeddings(List.of(chunk), embedding, null, true).join();

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(vector(0.3));
        assertThat(embedding.embedDocumentsCalls).hasSize(1);
        assertThat(embedding.multimodalDocuments).isEmpty();
    }

    @Test
    void textOnlyChunkUsesEmbedDocuments() {
        TextChunk chunk = chunk("c1", "Plain text chunk", "d1", Map.of());
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(vector(0.5)));

        EmbedChunks.computeChunkEmbeddings(List.of(chunk), embedding, null, false).join();

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(vector(0.5));
        assertThat(embedding.embedDocumentsCalls).hasSize(1);
        assertThat(embedding.lastTexts).containsExactly("Plain text chunk");
    }

    @Test
    void modelWithoutEmbedMultimodalUsesEmbedDocumentsForAll() throws Exception {
        Path image = jpg("fallback.jpg");
        TextChunk chunk = chunk("c1", "Caption", "d1", Map.of("image_path", image.toString()));
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(vector(0.6)));

        EmbedChunks.computeChunkEmbeddings(List.of(chunk), embedding, null, false).join();

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(vector(0.6));
        assertThat(embedding.embedDocumentsCalls).hasSize(1);
        assertThat(embedding.lastTexts).containsExactly("Caption");
    }

    @Test
    void imagePathNonexistentFileTreatedAsTextOnly() {
        TextChunk chunk = chunk("c1", "Caption", "d1", Map.of("image_path", "/nonexistent/image.jpg"));
        MultimodalEmbedding embedding = new MultimodalEmbedding(List.of(vector(0.8)), List.of(vector(0.7)));

        EmbedChunks.computeChunkEmbeddings(List.of(chunk), embedding, null, false).join();

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(vector(0.7));
        assertThat(embedding.embedDocumentsCalls).hasSize(1);
        assertThat(embedding.multimodalDocuments).isEmpty();
    }

    @Test
    void mixedImageAndTextChunks() throws Exception {
        Path image = jpg("mixed.jpg");
        List<TextChunk> chunks = new ArrayList<>();
        chunks.add(chunk("c1", "Caption", "d1", Map.of("image_path", image.toString())));
        chunks.add(chunk("c2", "Text only", "d1", Map.of()));
        MultimodalEmbedding embedding = new MultimodalEmbedding(List.of(vector(0.1)), List.of(vector(0.2)));

        EmbedChunks.computeChunkEmbeddings(chunks, embedding, null, false).join();

        assertThat(chunks.get(0).getEmbedding()).containsExactlyElementsOf(vector(0.1));
        assertThat(chunks.get(1).getEmbedding()).containsExactlyElementsOf(vector(0.2));
        assertThat(embedding.multimodalDocuments).hasSize(1);
        assertThat(embedding.lastTexts).containsExactly("Text only");
    }

    @Test
    void multimodalDocumentContentHasTextAndImageUrl() throws Exception {
        Path image = jpg("content.jpg");
        MultimodalDocument document = new MultimodalDocument()
                .addField("text", "A photograph of a person")
                .addField("image", image);

        List<Map<String, Object>> content = document.getContent();

        assertThat(content).isNotEmpty();
        assertThat(content).anySatisfy(item -> {
            assertThat(item).containsEntry("type", "text");
            assertThat(item.get("text")).asString().contains("photograph");
        });
        assertThat(content).anySatisfy(item -> {
            assertThat(item).containsEntry("type", "image_url");
            @SuppressWarnings("unchecked")
            Map<String, Object> imageUrl = (Map<String, Object>) item.get("image_url");
            assertThat(imageUrl).containsKey("url");
        });
    }

    @Test
    void imageParserToComputeChunkEmbeddingsFlow() throws Exception {
        Path image = jpg("flow.jpg");
        ImageParser parser = new TestableImageParser(
                new StubCaptioner(List.of("A photograph of a person")),
                image.toString()
        );
        List<Document> documents = parser.parse(image.toString(), "img_1", null, Map.of()).join();
        Document document = documents.get(0);
        TextChunk chunk = TextChunk.fromDocument(document, document.getText(), "chunk_1");
        chunk.setMetadata(document.getMetadata());
        MultimodalEmbedding embedding = new MultimodalEmbedding(List.of(vector(0.0)), List.of());

        EmbedChunks.computeChunkEmbeddings(List.of(chunk), embedding, null, false).join();

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(vector(0.0));
        assertThat(embedding.multimodalDocuments).hasSize(1);
        List<String> textContent = embedding.multimodalDocuments.get(0).getContent().stream()
                .filter(item -> "text".equals(item.get("type")))
                .map(item -> String.valueOf(item.get("text")))
                .toList();
        assertThat(textContent).contains("A photograph of a person");
    }

    @Test
    void multimodalEmbeddingAndSimilarity() throws Exception {
        Path image = jpg("showcase.jpg");
        MultimodalDocument doc1 = new MultimodalDocument()
                .addField("text", "A photograph of a person")
                .addField("image", image);
        MultimodalDocument doc2 = new MultimodalDocument()
                .addField("text", "Picture of an octopus in ocean")
                .addField("image", image);
        MultimodalEmbedding embedding = new MultimodalEmbedding(List.of(
                List.of(1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0),
                List.of(0.9, 0.9, 0.9, 0.9, 0.1, 0.1, 0.1, 0.1),
                List.of(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0)
        ), List.of());

        List<Double> emb1 = embedding.embedMultimodal(doc1).join();
        List<Double> emb2 = embedding.embedMultimodal(doc2).join();
        List<Double> emb3 = embedding.embedMultimodal(new MultimodalDocument()
                .addField("text", "Different")
                .addField("image", image)).join();

        assertThat(cosineSimilarity(emb1, emb2)).isGreaterThan(cosineSimilarity(emb1, emb3));
        assertThat(embedding.multimodalDocuments).hasSize(3);
    }

    private Path jpg(String fileName) throws Exception {
        return Files.write(tempDir.resolve(fileName), new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
    }

    private static TextChunk chunk(String id, String text, String docId, Map<String, Object> metadata) {
        return new TextChunk(id, text, docId, new LinkedHashMap<>(metadata), null);
    }

    private static List<Double> vector(double value) {
        return List.of(value, value, value, value, value, value, value, value);
    }

    private static double cosineSimilarity(List<Double> left, List<Double> right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            dot += left.get(i) * right.get(i);
            leftNorm += left.get(i) * left.get(i);
            rightNorm += right.get(i) * right.get(i);
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

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

    private static final class StubCaptioner extends ImageCaptioner {
        private final List<String> captions;

        private StubCaptioner(List<String> captions) {
            super((BaseModelClient) null);
            this.captions = captions;
        }

        @Override
        public CompletableFuture<List<String>> captionImages(List<String> imageLocs) {
            return CompletableFuture.completedFuture(captions);
        }
    }

    public static class RecordingEmbedding extends Embedding {
        final List<List<String>> embedDocumentsCalls = new ArrayList<>();
        final List<MultimodalDocument> multimodalDocuments = new ArrayList<>();
        final List<List<Double>> documentVectors;
        List<String> lastTexts = List.of();

        private RecordingEmbedding(List<List<Double>> documentVectors) {
            this.documentVectors = new ArrayList<>(documentVectors);
        }

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
            if (!documentVectors.isEmpty()) {
                return CompletableFuture.completedFuture(documentVectors);
            }
            List<List<Double>> generated = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                generated.add(vector(i + 1.0));
            }
            return CompletableFuture.completedFuture(generated);
        }

        @Override
        public int getDimension() {
            return 8;
        }
    }

    public static final class MultimodalEmbedding extends RecordingEmbedding {
        private final List<List<Double>> multimodalVectors;
        private int multimodalIndex;

        private MultimodalEmbedding(List<List<Double>> multimodalVectors, List<List<Double>> documentVectors) {
            super(documentVectors);
            this.multimodalVectors = new ArrayList<>(multimodalVectors);
        }

        public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document) {
            multimodalDocuments.add(document);
            List<Double> vector = multimodalVectors.get(multimodalIndex++);
            return CompletableFuture.completedFuture(vector);
        }
    }
}
