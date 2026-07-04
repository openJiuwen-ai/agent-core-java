/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
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

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

/**
 * Supplemental parity tests for simple knowledge base behavior.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.test_simple_knowledge_base} in
 * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.</p>
 */
public class SimpleKnowledgeBasePythonParityTest {

    private static final String SOURCE = "tests/unit_tests/core/retrieval/test_simple_knowledge_base.py";

    @TestFactory
    Collection<DynamicTest> pythonSimpleKnowledgeBaseCases() {
        return pythonNodeIds()
                .map(nodeId -> dynamicTest(nodeId, () -> runPythonCase(nodeId)))
                .toList();
    }

    private static Stream<String> pythonNodeIds() {
        return Stream.of(
                SOURCE + "::TestSimpleKnowledgeBase::test_parse_files_success",
                SOURCE + "::TestSimpleKnowledgeBase::test_parse_files_without_parser",
                SOURCE + "::TestSimpleKnowledgeBase::test_parse_files_with_exception",
                SOURCE + "::TestSimpleKnowledgeBase::test_database_name_mismatch",
                SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_success",
                SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_without_chunker",
                SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_without_index_manager",
                SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_build_index_failed",
                SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_with_retriever",
                SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_with_agentic",
                SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_without_agentic",
                SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_without_retriever_or_vector_store",
                SOURCE + "::TestSimpleKnowledgeBase::test_delete_documents_success",
                SOURCE + "::TestSimpleKnowledgeBase::test_delete_documents_without_index_manager",
                SOURCE + "::TestSimpleKnowledgeBase::test_update_documents_success",
                SOURCE + "::TestSimpleKnowledgeBase::test_get_statistics_success",
                SOURCE + "::TestSimpleKnowledgeBase::test_get_statistics_without_index_manager",
                SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_empty_list",
                SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_success",
                SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_with_failure",
                SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_with_source"
        );
    }

    private static void runPythonCase(String nodeId) throws Exception {
        switch (nodeId) {
            case SOURCE + "::TestSimpleKnowledgeBase::test_parse_files_success" -> parseFilesSuccess();
            case SOURCE + "::TestSimpleKnowledgeBase::test_parse_files_without_parser" -> parseFilesWithoutParser();
            case SOURCE + "::TestSimpleKnowledgeBase::test_parse_files_with_exception" -> parseFilesWithException();
            case SOURCE + "::TestSimpleKnowledgeBase::test_database_name_mismatch" -> databaseNameMismatch();
            case SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_success" -> addDocumentsSuccess();
            case SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_without_chunker" -> addDocumentsWithoutChunker();
            case SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_without_index_manager" ->
                    addDocumentsWithoutIndexManager();
            case SOURCE + "::TestSimpleKnowledgeBase::test_add_documents_build_index_failed" ->
                    addDocumentsBuildIndexFailed();
            case SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_with_retriever" -> retrieveWithRetriever();
            case SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_with_agentic" -> retrieveWithAgentic();
            case SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_without_agentic" -> retrieveWithoutAgentic();
            case SOURCE + "::TestSimpleKnowledgeBase::test_retrieve_without_retriever_or_vector_store" ->
                    retrieveWithoutRetrieverOrVectorStore();
            case SOURCE + "::TestSimpleKnowledgeBase::test_delete_documents_success" -> deleteDocumentsSuccess();
            case SOURCE + "::TestSimpleKnowledgeBase::test_delete_documents_without_index_manager" ->
                    deleteDocumentsWithoutIndexManager();
            case SOURCE + "::TestSimpleKnowledgeBase::test_update_documents_success" -> updateDocumentsSuccess();
            case SOURCE + "::TestSimpleKnowledgeBase::test_get_statistics_success" -> getStatisticsSuccess();
            case SOURCE + "::TestSimpleKnowledgeBase::test_get_statistics_without_index_manager" ->
                    getStatisticsWithoutIndexManager();
            case SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_empty_list" -> retrieveMultiKbEmptyList();
            case SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_success" -> retrieveMultiKbSuccess();
            case SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_with_failure" -> retrieveMultiKbWithFailure();
            case SOURCE + "::TestRetrieveMultiKb::test_retrieve_multi_kb_with_source" -> retrieveMultiKbWithSource();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void parseFilesSuccess() {
        RecordingParser parser = new RecordingParser();
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config(), null, null, parser, null, null, null, null, null);

        List<Document> documents = kb.parseFiles(List.of("test1.txt", "test2.txt"));

        assertThat(documents).hasSize(4);
        assertThat(parser.parseCalls).isEqualTo(2);
    }

    private static void parseFilesWithoutParser() {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config());

        assertBaseErrorContains(() -> kb.parseFiles(List.of("test.txt")), "parser is required");
    }

