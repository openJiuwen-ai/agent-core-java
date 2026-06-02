/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.KnowledgeBase;
import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Simple knowledge base test cases.
 *
 * <p>Mirrors Python's {@code test_simple_knowledge_base.py} in
 * {@code tests.unit_tests.core.retrieval.test_simple_knowledge_base}.</p>
 */
@DisplayName("SimpleKnowledgeBase Tests")
class TestSimpleKnowledgeBase {

    @Nested
    @DisplayName("Parse Files")
    class ParseFilesTests {

        @Test
        @DisplayName("test_parse_files_success - parses all files")
        void testParseFilesSuccess() {
            CountingParser parser = new CountingParser(false);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setParser(parser);
            kb.setLlmClient(mock(BaseModelClient.class));

            List<Document> documents = kb.parseFiles(List.of("test1.txt", "test2.txt"));

            assertThat(documents).hasSize(4);
            assertThat(parser.getParseCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("test_parse_files_without_parser - parser is required")
        void testParseFilesWithoutParser() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));

            assertThatThrownBy(() -> kb.parseFiles(List.of("test.txt")))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("parser is required");
        }

        @Test
        @DisplayName("test_parse_files_with_exception - ignores parse failures")
        void testParseFilesWithException() {
            CountingParser parser = new CountingParser(true);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setParser(parser);

            List<Document> documents = kb.parseFiles(List.of("test.txt"));

            assertThat(documents).isEmpty();
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("test_database_name_mismatch - rejects incompatible backend configs")
        void testDatabaseNameMismatch() {
            TestVectorStore vectorStore = new TestVectorStore(
                    "db_1", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id");
            TestIndexer indexer = new TestIndexer(
                    "db_2", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", true);

            assertThatThrownBy(() -> new SimpleKnowledgeBase(
                    config("vector"),
                    vectorStore,
                    new TestEmbedding(),
                    null,
                    null,
                    indexer,
                    null,
                    null))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("incompatible database_name configs");
        }
    }

    @Nested
    @DisplayName("Add Documents")
    class AddDocumentsTests {

        @Test
        @DisplayName("test_add_documents_success - adds and indexes documents")
        void testAddDocumentsSuccess() {
            CountingChunker chunker = new CountingChunker();
            TestIndexer indexer = new TestIndexer(
                    "", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", true);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setChunker(chunker);
            kb.setIndexManager(indexer);
            kb.setEmbedModel(new TestEmbedding());

            List<String> docIds = kb.addDocuments(List.of(
                    new Document("doc_1", "Test document 1"),
                    new Document("doc_2", "Test document 2")));

            assertThat(docIds).containsExactly("doc_1", "doc_2");
            assertThat(chunker.getChunkDocumentsCount()).isEqualTo(1);
            assertThat(indexer.getBuildIndexCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("test_add_documents_without_chunker - chunker is required")
        void testAddDocumentsWithoutChunker() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setIndexManager(new TestIndexer("", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", true));

            assertThatThrownBy(() -> kb.addDocuments(List.of(new Document("doc_1", "Test"))))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("chunker is required");
        }

        @Test
        @DisplayName("test_add_documents_without_index_manager - index_manager is required")
        void testAddDocumentsWithoutIndexManager() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setChunker(new CountingChunker());

            assertThatThrownBy(() -> kb.addDocuments(List.of(new Document("doc_1", "Test"))))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("index_manager is required");
        }

        @Test
        @DisplayName("test_add_documents_build_index_failed - build failure raises")
        void testAddDocumentsBuildIndexFailed() {
            CountingChunker chunker = new CountingChunker();
            TestIndexer indexer = new TestIndexer(
                    "", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", false);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setChunker(chunker);
            kb.setIndexManager(indexer);
            kb.setEmbedModel(new TestEmbedding());

            assertThatThrownBy(() -> kb.addDocuments(List.of(new Document("doc_1", "Test"))))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("Failed to build index");
        }
    }

    @Nested
    @DisplayName("Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("test_retrieve_with_retriever - uses provided retriever")
        void testRetrieveWithRetriever() {
            TestRetriever retriever = new TestRetriever(List.of(
                    new RetrievalResult("Test result", 0.95, Map.of(), "doc_1", "chunk_1")));
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setRetriever(retriever);

            List<RetrievalResult> results = kb.retrieve("test query", new RetrievalConfig());

            assertThat(results).hasSize(1);
            assertThat(retriever.getRetrieveCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("test_retrieve_with_agentic - wraps retriever with agentic layer")
        void testRetrieveWithAgentic() throws Exception {
            TestRetriever retriever = new TestRetriever(List.of(
                    new RetrievalResult("Test result", 0.95, Map.of(), "doc_1", "chunk_1")));
            BaseModelClient llmClient = mock(BaseModelClient.class);
            when(llmClient.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyMap()))
                    .thenAnswer(agenticResponse(
                            "[[\"subject\",\"predicate\",\"object\"]]",
                            "{\"sufficient\": true, \"next_question\": null}"));

            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setRetriever(retriever);
            kb.setLlmClient(llmClient);

            RetrievalConfig retrievalConfig = new RetrievalConfig();
            retrievalConfig.setAgentic(true);
            List<RetrievalResult> results = kb.retrieve("test query", retrievalConfig);

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("test_retrieve_without_agentic - uses base retriever directly")
        void testRetrieveWithoutAgentic() {
            TestRetriever retriever = new TestRetriever(List.of(
                    new RetrievalResult("Test result", 0.95, Map.of(), "doc_1", "chunk_1")));
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setRetriever(retriever);

            RetrievalConfig retrievalConfig = new RetrievalConfig();
            retrievalConfig.setAgentic(false);
            List<RetrievalResult> results = kb.retrieve("test query", retrievalConfig);

            assertThat(results).hasSize(1);
            assertThat(retriever.getRetrieveCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("test_retrieve_without_retriever_or_vector_store - retriever or vector_store required")
        void testRetrieveWithoutRetrieverOrVectorStore() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));

            assertThatThrownBy(() -> kb.retrieve("test query", new RetrievalConfig()))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("vector_store or retriever is required");
        }
    }

    @Nested
    @DisplayName("Delete / Update / Statistics")
    class MaintenanceTests {

        @Test
        @DisplayName("test_delete_documents_success - deletes every document")
        void testDeleteDocumentsSuccess() {
            TestIndexer indexer = new TestIndexer(
                    "", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", true);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setIndexManager(indexer);

            boolean result = kb.deleteDocuments(List.of("doc_1", "doc_2"));

            assertThat(result).isTrue();
            assertThat(indexer.getDeleteIndexCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("test_delete_documents_without_index_manager - index_manager is required")
        void testDeleteDocumentsWithoutIndexManager() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));

            assertThatThrownBy(() -> kb.deleteDocuments(List.of("doc_1")))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("index_manager is required");
        }

        @Test
        @DisplayName("test_update_documents_success - updates all documents")
        void testUpdateDocumentsSuccess() {
            CountingChunker chunker = new CountingChunker();
            TestIndexer indexer = new TestIndexer(
                    "", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", true);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setChunker(chunker);
            kb.setIndexManager(indexer);
            kb.setEmbedModel(new TestEmbedding());

            List<String> ids = kb.updateDocuments(List.of(new Document("doc_1", "Updated document")));

            assertThat(ids).containsExactly("doc_1");
            assertThat(indexer.getUpdateIndexCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("test_get_statistics_success - returns index stats")
        void testGetStatisticsSuccess() {
            TestIndexer indexer = new TestIndexer(
                    "", "cosine", "vector", "text", "vector", "sparse_vector", "metadata", "doc_id", true);
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));
            kb.setIndexManager(indexer);

            Map<String, Object> stats = kb.getStatistics();

            assertThat(stats.get("kb_id")).isEqualTo("test_kb");
            assertThat(stats.get("index_type")).isEqualTo("vector");
            assertThat(stats).containsKey("index_info");
        }

        @Test
        @DisplayName("test_get_statistics_without_index_manager - reports missing index manager")
        void testGetStatisticsWithoutIndexManager() {
            SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config("vector"));

            Map<String, Object> stats = kb.getStatistics();

            assertThat(stats.get("kb_id")).isEqualTo("test_kb");
            assertThat(stats.get("index_exists")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("Multi KB Retrieval")
    class MultiKbTests {

        @Test
        @DisplayName("test_retrieve_multi_kb_empty_list - empty KB list")
        void testRetrieveMultiKbEmptyList() {
            List<String> results = SimpleKnowledgeBase.retrieveMultiKb(List.of(), "test query", null, 5);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("test_retrieve_multi_kb_success - deduplicates by text")
        void testRetrieveMultiKbSuccess() {
            StubKnowledgeBase kb1 = new StubKnowledgeBase(kbConfig("kb1"), List.of(
                    new RetrievalResult("Result 1", 0.9, Map.of(), "doc_1", "chunk_1"),
                    new RetrievalResult("Result 2", 0.8, Map.of(), "doc_2", "chunk_2")));
            StubKnowledgeBase kb2 = new StubKnowledgeBase(kbConfig("kb2"), List.of(
                    new RetrievalResult("Result 2", 0.85, Map.of(), "doc_2", "chunk_2"),
                    new RetrievalResult("Result 3", 0.7, Map.of(), "doc_3", "chunk_3")));

            List<String> results = SimpleKnowledgeBase.retrieveMultiKb(List.of(kb1, kb2), "test query", null, 5);

            assertThat(results).hasSizeLessThanOrEqualTo(5);
            assertThat(results).contains("Result 1", "Result 2", "Result 3");
        }

        @Test
        @DisplayName("test_retrieve_multi_kb_with_failure - ignores failed KB")
        void testRetrieveMultiKbWithFailure() {
            StubKnowledgeBase kb1 = new StubKnowledgeBase(kbConfig("kb1"), new RuntimeException("Error"));
            StubKnowledgeBase kb2 = new StubKnowledgeBase(kbConfig("kb2"), List.of(
                    new RetrievalResult("Result 1", 0.9, Map.of(), "doc_1", "chunk_1")));

            List<String> results = SimpleKnowledgeBase.retrieveMultiKb(List.of(kb1, kb2), "test query", null, 5);

            assertThat(results).containsExactly("Result 1");
        }

        @Test
        @DisplayName("test_retrieve_multi_kb_with_source - aggregates source KB ids")
        void testRetrieveMultiKbWithSource() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("raw_score", 0.95);
            metadata.put("raw_score_scaled", 0.95);
            StubKnowledgeBase kb1 = new StubKnowledgeBase(kbConfig("kb1"), List.of(
                    new RetrievalResult("Result 1", 0.9, metadata, "doc_1", "chunk_1")));
            StubKnowledgeBase kb2 = new StubKnowledgeBase(kbConfig("kb2"), List.of(
                    new RetrievalResult("Result 1", 0.95, metadata, "doc_1", "chunk_1")));

            List<MultiKBRetrievalResult> results = SimpleKnowledgeBase.retrieveMultiKbWithSource(
                    List.of(kb1, kb2), "test query", null, 5);

            assertThat(results).hasSizeLessThanOrEqualTo(5);
            assertThat(results.get(0).getText()).isEqualTo("Result 1");
            assertThat(results.get(0).getScore()).isEqualTo(0.95);
            assertThat(results.get(0).getKbIds()).containsExactly("kb1", "kb2");
        }
    }

    private static KnowledgeBaseConfig config(String indexType) {
        return new KnowledgeBaseConfig("test_kb", indexType, false, 512, 50);
    }

    private static KnowledgeBaseConfig kbConfig(String kbId) {
        return new KnowledgeBaseConfig(kbId, "vector", false, 512, 50);
    }

    private static Answer<Object> agenticResponse(String first, String second) {
        AtomicInteger callCount = new AtomicInteger();
        return invocation -> {
            int index = callCount.getAndIncrement();
            return new com.openjiuwen.core.foundation.llm.schema.AssistantMessage(index == 0 ? first : second);
        };
    }

    private static final class CountingParser extends Parser {
        private final boolean fail;
        private int parseCount;

        private CountingParser(boolean fail) {
            this.fail = fail;
        }

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            parseCount++;
            if (fail) {
                throw new RuntimeException("Parse error");
            }
            return List.of(
                    new Document(docId + "_1", "Test document 1"),
                    new Document(docId + "_2", "Test document 2"));
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }

        private int getParseCount() {
            return parseCount;
        }
    }

    private static final class CountingChunker extends Chunker {
        private int chunkDocumentsCount;

        private CountingChunker() {
            super(64, 8);
        }

        @Override
        public List<String> chunkText(String text) {
            return List.of(text + " chunk 1", text + " chunk 2");
        }

        @Override
        public List<TextChunk> chunkDocuments(List<Document> documents) {
            chunkDocumentsCount++;
            return super.chunkDocuments(documents);
        }

        private int getChunkDocumentsCount() {
            return chunkDocumentsCount;
        }
    }

    private static final class TestEmbedding implements Embedding {

        @Override
        public List<Float> embedQuery(String text) {
            return List.of(0.1f, 0.2f);
        }

        @Override
        public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
            List<List<Float>> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(List.of(0.1f, 0.2f));
            }
            return vectors;
        }

        @Override
        public int getDimension() {
            return 2;
        }
    }

    private static class TestVectorStore implements VectorStore {
        private final String databaseName;
        private final String distanceMetric;
        private final String indexType;
        private final String textField;
        private final String vectorField;
        private final String sparseVectorField;
        private final String metadataField;
        private final String docIdField;

        private TestVectorStore(String databaseName,
                                String distanceMetric,
                                String indexType,
                                String textField,
                                String vectorField,
                                String sparseVectorField,
                                String metadataField,
                                String docIdField) {
            this.databaseName = databaseName;
            this.distanceMetric = distanceMetric;
            this.indexType = indexType;
            this.textField = textField;
            this.vectorField = vectorField;
            this.sparseVectorField = sparseVectorField;
            this.metadataField = metadataField;
            this.docIdField = docIdField;
        }

        @Override
        public String getCollectionName() {
            return "test_collection";
        }

        @Override
        public void setCollectionName(String collectionName) {
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            return this;
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        }

        @Override
        public List<com.openjiuwen.core.retrieval.common.SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public List<com.openjiuwen.core.retrieval.common.SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public List<com.openjiuwen.core.retrieval.common.SearchResult> hybridSearch(String queryText, List<Float> queryVector, int topK, double alpha, Map<String, Object> filters, Map<String, Object> options) {
            return List.of();
        }

        @Override
        public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
            return false;
        }

        @Override
        public boolean tableExists(String tableName) {
            return false;
        }

        @Override
        public void deleteTable(String tableName) {
        }

        @Override
        public List<com.openjiuwen.core.retrieval.common.SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
            return List.of();
        }

        @Override
        public long count(String tableName) {
            return 0;
        }

        @Override
        public String getDatabaseName() {
            return databaseName;
        }

        @Override
        public String getDistanceMetric() {
            return distanceMetric;
        }

        @Override
        public String getIndexType() {
            return indexType;
        }

        @Override
        public String getTextField() {
            return textField;
        }

        @Override
        public String getVectorField() {
            return vectorField;
        }

        @Override
        public String getSparseVectorField() {
            return sparseVectorField;
        }

        @Override
        public String getMetadataField() {
            return metadataField;
        }

        @Override
        public String getDocIdField() {
            return docIdField;
        }
    }

    private static final class TestIndexer implements Indexer {
        private final String databaseName;
        private final String distanceMetric;
        private final String indexType;
        private final String textField;
        private final String vectorField;
        private final String sparseVectorField;
        private final String metadataField;
        private final String docIdField;
        private final boolean buildResult;
        private int buildIndexCount;
        private int deleteIndexCount;
        private int updateIndexCount;

        private TestIndexer(String databaseName,
                            String distanceMetric,
                            String indexType,
                            String textField,
                            String vectorField,
                            String sparseVectorField,
                            String metadataField,
                            String docIdField,
                            boolean buildResult) {
            this.databaseName = databaseName;
            this.distanceMetric = distanceMetric;
            this.indexType = indexType;
            this.textField = textField;
            this.vectorField = vectorField;
            this.sparseVectorField = sparseVectorField;
            this.metadataField = metadataField;
            this.docIdField = docIdField;
            this.buildResult = buildResult;
        }

        @Override
        public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel, Map<String, Object> options) {
            buildIndexCount++;
            return buildResult;
        }

        @Override
        public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel, Map<String, Object> options) {
            updateIndexCount++;
            return true;
        }

        @Override
        public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
            deleteIndexCount++;
            return true;
        }

        @Override
        public boolean indexExists(String indexName) {
            return true;
        }

        @Override
        public Map<String, Object> getIndexInfo(String indexName) {
            return Map.of("count", 10);
        }

        @Override
        public String getDatabaseName() {
            return databaseName;
        }

        @Override
        public String getDistanceMetric() {
            return distanceMetric;
        }

        @Override
        public String getIndexType() {
            return indexType;
        }

        @Override
        public String getTextField() {
            return textField;
        }

        @Override
        public String getVectorField() {
            return vectorField;
        }

        @Override
        public String getSparseVectorField() {
            return sparseVectorField;
        }

        @Override
        public String getMetadataField() {
            return metadataField;
        }

        @Override
        public String getDocIdField() {
            return docIdField;
        }

        private int getBuildIndexCount() {
            return buildIndexCount;
        }

        private int getDeleteIndexCount() {
            return deleteIndexCount;
        }

        private int getUpdateIndexCount() {
            return updateIndexCount;
        }
    }

    private static final class TestRetriever implements Retriever {
        private final List<RetrievalResult> results;
        private final AtomicInteger retrieveCount = new AtomicInteger();

        private TestRetriever(List<RetrievalResult> results) {
            this.results = results;
        }

        @Override
        public List<RetrievalResult> retrieve(String query, int topK, Double scoreThreshold, String mode, Map<String, Object> options) {
            retrieveCount.incrementAndGet();
            return results;
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(List<String> queries, int topK, String mode, Map<String, Object> options) {
            List<List<RetrievalResult>> batch = new ArrayList<>();
            for (String ignored : queries) {
                batch.add(results);
            }
            return batch;
        }

        private int getRetrieveCount() {
            return retrieveCount.get();
        }
    }

    private static final class StubKnowledgeBase extends KnowledgeBase {
        private final List<RetrievalResult> retrievalResults;
        private final RuntimeException failure;

        private StubKnowledgeBase(KnowledgeBaseConfig config, List<RetrievalResult> retrievalResults) {
            super(config);
            this.retrievalResults = retrievalResults;
            this.failure = null;
        }

        private StubKnowledgeBase(KnowledgeBaseConfig config, RuntimeException failure) {
            super(config);
            this.retrievalResults = List.of();
            this.failure = failure;
        }

        @Override
        public List<String> addDocuments(List<Document> documents) {
            return List.of();
        }

        @Override
        public List<RetrievalResult> retrieve(String query, RetrievalConfig retrievalConfig) {
            if (failure != null) {
                throw failure;
            }
            return retrievalResults;
        }

        @Override
        public boolean deleteDocuments(List<String> docIds) {
            return false;
        }

        @Override
        public List<String> updateDocuments(List<Document> documents) {
            return List.of();
        }

        @Override
        public Map<String, Object> getStatistics() {
            return Map.of();
        }
    }
}
