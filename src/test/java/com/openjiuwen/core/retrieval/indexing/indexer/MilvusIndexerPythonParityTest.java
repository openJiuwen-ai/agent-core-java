/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Mirrors Python's {@code TestMilvusIndexer} in
 * {@code tests/unit_tests/core/retrieval/indexing/indexer/test_milvus_indexer.py}.
 */
class MilvusIndexerPythonParityTest {

    @Test
    void testInitSuccess() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        MilvusIndexer indexer = newIndexer(config("name", "test_collection"), "http://localhost:19530", null, null,
                client);

        assertThat(indexer.getMilvusUri()).isEqualTo("http://localhost:19530");
        assertThat(indexer.getClient()).isSameAs(client);
        assertThat(indexer.getDatabaseName()).isEqualTo("name");
        assertThat(indexer.getMilvusAlias()).isEqualTo(CommonUtils.createMilvusAlias(
                null, "http://localhost:19530", "", null));
    }

    @Test
    void testInitWithToken() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        MilvusIndexer indexer = newIndexer(config("name", "test_collection"), "http://localhost:19530",
                "test_token", null, client);

        assertThat(indexer.getMilvusToken()).isEqualTo("test_token");
        assertThat(indexer.getMilvusAlias()).isEqualTo(CommonUtils.createMilvusAlias(
                null, "http://localhost:19530", "", "test_token"));
    }

    @Test
    void testInitWithMilvusAlias() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        MilvusIndexer indexer = newIndexer(config("db", "test"), "http://localhost:19530", null, "idx_1_2", client);

        assertThat(indexer.getMilvusAlias()).isEqualTo("idx_1_2");
        assertThat(indexer.getDatabaseName()).isEqualTo("db");
    }

    @Test
    void testInitWithCustomFields() {
        MilvusIndexer indexer = new MilvusIndexer(
                config("", "test_collection"),
                "http://localhost:19530",
                null,
                "custom_text",
                "custom_vector",
                "sparse_vector",
                "metadata",
                "custom_doc_id",
                RecordingCallback.class,
                null,
                new RecordingMilvusClient()
        );

        assertThat(indexer.getTextField()).isEqualTo("custom_text");
        assertThat(indexer.getVectorField()).isEqualTo("custom_vector");
        assertThat(indexer.getDocIdField()).isEqualTo("custom_doc_id");
    }

    @Test
    void testInitWithInvalidVectorField() {
        BaseError error = expectBaseError(() -> new MilvusIndexer(
                config("", "test_collection"),
                "http://localhost:19530",
                null,
                "custom_text",
                Map.of("vector_field", "custom_vector"),
                "sparse_vector",
                "metadata",
                "custom_doc_id",
                RecordingCallback.class,
                null,
                new RecordingMilvusClient()
        ));

        assertThat(error.getMessage()).contains("vector_field must be either a str or MilvusVectorField instance");
    }

    @Test
    void testBuildIndexVectorType() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        RecordingEmbedding embedding = new RecordingEmbedding(List.of(List.of(0.1d, 0.1d), List.of(0.2d, 0.2d)));
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);
        List<TextChunk> chunks = List.of(chunk("1", "chunk 1", "doc_1"), chunk("2", "chunk 2", "doc_1"));

        boolean result = indexer.buildIndex(chunks, indexConfig("test_index", "vector"), embedding, Map.of()).join();

        assertThat(result).isTrue();
        assertThat(embedding.documentCallCount).isEqualTo(1);
        assertThat(embedding.documentTexts).containsExactly("chunk 1", "chunk 2");
        assertThat(client.insertedRows).hasSize(2);
    }

    @Test
    void testBuildIndexVectorTypeWithImagePathUsesEmbedMultimodal(@TempDir Path tempDir) throws Exception {
        Path image = Files.write(tempDir.resolve("image.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        RecordingMilvusClient client = new RecordingMilvusClient();
        MultimodalRecordingEmbedding embedding = new MultimodalRecordingEmbedding(List.of(List.of(0.2d, 0.2d)));
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);
        TextChunk imageChunk = chunk("1", "caption one", "doc_1", Map.of("image_path", image.toString()));
        TextChunk textChunk = chunk("2", "text only chunk", "doc_1");

        boolean result = indexer.buildIndex(
                List.of(imageChunk, textChunk),
                indexConfig("test_index", "vector"),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.multimodalDocuments).hasSize(1);
        assertThat(embedding.documentTexts).containsExactly("text only chunk");
        assertThat(imageChunk.getEmbedding()).containsExactly(0.5d, 0.5d);
        assertThat(textChunk.getEmbedding()).containsExactly(0.2d, 0.2d);
        assertThat(client.insertedRows).hasSize(2);
    }

    @Test
    void testBuildIndexVectorTypeWithImagePathUseCaptionForImages(@TempDir Path tempDir) throws Exception {
        Path image = Files.write(tempDir.resolve("caption.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        RecordingMilvusClient client = new RecordingMilvusClient();
        MultimodalRecordingEmbedding embedding = new MultimodalRecordingEmbedding(
                List.of(List.of(0.1d, 0.1d), List.of(0.2d, 0.2d)));
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);
        TextChunk imageChunk = chunk("1", "caption one", "doc_1", Map.of("image_path", image.toString()));
        TextChunk textChunk = chunk("2", "text only chunk", "doc_1");

        boolean result = indexer.buildIndex(
                List.of(imageChunk, textChunk),
                IndexConfig.builder().indexName("test_index").indexType("vector").useCaptionForImages(true).build(),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.multimodalDocuments).isEmpty();
        assertThat(embedding.documentTexts).containsExactly("caption one", "text only chunk");
        assertThat(imageChunk.getEmbedding()).containsExactly(0.1d, 0.1d);
        assertThat(textChunk.getEmbedding()).containsExactly(0.2d, 0.2d);
        assertThat(client.insertedRows).hasSize(2);
    }

    @Test
    void testBuildIndexBm25Type() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        boolean result = indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1")),
                indexConfig("test_index", "bm25"),
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(client.lastSchema.getField("sparse_vector")).isNotNull();
        assertThat(client.lastSchema.getField("embedding")).isNull();
        assertThat(client.insertedRows).hasSize(1);
    }

    @Test
    void testBuildIndexVectorTypeWithoutEmbedModel() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.existingCollections.add("test_index");
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        BaseError error = expectBaseError(() -> indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1")),
                indexConfig("test_index", "vector"),
                null,
                Map.of()
        ).join());

        assertThat(error.getStatus()).isEqualTo(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND);
    }

    @Test
    void testBuildIndexWithDuplicateDocIds() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.queryRows.add(Map.of("document_id", "doc_1"));
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        BaseError error = expectBaseError(() -> indexer.buildIndex(
                List.of(chunk("1", "chunk 1", "doc_1")),
                indexConfig("test_index", "vector"),
                new RecordingEmbedding(List.of(List.of(0.1d, 0.1d))),
                Map.of()
        ).join());

        assertThat(error.getMessage()).contains("some documents with same doc_id already exist");
        assertThat(client.insertedRows).isEmpty();
    }

    @Test
    void testUpdateIndex() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.deleteCount = 1L;
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        boolean result = indexer.updateIndex(
                List.of(chunk("1", "updated chunk", "doc_1")),
                "doc_1",
                indexConfig("test_index", "vector"),
                new RecordingEmbedding(List.of(List.of(0.1d, 0.1d))),
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(client.lastDeleteFilter).isEqualTo("document_id == \"doc_1\"");
        assertThat(client.flushCollections).contains("test_index");
        assertThat(client.insertedRows).hasSize(1);
    }

    @Test
    void testDeleteIndexSuccess() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.deleteCount = 2L;
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        assertThat(indexer.deleteIndex("doc_1", "test_index", Map.of()).join()).isTrue();
    }

    @Test
    void testDeleteIndexNotFound() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.deleteCount = 0L;
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        assertThat(indexer.deleteIndex("doc_1", "test_index", Map.of()).join()).isFalse();
    }

    @Test
    void testIndexExistsTrue() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.existingCollections.add("test_index");
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        assertThat(indexer.indexExists("test_index").join()).isTrue();
    }

    @Test
    void testIndexExistsFalse() {
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                new RecordingMilvusClient());

        assertThat(indexer.indexExists("nonexistent_index").join()).isFalse();
    }

    @Test
    void testGetIndexInfoExists() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        client.existingCollections.add("test_index");
        client.collectionCount = 100L;
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        Map<String, Object> info = indexer.getIndexInfo("test_index").join();

        assertThat(info)
                .containsEntry("exists", true)
                .containsEntry("collection_name", "test_index")
                .containsEntry("count", 100L);
    }

    @Test
    void testGetIndexInfoNotExists() {
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                new RecordingMilvusClient());

        assertThat(indexer.getIndexInfo("nonexistent_index").join()).containsEntry("exists", false);
    }

    @Test
    void testClose() {
        RecordingMilvusClient client = new RecordingMilvusClient();
        MilvusIndexer indexer = newIndexer(config("", "test_collection"), "http://localhost:19530", null, null,
                client);

        indexer.close();

        assertThat(client.closed).isTrue();
    }

    private static MilvusIndexer newIndexer(
            VectorStoreConfig config,
            String uri,
            String token,
            String alias,
            RecordingMilvusClient client
    ) {
        return new MilvusIndexer(
                config,
                uri,
                token,
                "content",
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                RecordingCallback.class,
                alias,
                client
        );
    }

    private static VectorStoreConfig config(String databaseName, String collectionName) {
        return VectorStoreConfig.builder()
                .storeProvider(StoreType.MILVUS)
                .databaseName(databaseName)
                .collectionName(collectionName)
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

    private static class RecordingEmbedding extends Embedding {
        private final List<List<Double>> documentVectors;
        int documentCallCount;
        List<String> documentTexts = List.of();
        Map<String, Object> documentKwargs = Map.of();

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

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static final class MultimodalRecordingEmbedding extends RecordingEmbedding {
        private final List<MultimodalDocument> multimodalDocuments = new ArrayList<>();

        private MultimodalRecordingEmbedding(List<List<Double>> documentVectors) {
            super(documentVectors);
        }

        public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document) {
            multimodalDocuments.add(document);
            return CompletableFuture.completedFuture(List.of(0.5d, 0.5d));
        }
    }

    private static final class RecordingMilvusClient implements MilvusIndexer.MilvusClientFacade {
        private final List<String> existingCollections = new ArrayList<>();
        private final List<String> createdCollections = new ArrayList<>();
        private final List<Map<String, Object>> queryRows = new ArrayList<>();
        private final List<Map<String, Object>> insertedRows = new ArrayList<>();
        private final List<String> flushCollections = new ArrayList<>();
        private CreateCollectionReq.CollectionSchema lastSchema;
        private List<IndexParam> lastIndexParams = List.of();
        private String lastDeleteFilter;
        private long deleteCount;
        private long collectionCount;
        private boolean closed;

        @Override
        public CompletableFuture<Boolean> hasCollection(String collectionName) {
            return CompletableFuture.completedFuture(existingCollections.contains(collectionName));
        }

        @Override
        public CompletableFuture<Void> createCollection(
                String collectionName,
                CreateCollectionReq.CollectionSchema schema,
                List<IndexParam> indexParams,
                Map<String, Object> options
        ) {
            createdCollections.add(collectionName);
            existingCollections.add(collectionName);
            lastSchema = schema;
            lastIndexParams = new ArrayList<>(indexParams);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<Map<String, Object>>> query(
                String collectionName,
                String filter,
                List<String> outputFields
        ) {
            return CompletableFuture.completedFuture(new ArrayList<>(queryRows));
        }

        @Override
        public CompletableFuture<Void> insert(String collectionName, List<Map<String, Object>> rows, int batchSize) {
            insertedRows.addAll(rows.stream().map(LinkedHashMap::new).map(row -> (Map<String, Object>) row).toList());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Long> delete(String collectionName, String filter) {
            lastDeleteFilter = filter;
            return CompletableFuture.completedFuture(deleteCount);
        }

        @Override
        public CompletableFuture<Void> flush(String collectionName) {
            flushCollections.add(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Long> count(String collectionName) {
            return CompletableFuture.completedFuture(collectionCount);
        }

        @Override
        public CompletableFuture<DescribeCollectionResp> describeCollection(String collectionName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