    private static void parseFilesWithException() {
        RecordingParser parser = new RecordingParser();
        parser.fail = true;
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config(), null, null, parser, null, null, null, null, null);

        List<Document> documents = kb.parseFiles(List.of("test.txt"));

        assertThat(documents).isEmpty();
    }

    private static void databaseNameMismatch() {
        assertBaseErrorContains(() -> new SimpleKnowledgeBase(
                config(),
                new FakeVectorStore("database_name"),
                null,
                null,
                new RecordingChunker(),
                null,
                new RecordingIndexer("different_name"),
                null,
                null
        ), "incompatible database_name configs");
    }

    private static void addDocumentsSuccess() {
        RecordingChunker chunker = new RecordingChunker();
        RecordingIndexer indexer = new RecordingIndexer("database_name");
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config(),
                null,
                null,
                null,
                chunker,
                null,
                indexer,
                null,
                null
        );
        List<Document> documents = List.of(
                new Document("doc_1", "Test document 1"),
                new Document("doc_2", "Test document 2")
        );

        List<String> docIds = kb.addDocuments(documents);

        assertThat(docIds).containsExactly("doc_1", "doc_2");
        assertThat(chunker.chunkDocumentsCalls).isEqualTo(1);
        assertThat(indexer.buildIndexCalls).isEqualTo(1);
    }

    private static void addDocumentsWithoutChunker() {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config());

        assertBaseErrorContains(() -> kb.addDocuments(List.of(new Document("Test"))), "chunker is required");
    }

    private static void addDocumentsWithoutIndexManager() {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config(),
                null,
                null,
                null,
                new RecordingChunker(),
                null,
                null,
                null,
                null
        );

        assertBaseErrorContains(() -> kb.addDocuments(List.of(new Document("Test"))), "index_manager is required");
    }

    private static void addDocumentsBuildIndexFailed() {
        RecordingIndexer indexer = new RecordingIndexer("database_name");
        indexer.buildIndexResult = false;
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config(),
                null,
                null,
                null,
                new RecordingChunker(),
                null,
                indexer,
                null,
                null
        );

        assertBaseErrorContains(() -> kb.addDocuments(List.of(new Document("Test"))), "Failed to build index");
    }

    private static void retrieveWithRetriever() {
        RecordingRetriever retriever = new RecordingRetriever(List.of(result("Test result", 0.95d)));
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config(), null, null, null, null, null, null, null, retriever);

        List<RetrievalResult> results = kb.retrieve("test query", new RetrievalConfig());

        assertThat(results).hasSize(1);
        assertThat(retriever.retrieveCalls).isEqualTo(1);
    }

    private static void retrieveWithAgentic() {
        RecordingRetriever retriever = new RecordingRetriever(List.of(result("Agentic result", 0.98d)));
        FakeModelClient llmClient = new FakeModelClient(List.of(
                "[[\"subject\", \"predicate\", \"object\"]]",
                "{\"sufficient\": true, \"next_question\": null}"
        ));
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config(),
                null,
                null,
                null,
                null,
                null,
                null,
                llmClient,
                retriever
        );

        List<RetrievalResult> results = kb.retrieve("test query", RetrievalConfig.builder().agentic(true).topK(5).build());

        assertThat(results).hasSize(1);
        assertThat(retriever.retrieveCalls).isGreaterThanOrEqualTo(1);
    }

    private static void retrieveWithoutAgentic() {
        RecordingRetriever retriever = new RecordingRetriever(List.of(result("Test result", 0.95d)));
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config(), null, null, null, null, null, null, null, retriever);

        List<RetrievalResult> results = kb.retrieve(
                "test query",
                RetrievalConfig.builder().agentic(false).topK(5).build()
        );

        assertThat(results).hasSize(1);
        assertThat(retriever.retrieveCalls).isEqualTo(1);
    }

    private static void retrieveWithoutRetrieverOrVectorStore() {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config());

        assertBaseErrorContains(() -> kb.retrieve("test query", new RetrievalConfig()),
                "vector_store or retriever is required");
    }

    private static void deleteDocumentsSuccess() {
        RecordingIndexer indexer = new RecordingIndexer("database_name");
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config(), null, null, null, null, null, indexer, null, null);

        Boolean result = kb.deleteDocuments(List.of("doc_1", "doc_2"));

        assertThat(result).isTrue();
        assertThat(indexer.deleteIndexCalls).isEqualTo(2);
    }

    private static void deleteDocumentsWithoutIndexManager() {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config());

        assertBaseErrorContains(() -> kb.deleteDocuments(List.of("doc_1")), "index_manager is required");
    }

    private static void updateDocumentsSuccess() {
        RecordingIndexer indexer = new RecordingIndexer("database_name");
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(
                config(),
                null,
                null,
                null,
                new RecordingChunker(),
                null,
                indexer,
                null,
                null
        );

        List<String> docIds = kb.updateDocuments(List.of(new Document("doc_1", "Updated document")));

        assertThat(docIds).containsExactly("doc_1");
        assertThat(indexer.updateIndexCalls).isEqualTo(1);
    }

    private static void getStatisticsSuccess() {
        RecordingIndexer indexer = new RecordingIndexer("database_name");
        indexer.indexInfo = Map.of("count", 10);
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config(), null, null, null, null, null, indexer, null, null);

        Map<String, Object> stats = kb.getStatistics();

        assertThat(stats).containsEntry("kb_id", "test_kb")
                .containsEntry("index_type", "vector")
                .containsKey("index_info");
    }

    private static void getStatisticsWithoutIndexManager() {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase(config());

        Map<String, Object> stats = kb.getStatistics();

        assertThat(stats).containsEntry("kb_id", "test_kb")
                .containsEntry("index_exists", false);
    }

    private static void retrieveMultiKbEmptyList() {
        List<String> results = SimpleKnowledgeBase.retrieveMultiKb(List.of(), "test query", null, null).join();

        assertThat(results).isEmpty();
    }

    private static void retrieveMultiKbSuccess() {
        KnowledgeBase kb1 = new FixedKnowledgeBase("kb1", List.of(
                result("Result 1", 0.9d),
                result("Result 2", 0.8d)
        ));
        KnowledgeBase kb2 = new FixedKnowledgeBase("kb2", List.of(
                result("Result 2", 0.85d),
                result("Result 3", 0.7d)
        ));

        List<String> results = SimpleKnowledgeBase.retrieveMultiKb(List.of(kb1, kb2), "test query", null, 5).join();

        assertThat(results).hasSizeLessThanOrEqualTo(5);
        assertThat(results).containsAnyOf("Result 2", "Result 1");
    }

    private static void retrieveMultiKbWithFailure() {
        KnowledgeBase kb1 = new FixedKnowledgeBase("kb1", null);
        KnowledgeBase kb2 = new FixedKnowledgeBase("kb2", List.of(result("Result 1", 0.9d)));

        List<String> results = SimpleKnowledgeBase.retrieveMultiKb(List.of(kb1, kb2), "test query", null, null).join();

        assertThat(results).containsExactly("Result 1");
    }

    private static void retrieveMultiKbWithSource() {
        KnowledgeBase kb1 = new FixedKnowledgeBase("kb1", List.of(new RetrievalResult(
                "Result 1",
                0.9d,
                Map.of("raw_score", 0.9d, "raw_score_scaled", 0.9d),
                "doc1",
                "chunk1"
        )));
        KnowledgeBase kb2 = new FixedKnowledgeBase("kb2", List.of(new RetrievalResult(
                "Result 1",
                0.95d,
                Map.of("raw_score", 0.95d, "raw_score_scaled", 0.95d),
                "doc2",
                "chunk2"
        )));

        List<MultiKBRetrievalResult> results = SimpleKnowledgeBase
                .retrieveMultiKbWithSource(List.of(kb1, kb2), "test query", null, 5)
                .join();

        assertThat(results).hasSizeLessThanOrEqualTo(5);
        assertThat(results.get(0).getText()).isEqualTo("Result 1");
        assertThat(results.get(0).getScore()).isEqualTo(0.95d);
        assertThat(results.get(0).getKbIds()).containsExactly("kb1", "kb2");
    }

    private static KnowledgeBaseConfig config() {
        return KnowledgeBaseConfig.builder()
                .kbId("test_kb")
                .indexType("vector")
                .build();
    }

    private static RetrievalResult result(String text, double score) {
        return new RetrievalResult(text, score, Map.of(), "", "");
    }

    private static void assertBaseErrorContains(Executable executable, String expectedMessage) {
        assertThatThrownBy(executable::execute)
                .satisfies(error -> {
                    Throwable unwrapped = unwrap(error);
                    assertThat(unwrapped).isInstanceOf(BaseError.class);
                    assertThat(unwrapped.getMessage()).contains(expectedMessage);
                });
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Mirrors Python's {@code AsyncMock.parse} parser fixture in
     * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.
     */
    private static final class RecordingParser extends Parser {
        private int parseCalls;
        private boolean fail;

        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            parseCalls++;
            if (fail) {
                return CompletableFuture.failedFuture(new IllegalStateException("Parse error"));
            }
            return CompletableFuture.completedFuture(List.of(
                    new Document("doc_1", "Test document 1"),
                    new Document("doc_2", "Test document 2")
            ));
        }
    }

    /**
     * Mirrors Python's {@code MagicMock.chunk_documents} fixture in
     * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.
     */
    private static final class RecordingChunker extends Chunker {
        private int chunkDocumentsCalls;

        @Override
        public List<TextChunk> chunkDocuments(List<Document> documents) {
            chunkDocumentsCalls++;
            List<TextChunk> chunks = new ArrayList<>();
            for (Document document : documents) {
                chunks.add(new TextChunk("chunk_" + document.getId_(), "Test chunk", document.getId_()));
            }
            return chunks;
        }

        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }
    }

    /**
     * Mirrors Python's async index manager mock in
     * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.
     */
    public static final class RecordingIndexer extends Indexer {
        private final String databaseName;
        private int buildIndexCalls;
        private int updateIndexCalls;
        private int deleteIndexCalls;
        private boolean buildIndexResult = true;
        private Map<String, Object> indexInfo = Map.of("count", 10);

        private RecordingIndexer(String databaseName) {
            this.databaseName = databaseName;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getDistanceMetric() {
            return "cosine";
        }

        public String getTextField() {
            return "text";
        }

        public String getVectorField() {
            return "vector";
        }

        public String getSparseVectorField() {
            return "sparse_vector";
        }

        public String getMetadataField() {
            return "metadata";
        }

        public String getDocIdField() {
            return "doc_id";
        }

        @Override
        public CompletableFuture<Boolean> buildIndex(
                List<TextChunk> chunks,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            buildIndexCalls++;
            return CompletableFuture.completedFuture(buildIndexResult);
        }

        @Override
        public CompletableFuture<Boolean> updateIndex(
                List<TextChunk> chunks,
                String docId,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            updateIndexCalls++;
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            deleteIndexCalls++;
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> indexExists(String indexName) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
            return CompletableFuture.completedFuture(indexInfo);
        }
    }

    /**
     * Mirrors Python's vector store mock with config.database_name in
     * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.
     */
    public static final class FakeVectorStore implements VectorStore {
        private final FakeVectorStoreConfig config;

        private FakeVectorStore(String databaseName) {
            this.config = new FakeVectorStoreConfig(databaseName);
        }

        public FakeVectorStoreConfig getConfig() {
            return config;
        }

        public String getDatabaseName() {
            return config.getDatabaseName();
        }

        public String getDistanceMetric() {
            return "cosine";
        }

        public String getTextField() {
            return "text";
        }

        public String getVectorField() {
            return "vector";
        }

        public String getSparseVectorField() {
            return "sparse_vector";
        }

        public String getMetadataField() {
            return "metadata";
        }

        public String getDocIdField() {
            return "doc_id";
        }

        @Override
        public void checkVectorField() {
        }

        @Override
        public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> search(
                List<Double> queryVector,
                int topK,
                VectorStore.VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStore.VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> hybridSearch(
                String queryText,
                List<Double> queryVector,
                int topK,
                double alpha,
                VectorStore.VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids, VectorStore.DeleteFilter filterExpr, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }

    public record FakeVectorStoreConfig(String databaseName) {
        public String getDatabaseName() {
            return databaseName;
        }
    }

    /**
     * Mirrors Python's async retriever mock in
     * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.
     */
    private static final class RecordingRetriever implements Retriever {
        private final List<RetrievalResult> results;
        private int retrieveCalls;

        private RecordingRetriever(List<RetrievalResult> results) {
            this.results = results;
        }

        @Override
        public List<RetrievalResult> retrieve(
                String query,
                int topK,
                Double scoreThreshold,
                String mode,
                Map<String, Object> options
        ) {
            retrieveCalls++;
            return results;
        }

        @Override
        public List<List<RetrievalResult>> batchRetrieve(
                List<String> queries,
                int topK,
                String mode,
                Map<String, Object> options
        ) {
            return queries.stream().map(ignored -> results).toList();
        }

        @Override
        public String getIndexType() {
            return "vector";
        }
    }

    private static final class FixedKnowledgeBase extends KnowledgeBase {
        private final List<RetrievalResult> results;

        private FixedKnowledgeBase(String kbId, List<RetrievalResult> results) {
            super(KnowledgeBaseConfig.builder().kbId(kbId).indexType("vector").build());
            this.results = results;
        }

        @Override
        public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> retrieve(
                String query,
                RetrievalConfig config,
                Map<String, Object> kwargs
        ) {
            if (results == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Error"));
            }
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    /**
     * Mirrors Python's {@code mock_llm_client = AsyncMock()} fixture in
     * {@code tests/unit_tests/core/retrieval/test_simple_knowledge_base.py}.
     */
    private static final class FakeModelClient extends BaseModelClient {
        private final Queue<String> responses = new ArrayDeque<>();

        private FakeModelClient(List<String> responses) {
            super(new ModelRequestConfig(), ModelClientConfig.builder()
                    .clientProvider("OpenAI")
                    .apiKey("test-key")
                    .apiBase("http://localhost")
                    .verifySsl(false)
                    .build());
            this.responses.addAll(responses);
        }

        @Override
        public AssistantMessage invoke(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            return new AssistantMessage(responses.isEmpty() ? "{\"sufficient\": true}" : responses.remove());
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String model,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages,
                String model,
                String size,
                String negativePrompt,
                int n,
                boolean promptExtend,
                boolean watermark,
                int seed,
                Map<String, Object> kwargs
        ) {
            return null;
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages,
                String model,
                String voice,
                String languageType,
                Map<String, Object> kwargs
        ) {
            return null;
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages,
                String imgUrl,
                String audioUrl,
                String model,
                String size,
                String resolution,
                int duration,
                boolean promptExtend,
                boolean watermark,
                String negativePrompt,
                Integer seed,
                Map<String, Object> kwargs
        ) {
            return null;
        }
    }
}
