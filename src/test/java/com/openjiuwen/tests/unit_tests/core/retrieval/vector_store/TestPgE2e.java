/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PG vector store E2E test cases.
 *
 * <p>Mirrors Python's {@code test_pg_e2e.py} in
 * {@code tests.unit_tests.core.retrieval.vector_store.test_pg_e2e}.</p>
 */
@DisplayName("PGVectorStore E2E Tests")
class TestPgE2e {

    @Nested
    @DisplayName("E2E Tests")
    class E2eTests {

        @Test
        @DisplayName("test_workflow_agent_kb_flow - KB add and retrieve use PGVectorStore")
        void testWorkflowAgentKbFlow() {
            RecordingPGVectorStore pgStore = new RecordingPGVectorStore("pg_collection", "cosine");
            RecordingIndexer indexer = new RecordingIndexer(pgStore);
            SimpleKnowledgeBase kb = knowledgeBase("workflow_kb", pgStore, indexer);

            kb.addDocuments(List.of(new Document(
                    "doc_workflow",
                    "This is a workflow document",
                    Map.of("type", "report"))));

            assertThat(indexer.buildIndexCount).isEqualTo(1);
            assertThat(pgStore.addedRows).hasSize(1);
            assertThat(pgStore.addedRows.get(0).get("text")).isEqualTo("This is a workflow document");

            pgStore.searchResults = List.of(new SearchResult(
                    "chunk_1",
                    "This is a workflow document",
                    0.9,
                    Map.of("doc_id", "doc_workflow", "chunk_id", "chunk_1", "type", "report")));

            List<RetrievalResult> results = kb.retrieve("workflow query", new RetrievalConfig());

            assertThat(pgStore.searchCount).isEqualTo(1);
            assertThat(pgStore.lastQueryVector).containsExactly(0.1f, 0.2f);
            assertThat(pgStore.collectionRequests).contains("kb_workflow_kb_chunks");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getText()).isEqualTo("This is a workflow document");
        }

