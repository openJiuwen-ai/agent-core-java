/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code MilvusIndexer} in
 * {@code openjiuwen/core/retrieval/indexing/indexer/milvus_indexer.py}.
 */
class MilvusIndexerTest {

    @TempDir
    Path tempDir;

    @Test
    void initStoresConnectionAndFieldConfiguration() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        VectorStoreConfig config = new VectorStoreConfig(StoreType.MILVUS, "name", "test_collection", "dot");

        MilvusIndexer indexer = newIndexer(
                config,
                "http://localhost:19530",
                "token",
                "custom_text",
                "custom_vector",
                "custom_sparse",
                "custom_metadata",
                "custom_doc_id",
                null,
                client
        );

        assertThat(indexer.getMilvusUri()).isEqualTo("http://localhost:19530");
        assertThat(indexer.getMilvusToken()).isEqualTo("token");
        assertThat(indexer.getTextField()).isEqualTo("custom_text");
        assertThat(indexer.getVectorField()).isEqualTo("custom_vector");
        assertThat(indexer.getSparseVectorField()).isEqualTo("custom_sparse");
        assertThat(indexer.getMetadataField()).isEqualTo("custom_metadata");
        assertThat(indexer.getDocIdField()).isEqualTo("custom_doc_id");
        assertThat(indexer.getDistanceMetric()).isEqualTo("IP");
        assertThat(indexer.getConstructConfig()).containsEntry("metric_type", "IP");
        assertThat(indexer.getMilvusAlias()).isEqualTo(CommonUtils.createMilvusAlias(
                null,
                "http://localhost:19530",
                "",
                "token"
        ));
    }

    @Test
    void initAcceptsMilvusVectorFieldAndRejectsInvalidVectorField() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusHNSW hnsw = new MilvusHNSW();
        hnsw.setVectorField("dense");

        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", hnsw,
                "sparse_vector", "metadata", "document_id", "alias", client);

        assertThat(indexer.getVectorField()).isEqualTo("dense");
        assertThat(indexer.getConstructConfig()).containsEntry("M", 30);

        assertThatThrownBy(() -> newIndexer(defaultConfig(), "uri", null, "content", Map.of("vector_field", "bad"),
                "sparse_vector", "metadata", "document_id", null, client))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("vector_field must be either a str or MilvusVectorField instance");
    }

    @Test
    void buildIndexVectorUsesMultimodalEmbeddingAndWritesMilvusRows() throws Exception {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);
        MultimodalRecordingEmbedding embedding = new MultimodalRecordingEmbedding();
        Path image = Files.writeString(tempDir.resolve("img.png"), "png");
        List<TextChunk> chunks = new ArrayList<>();
        chunks.add(new TextChunk("chunk-1", "caption one", "doc-1",
                new LinkedHashMap<>(Map.of("image_path", image.toString())), null));
        chunks.add(new TextChunk("chunk-2", "plain text", "doc-1"));

        boolean result = indexer.buildIndex(
                chunks,
                new IndexConfig("test_index", "vector", false),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(client.createdCollections).containsExactly("test_index");
        assertThat(client.lastSchema.getField("embedding")).isNotNull();
        assertThat(client.lastIndexParams).extracting(IndexParam::getFieldName)
                .contains("document_id", "chunk_id", "embedding");
        assertThat(embedding.multimodalDocuments).hasSize(1);
        assertThat(embedding.lastTexts).containsExactly("plain text");
        assertThat(chunks.get(0).getEmbedding()).containsExactly(9.0d, 8.0d);
        assertThat(chunks.get(1).getEmbedding()).containsExactly(1.0d, 2.0d);
        assertThat(client.insertedRows).hasSize(2);
        assertThat(client.insertedRows.get(0))
                .containsEntry("chunk_id", "chunk-1")
                .containsEntry("document_id", "doc-1")
                .containsEntry("content", "caption one")
                .containsKey("embedding")
                .containsKey("metadata");
    }

    @Test
    void buildIndexUseCaptionForImagesForcesTextOnlyEmbedding() throws Exception {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);
        MultimodalRecordingEmbedding embedding = new MultimodalRecordingEmbedding();
        Path image = Files.writeString(tempDir.resolve("caption.png"), "png");
        List<TextChunk> chunks = List.of(
                new TextChunk("chunk-1", "caption one", "doc-1",
                        new LinkedHashMap<>(Map.of("image_path", image.toString())), null),
                new TextChunk("chunk-2", "plain text", "doc-1")
        );

        boolean result = indexer.buildIndex(
                chunks,
                new IndexConfig("test_index", "vector", true),
                embedding,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(embedding.multimodalDocuments).isEmpty();
        assertThat(embedding.lastTexts).containsExactly("caption one", "plain text");
        assertThat(chunks.get(0).getEmbedding()).containsExactly(1.0d, 2.0d);
        assertThat(chunks.get(1).getEmbedding()).containsExactly(3.0d, 4.0d);
    }

    @Test
    void buildIndexBm25CreatesSparseSchemaWithoutEmbeddingModel() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);

        boolean result = indexer.buildIndex(
                List.of(new TextChunk("chunk-1", "chunk 1", "doc-1")),
                new IndexConfig("bm25_index", "bm25", false),
                null,
                Map.of()
        ).join();

        assertThat(result).isTrue();
        assertThat(client.lastSchema.getField("sparse_vector")).isNotNull();
        assertThat(client.lastSchema.getField("embedding")).isNull();
        assertThat(client.lastSchema.getFunctionList()).hasSize(1);
        assertThat(client.insertedRows.get(0)).doesNotContainKey("embedding");
    }

    @Test
    void buildIndexRejectsDuplicateDocIdsBeforeInsert() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.queryRows.add(Map.of("document_id", "doc-1"));
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);

        assertThatThrownBy(() -> indexer.buildIndex(
                List.of(new TextChunk("chunk-1", "chunk 1", "doc-1")),
                new IndexConfig("test_index", "bm25", false),
                null,
                Map.of()
        ).join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("some documents with same doc_id already exist");

        assertThat(client.insertedRows).isEmpty();
    }

    @Test
    void vectorIndexWithoutEmbeddingModelMatchesPythonErrorOrdering() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);

        assertBaseError(
                () -> indexer.buildIndex(
                        List.of(new TextChunk("chunk-1", "chunk 1", "doc-1")),
                        new IndexConfig("new_index", "vector", false),
                        null,
                        Map.of()
                ).join(),
                StatusCode.RETRIEVAL_INDEXING_DIMENSION_NOT_FOUND
        );

        client.existingCollections.add("existing_index");
        assertBaseError(
                () -> indexer.buildIndex(
                        List.of(new TextChunk("chunk-1", "chunk 1", "doc-1")),
                        new IndexConfig("existing_index", "vector", false),
                        null,
                        Map.of()
                ).join(),
                StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND
        );
    }

    @Test
    void updateDeleteIndexExistsAndInfoMirrorPythonAsyncContract() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.deleteCount = 2;
        client.collectionCount = 100L;
        client.existingCollections.add("test_index");
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);
        RecordingEmbedding embedding = new RecordingEmbedding();
        List<TextChunk> chunks = List.of(new TextChunk("chunk-1", "updated chunk", "doc-1"));
        IndexConfig config = new IndexConfig("test_index", "vector", false);

        assertThat(indexer.deleteIndex("doc-1", "test_index", Map.of()).join()).isTrue();
        assertThat(client.lastDeleteFilter).isEqualTo("document_id == \"doc-1\"");

        assertThat(indexer.updateIndex(chunks, "doc-1", config, embedding, Map.of()).join()).isTrue();
        assertThat(client.flushCollections).contains("test_index");
        assertThat(client.insertedRows).isNotEmpty();

        assertThat(indexer.indexExists("test_index").join()).isTrue();
        assertThat(indexer.getIndexInfo("test_index").join())
                .containsEntry("exists", true)
                .containsEntry("collection_name", "test_index")
                .containsEntry("count", 100L);
    }

    @Test
    void closeDelegatesToClient() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusIndexer indexer = newIndexer(defaultConfig(), "uri", null, "content", "embedding",
                "sparse_vector", "metadata", "document_id", null, client);

        indexer.close();

        assertThat(client.closed).isTrue();
    }

    private static VectorStoreConfig defaultConfig() {
        return new VectorStoreConfig(StoreType.MILVUS, "db", "test_collection", "cosine");
    }

    private static MilvusIndexer newIndexer(
            VectorStoreConfig config,
            String uri,
            String token,
            String textField,
            Object vectorField,
            String sparseVectorField,
            String metadataField,
            String docIdField,
            String alias,
            FakeMilvusClientFacade client
    ) {
        return new MilvusIndexer(
                config,
                uri,
                token,
                textField,
                vectorField,
                sparseVectorField,
                metadataField,
                docIdField,
                BaseCallback.class,
                alias,
                client
        );
    }

    private static void assertBaseError(Runnable action, StatusCode statusCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(BaseError.class)
                .extracting("status")
                .isEqualTo(statusCode);
    }

    private static class RecordingEmbedding extends Embedding {
        List<String> lastTexts = List.of();
        Map<String, Object> lastKwargs = Map.of();

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(1.0d, 2.0d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
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
        private final List<MultimodalDocument> multimodalDocuments = new ArrayList<>();

        public CompletableFuture<List<Double>> embedMultimodal(MultimodalDocument document, Map<String, Object> kwargs) {
            multimodalDocuments.add(document);
            return CompletableFuture.completedFuture(List.of(9.0d, 8.0d));
        }
    }

    private static final class FakeMilvusClientFacade implements MilvusIndexer.MilvusClientFacade {
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
