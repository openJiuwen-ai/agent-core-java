/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Mirrors Python's {@code TestChromaIndexer} in
 * {@code tests/unit_tests/core/retrieval/indexing/indexer/test_chroma_indexer.py}.
 */
class ChromaIndexerPythonParityTest {

    @Test
    void testInitSuccess() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);

        assertThat(indexer.getChromaPath()).isEqualTo("/tmp/test_chroma");
        assertThat(client.collection).isNull();
    }

    @Test
    void testInitWithEmptyPath() {
        BaseError error = expectBaseError(() -> newIndexer(config(), "", new RecordingChromaClient()));

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_PATH_NOT_FOUND);
        assertThat(error.getMessage()).contains("chroma_path is required");
    }

    @Test
    void testInitWithWhitespacePath() {
        BaseError error = expectBaseError(() -> newIndexer(config(), "   ", new RecordingChromaClient()));

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_PATH_NOT_FOUND);
        assertThat(error.getMessage()).contains("chroma_path is required");
    }

    @Test
    void testInitWithCustomFields() {
        ChromaIndexer indexer = new ChromaIndexer(
                config(),
                "/tmp/test_chroma",
                "custom_text",
                "custom_vector",
                "custom_sparse",
                "custom_metadata",
                "custom_doc_id",
                RecordingCallback.class,
                new RecordingChromaClient()
        );

        assertThat(indexer.getTextField()).isEqualTo("custom_text");
        assertThat(indexer.getVectorField().getVectorField()).isEqualTo("custom_vector");
        assertThat(indexer.getSparseVectorField()).isEqualTo("custom_sparse");
        assertThat(indexer.getMetadataField()).isEqualTo("custom_metadata");
        assertThat(indexer.getDocIdField()).isEqualTo("custom_doc_id");
    }

    @Test
    void testInitWithInvalidVectorField() {
        BaseError error = expectBaseError(() -> new ChromaIndexer(
                config(),
                "/tmp/test_chroma",
                "custom_text",
                Map.of("vector_field", "custom_vector"),
                "custom_sparse",
                "custom_metadata",
                "custom_doc_id",
                RecordingCallback.class,
                new RecordingChromaClient()
        ));

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_VECTOR_FIELD_INVALID);
        assertThat(error.getMessage()).contains("vector_field must be either a str or ChromaVectorField instance");
    }

    @Test
    void testBuildIndexVectorType() {
        RecordingChromaClient client = new RecordingChromaClient();
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(List.of(0.1d, 0.1d), List.of(0.2d, 0.2d)));
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);

        boolean result = indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1"), chunk("2", "chunk 2", "doc_1")),
                indexConfig("test_index", "vector"),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.documentCallCount).isEqualTo(1);
        assertThat(embedding.documentTexts).containsExactly("chunk 1", "chunk 2");
        assertThat(client.collection.rows).hasSize(2);
        assertThat(client.collection.rows.get(0)).containsEntry("id", "1");
    }

    @Test
    void testBuildIndexBm25Type() {
        RecordingChromaClient client = new RecordingChromaClient();
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(List.of(0.1d, 0.1d)));
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);

        boolean result = indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1")),
                indexConfig("test_index", "bm25"),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.documentCallCount).isZero();
        assertThat(client.collection.rows).hasSize(1);
    }

    @Test
    void testBuildIndexVectorTypeWithImagePathUsesEmbedMultimodal(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("image.png");
        Files.write(image, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        RecordingChromaClient client = new RecordingChromaClient();
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(List.of(0.2d, 0.2d)));
        embedding.multimodalVector = List.of(0.5d, 0.5d);
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        TextChunk imageChunk = chunk("1", "caption one", "doc_1", Map.of("image_path", image.toString()));
        TextChunk textChunk = chunk("2", "text only chunk", "doc_1");

        boolean result = indexer.buildIndex(
                List.of(imageChunk, textChunk),
                indexConfig("test_index", "vector"),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.multimodalCallCount).isEqualTo(1);
        assertThat(embedding.documentTexts).containsExactly("text only chunk");
        assertThat(imageChunk.getEmbedding()).containsExactly(0.5d, 0.5d);
        assertThat(textChunk.getEmbedding()).containsExactly(0.2d, 0.2d);
        assertThat(client.collection.rows).hasSize(2);
    }

    @Test
    void testBuildIndexVectorTypeWithImagePathUseCaptionForImages(@TempDir Path tempDir) throws Exception {
        Path image = tempDir.resolve("image.png");
        Files.write(image, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        RecordingChromaClient client = new RecordingChromaClient();
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(List.of(0.1d, 0.1d), List.of(0.2d, 0.2d)));
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        TextChunk imageChunk = chunk("1", "caption one", "doc_1", Map.of("image_path", image.toString()));
        TextChunk textChunk = chunk("2", "text only chunk", "doc_1");

        boolean result = indexer.buildIndex(
                List.of(imageChunk, textChunk),
                IndexConfig.builder()
                        .indexName("test_index")
                        .indexType("vector")
                        .useCaptionForImages(true)
                        .build(),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.multimodalCallCount).isZero();
        assertThat(embedding.documentTexts).containsExactly("caption one", "text only chunk");
        assertThat(imageChunk.getEmbedding()).containsExactly(0.1d, 0.1d);
        assertThat(textChunk.getEmbedding()).containsExactly(0.2d, 0.2d);
        assertThat(client.collection.rows).hasSize(2);
    }

    @Test
    void testBuildIndexHybridType() {
        RecordingChromaClient client = new RecordingChromaClient();
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(List.of(0.1d, 0.1d), List.of(0.2d, 0.2d)));
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);

        boolean result = indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1"), chunk("2", "chunk 2", "doc_1")),
                indexConfig("test_index", "hybrid"),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.documentCallCount).isEqualTo(1);
        assertThat(client.collection.rows).hasSize(2);
    }

    @Test
    void testBuildIndexVectorTypeWithoutEmbedModel() {
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", new RecordingChromaClient());

        BaseError error = expectBaseError(() -> indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1")),
                indexConfig("test_index", "vector"),
                null,
                Map.of()
        ).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND);
    }

    @Test
    void testBuildIndexException() {
        RecordingChromaClient client = new RecordingChromaClient();
        client.failOnCreate = true;
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);

        BaseError error = expectBaseError(() -> indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1")),
                indexConfig("test_index", "vector"),
                new RecordingEmbedding(List.of(List.of(0.1d, 0.1d))),
                Map.of()
        ).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR);
        assertThat(error.getMessage()).contains("ChromaDB error");
    }

    @Test
    void testBuildIndexWithDuplicateDocIds() {
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", new RecordingChromaClient());
        IndexConfig indexConfig = indexConfig("test_index", "bm25");

        assertThat(indexer.buildIndex(List.of(chunk("1", "chunk 1", "doc_1")), indexConfig, null, Map.of()).join())
                .isTrue();
        BaseError error = expectBaseError(() -> indexer.buildIndex(
                List.of(chunk("2", "chunk 2", "doc_1")),
                indexConfig,
                null,
                Map.of()
        ).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR);
        assertThat(error.getMessage()).contains("some documents with same doc_id already exist");
    }

    @Test
    void testUpdateIndex() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        IndexConfig indexConfig = indexConfig("test_index", "bm25");

        assertThat(indexer.buildIndex(List.of(chunk("1", "old chunk", "doc_1")), indexConfig, null, Map.of()).join())
                .isTrue();
        boolean result = indexer.updateIndex(
                List.of(chunk("2", "updated chunk", "doc_1")),
                "doc_1",
                indexConfig,
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(client.collection.rows).hasSize(1);
        assertThat(client.collection.rows.get(0)).containsEntry("id", "2").containsEntry("content", "updated chunk");
    }

    @Test
    void testUpdateIndexException() {
        ChromaIndexer indexer = new DeleteFailingChromaIndexer(config(), "/tmp/test_chroma");

        boolean result = indexer.updateIndex(
                List.of(chunk("1", "updated chunk", "doc_1")),
                "doc_1",
                indexConfig("test_index", "vector"),
                new RecordingEmbedding(List.of(List.of(0.1d, 0.1d))),
                Map.of()
        ).join();

        assertThat(result).isFalse();
    }

    @Test
    void testDeleteIndexSuccess() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        IndexConfig indexConfig = indexConfig("test_index", "bm25");
        indexer.buildIndex(List.of(chunk("1", "chunk 1", "doc_1"), chunk("2", "chunk 2", "doc_1")),
                indexConfig, null, Map.of()).join();

        boolean result = indexer.deleteIndex("doc_1", "test_index", Map.of()).join();

        assertThat(result).isTrue();
        assertThat(client.collection.rows).isEmpty();
    }

    @Test
    void testDeleteIndexNotFound() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        indexer.buildIndex(List.of(), indexConfig("test_index", "bm25"), null, Map.of()).join();

        boolean result = indexer.deleteIndex("doc_1", "test_index", Map.of()).join();

        assertThat(result).isFalse();
    }

    @Test
    void testDeleteIndexNoIdsKey() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        indexer.buildIndex(List.of(chunk("1", "chunk 1", "doc_1")), indexConfig("test_index", "bm25"), null, Map.of())
                .join();
        client.collection.omitIds = true;

        boolean result = indexer.deleteIndex("doc_1", "test_index", Map.of()).join();

        assertThat(result).isFalse();
        assertThat(client.collection.rows).hasSize(1);
    }

    @Test
    void testDeleteIndexException() {
        RecordingChromaClient client = new RecordingChromaClient();
        client.failOnGet = true;
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);

        boolean result = indexer.deleteIndex("doc_1", "test_index", Map.of()).join();

        assertThat(result).isFalse();
    }

    @Test
    void testIndexExistsTrue() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        indexer.buildIndex(List.of(), indexConfig("test_index", "bm25"), null, Map.of()).join();

        assertThat(indexer.indexExists("test_index").join()).isTrue();
    }

    @Test
    void testIndexExistsFalse() {
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", new RecordingChromaClient());

        assertThat(indexer.indexExists("nonexistent_index").join()).isFalse();
    }

    @Test
    void testGetIndexInfoExists() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        indexer.buildIndex(List.of(chunk("1", "chunk 1", "doc_1")), indexConfig("test_index", "bm25"), null, Map.of())
                .join();

        Map<String, Object> info = indexer.getIndexInfo("test_index").join();

        assertThat(info)
                .containsEntry("exists", true)
                .containsEntry("collection_name", "test_index")
                .containsEntry("count", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) info.get("metadata");
        assertThat(metadata.get("collection_name")).isEqualTo("test_index");
    }

    @Test
    void testGetIndexInfoNotExists() {
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", new RecordingChromaClient());

        assertThat(indexer.getIndexInfo("nonexistent_index").join()).containsEntry("exists", false);
    }

    @Test
    void testGetIndexInfoException() {
        RecordingChromaClient client = new RecordingChromaClient();
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", client);
        indexer.buildIndex(List.of(chunk("1", "chunk 1", "doc_1")), indexConfig("test_index", "bm25"), null, Map.of())
                .join();
        client.collection.failOnCount = true;

        Map<String, Object> info = indexer.getIndexInfo("test_index").join();

        assertThat(info).containsEntry("exists", false);
        assertThat(info).containsKey("error");
    }

    @Test
    void testClose() {
        ChromaIndexer indexer = newIndexer(config(), "/tmp/test_chroma", new RecordingChromaClient());

        indexer.close();
    }

    private static ChromaIndexer newIndexer(VectorStoreConfig config, String chromaPath, RecordingChromaClient client) {
        return new ChromaIndexer(
                config,
                chromaPath,
                "content",
                new ChromaVectorField(),
                "sparse_vector",
                "metadata",
                "document_id",
                RecordingCallback.class,
                client
        );
    }

    private static VectorStoreConfig config() {
        return VectorStoreConfig.builder()
                .storeProvider(StoreType.CHROMA)
                .collectionName("test_collection")
                .databaseName("db")
                .distanceMetric("cosine")
                .build();
    }

    private static IndexConfig indexConfig(String indexName, String indexType) {
        return IndexConfig.builder().indexName(indexName).indexType(indexType).build();
    }

    private static TextChunk chunk(String id, String text, String docId) {
        return chunk(id, text, docId, Map.of());
    }

    private static TextChunk chunk(String id, String text, String docId, Map<String, Object> metadata) {
        return new TextChunk(id, text, docId, metadata);
    }

    private static BaseError expectBaseError(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            Throwable unwrapped = unwrap(throwable);
            assertThat(unwrapped).isInstanceOf(BaseError.class);
            return (BaseError) unwrapped;
        }
        fail("Expected BaseError to be thrown");
        throw new AssertionError("unreachable");
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class RecordingCallback extends BaseCallback {
        private RecordingCallback(Iterable<?> seq) {
            super(seq);
        }
    }

    private static final class DeleteFailingChromaIndexer extends ChromaIndexer {
        private DeleteFailingChromaIndexer(VectorStoreConfig config, String chromaPath) {
            super(config, chromaPath);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            throw new RuntimeException("Delete error");
        }
    }

    private static final class RecordingEmbedding extends Embedding {
        private final List<List<Double>> documentVectors;
        private List<Double> multimodalVector = List.of(0.5d, 0.5d);
        private int documentCallCount;
        private int multimodalCallCount;
        private List<String> documentTexts = List.of();
        private Map<String, Object> documentKwargs = Map.of();
        private final List<MultimodalDocument> multimodalDocuments = new ArrayList<>();

        private RecordingEmbedding(List<List<Double>> documentVectors) {
            this.documentVectors = new ArrayList<>(documentVectors);
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.1d, 0.1d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            documentCallCount++;
            documentTexts = List.copyOf(texts);
            documentKwargs = new LinkedHashMap<>(kwargs);
            return CompletableFuture.completedFuture(documentVectors.subList(0, texts.size()));
        }

        public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document) {
            multimodalCallCount++;
            multimodalDocuments.add(document);
            return CompletableFuture.completedFuture(multimodalVector);
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class RecordingChromaClient implements ChromaIndexer.ChromaClientGateway {
        private RecordingChromaCollection collection;
        private boolean failOnCreate;
        private boolean failOnGet;

        @Override
        public ChromaIndexer.ChromaCollectionGateway getCollection(String name) {
            if (failOnGet) {
                throw new RuntimeException("Collection not found");
            }
            if (collection == null) {
                throw new RuntimeException("Collection not found");
            }
            return collection;
        }

        @Override
        public ChromaIndexer.ChromaCollectionGateway getOrCreateCollection(String name, Map<String, Object> metadata) {
            if (failOnCreate) {
                throw new RuntimeException("ChromaDB error");
            }
            if (collection == null) {
                collection = new RecordingChromaCollection(metadata);
            }
            return collection;
        }
    }

    private static final class RecordingChromaCollection implements ChromaIndexer.ChromaCollectionGateway {
        private final Map<String, Object> metadata;
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private boolean omitIds;
        private boolean failOnCount;

        private RecordingChromaCollection(Map<String, Object> metadata) {
            this.metadata = new LinkedHashMap<>(metadata);
        }

        @Override
        public Map<String, Object> metadata() {
            return new LinkedHashMap<>(metadata);
        }

        @Override
        public void add(List<Map<String, Object>> data) {
            for (Map<String, Object> row : data) {
                rows.add(new LinkedHashMap<>(row));
            }
        }

        @Override
        public List<String> idsWhere(Map<String, Object> where) {
            if (omitIds) {
                return List.of();
            }
            return rows.stream()
                    .filter(row -> where.entrySet().stream()
                            .allMatch(entry -> Objects.equals(row.get(entry.getKey()), entry.getValue())))
                    .map(row -> String.valueOf(row.get("id")))
                    .toList();
        }

        @Override
        public void delete(List<String> ids) {
            rows.removeIf(row -> ids.contains(String.valueOf(row.get("id"))));
        }

        @Override
        public int count() {
            if (failOnCount) {
                throw new RuntimeException("Count error");
            }
            return rows.size();
        }
    }
}