        @Test
        @DisplayName("test_llm_agent_retrieval - agent tool retrieves from PG-backed KB")
        void testLlmAgentRetrieval() {
            RecordingPGVectorStore pgStore = new RecordingPGVectorStore("agent_collection", "euclidean");
            RecordingIndexer indexer = new RecordingIndexer(pgStore);
            SimpleKnowledgeBase kb = knowledgeBase("agent_kb", pgStore, indexer);
            pgStore.searchResults = List.of(new SearchResult(
                    "doc_1",
                    "Secret Agent Info",
                    0.95,
                    Map.of("doc_id", "doc_1", "chunk_id", "doc_1")));
            MockLLMAgent agent = new MockLLMAgent(List.of(new MockRetrievalTool(kb)));

            String response = agent.act("search for secret info");

            assertThat(response).contains("Found info: Secret Agent Info");
            assertThat(pgStore.searchCount).isEqualTo(1);
            assertThat(pgStore.lastQueryVector).containsExactly(0.1f, 0.2f);
        }
    }

    private static SimpleKnowledgeBase knowledgeBase(String kbId,
                                                     RecordingPGVectorStore pgStore,
                                                     RecordingIndexer indexer) {
        return new SimpleKnowledgeBase(
                new KnowledgeBaseConfig(kbId, "vector", false, 512, 50),
                pgStore,
                new MockEmbedding(),
                null,
                new PassthroughChunker(),
                indexer,
                null,
                null);
    }

    private static final class MockEmbedding implements Embedding {

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

    private static final class PassthroughChunker extends Chunker {

        private PassthroughChunker() {
            super(512, 50);
        }

        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }
    }

    private static final class RecordingIndexer implements Indexer {
        private final RecordingPGVectorStore vectorStore;
        private int buildIndexCount;

        private RecordingIndexer(RecordingPGVectorStore vectorStore) {
            this.vectorStore = vectorStore;
        }

        @Override
        public boolean buildIndex(List<TextChunk> chunks,
                                  IndexConfig config,
                                  Embedding embedModel,
                                  Map<String, Object> options) {
            buildIndexCount++;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (TextChunk chunk : chunks) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", chunk.getId());
                row.put("text", chunk.getText());
                row.put("embedding", embedModel.embedQuery(chunk.getText()));
                row.put("metadata", chunk.getMetadata());
                row.put("doc_id", chunk.getDocId());
                rows.add(row);
            }
            vectorStore.add(rows, null, Map.of());
            return true;
        }

        @Override
        public boolean updateIndex(List<TextChunk> chunks,
                                   String docId,
                                   IndexConfig config,
                                   Embedding embedModel,
                                   Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
            return true;
        }

        @Override
        public boolean indexExists(String indexName) {
            return true;
        }

        @Override
        public Map<String, Object> getIndexInfo(String indexName) {
            return Map.of("count", vectorStore.addedRows.size());
        }

        @Override
        public String getDatabaseName() {
            return vectorStore.getDatabaseName();
        }

        @Override
        public String getDistanceMetric() {
            return vectorStore.getDistanceMetric();
        }

        @Override
        public String getIndexType() {
            return vectorStore.getIndexType();
        }

        @Override
        public String getTextField() {
            return vectorStore.getTextField();
        }

        @Override
        public String getVectorField() {
            return vectorStore.getVectorField();
        }

        @Override
        public String getSparseVectorField() {
            return vectorStore.getSparseVectorField();
        }

        @Override
        public String getMetadataField() {
            return vectorStore.getMetadataField();
        }

        @Override
        public String getDocIdField() {
            return vectorStore.getDocIdField();
        }
    }

    private static final class RecordingPGVectorStore extends PGVectorStore {
        private final List<Map<String, Object>> addedRows = new ArrayList<>();
        private final List<String> collectionRequests = new ArrayList<>();
        private List<SearchResult> searchResults = List.of();
        private List<Float> lastQueryVector = List.of();
        private int searchCount;

        private RecordingPGVectorStore(String collectionName, String distanceMetric) {
            super(
                    new VectorStoreConfig("pgvector", "", collectionName, distanceMetric),
                    mock(DataSource.class),
                    "vector",
                    Map.of("vector_field", "embedding"));
        }

        @Override
        public void checkVectorField() {
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            collectionRequests.add(collectionName);
            setCollectionName(collectionName);
            return this;
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
            addedRows.addAll(data == null ? List.of() : data);
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector,
                                         int topK,
                                         Map<String, Object> filters,
                                         Map<String, Object> options) {
            searchCount++;
            lastQueryVector = queryVector == null ? List.of() : new ArrayList<>(queryVector);
            return searchResults;
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText,
                                               int topK,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            return List.of();
        }
    }

    private static final class MockRetrievalTool {
        private final SimpleKnowledgeBase kb;
        private final String name = "retrieval_tool";

        private MockRetrievalTool(SimpleKnowledgeBase kb) {
            this.kb = kb;
        }

        private String run(String query) {
            List<RetrievalResult> results = kb.retrieve(query, new RetrievalConfig());
            List<String> texts = new ArrayList<>();
            for (RetrievalResult result : results) {
                texts.add(result.getText());
            }
            return String.join("\n", texts);
        }
    }

    private static final class MockLLMAgent {
        private final Map<String, MockRetrievalTool> tools = new LinkedHashMap<>();

        private MockLLMAgent(List<MockRetrievalTool> tools) {
            for (MockRetrievalTool tool : tools) {
                this.tools.put(tool.name, tool);
            }
        }

        private String act(String instruction) {
            if (instruction.contains("search") || instruction.contains("find")) {
                String query = instruction.replace("search for ", "").replace("find ", "");
                return "Found info: " + tools.get("retrieval_tool").run(query);
            }
            return "I don't know.";
        }
    }
}
