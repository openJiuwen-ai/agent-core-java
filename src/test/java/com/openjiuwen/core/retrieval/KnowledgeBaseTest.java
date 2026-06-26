/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code KnowledgeBase} in
 * {@code openjiuwen/core/retrieval/knowledge_base.py}.
 *
 * <p>Scenarios also mirror Python's {@code TestKnowledgeBase} in
 * {@code tests/unit_tests/core/retrieval/test_knowledge_base.py}.</p>
 */
class KnowledgeBaseTest {

    @Test
    void initializesWithConfigAndNoOptionalComponents() {
        KnowledgeBaseConfig config = config();

        TestKnowledgeBase kb = new TestKnowledgeBase(config);

        assertSame(config, kb.getConfig());
        assertNull(kb.getVectorStore());
        assertNull(kb.getEmbedModel());
        assertNull(kb.getParser());
        assertNull(kb.getChunker());
        assertNull(kb.getExtractor());
        assertNull(kb.getIndexManager());
        assertNull(kb.getLlmClient());
    }

    @Test
    void initializesWithInjectedComponents() {
        KnowledgeBaseConfig config = config();
        FakeVectorStore vectorStore = new FakeVectorStore("db");
        FakeEmbedding embedding = new FakeEmbedding();
        SelectiveParser parser = new SelectiveParser();
        FakeChunker chunker = new FakeChunker();
        FakeExtractor extractor = new FakeExtractor();
        FakeIndexer indexer = new FakeIndexer("db");
        FakeModelClient llmClient = new FakeModelClient();

        TestKnowledgeBase kb = new TestKnowledgeBase(
                config,
                vectorStore,
                embedding,
                parser,
                chunker,
                extractor,
                indexer,
                llmClient
        );

        assertSame(vectorStore, kb.getVectorStore());
        assertSame(embedding, kb.getEmbedModel());
        assertSame(parser, kb.getParser());
        assertSame(chunker, kb.getChunker());
        assertSame(extractor, kb.getExtractor());
        assertSame(indexer, kb.getIndexManager());
        assertSame(llmClient, kb.getLlmClient());
    }

    @Test
    void parseFilesDelegatesToParserAndContinuesAfterFailures() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        kb.setParser(new SelectiveParser());

        List<Document> documents = kb.parseFiles(List.of("first.txt", "bad.txt", "second.txt")).join();

        assertEquals(2, documents.size());
        assertEquals("first.txt", documents.get(0).getText());
        assertEquals("second.txt", documents.get(1).getText());
        assertFalse(documents.get(0).getId_().isBlank());
    }

    @Test
    void parseFilesUsesPythonSlashBasenameAndContinuesAfterSynchronousFailure() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        SelectiveParser parser = new SelectiveParser();
        kb.setParser(parser);

        List<Document> documents = kb.parseFiles(List.of(
                "dir/nested.txt",
                "sync-bad.txt",
                "dir\\windows.txt"
        )).join();

        assertEquals(2, documents.size());
        assertEquals(List.of("nested.txt", "dir\\windows.txt"), parser.fileNamesSeen);
    }

    @Test
    void parseUrlsSkipsUnsupportedUrlsAndUsesGeneratedDocIds() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        kb.setParser(new SelectiveParser());

        List<Document> documents = kb.parseUrls(List.of("file://local", "https://example.test/doc")).join();

        assertEquals(1, documents.size());
        assertEquals("https://example.test/doc", documents.getFirst().getText());
        assertFalse(documents.getFirst().getId_().isBlank());
    }

    @Test
    void parseMethodsRequireParser() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());

        assertThrows(BaseError.class, () -> kb.parseFiles(List.of("file.txt")));
        assertThrows(BaseError.class, () -> kb.parseUrls(List.of("https://example.test")));
    }

    @Test
    void deleteCollectionRequiresVectorStore() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());

        assertThrows(BaseError.class, () -> kb.deleteCollection("collection"));
    }

    @Test
    void validatesMatchingVectorStoreAndIndexerFields() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        kb.setVectorStore(new FakeVectorStore("db"));

        kb.setIndexManager(new FakeIndexer("db"));

        assertEquals("db", kb.getVectorStore().getClass().cast(kb.getVectorStore()).getClass().getSimpleName().equals("FakeVectorStore")
                ? ((FakeVectorStore) kb.getVectorStore()).getDatabaseName()
                : "");
    }

    @Test
    void mismatchedVectorStoreAndIndexerFieldsFailValidation() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        kb.setVectorStore(new FakeVectorStore("left"));

        assertThrows(BaseError.class, () -> kb.setIndexManager(new FakeIndexer("right")));
    }

    @Test
    void mismatchedVectorStoreAndIndexerDistanceMetricFailsValidation() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        kb.setVectorStore(new FakeVectorStore("db", "cosine"));

        assertThrows(BaseError.class, () -> kb.setIndexManager(new FakeIndexer("db", "ip")));
    }

    @Test
    void closeInvokesVectorStoreClose() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        FakeVectorStore vectorStore = new FakeVectorStore("db");
        kb.setVectorStore(vectorStore);

        kb.close().join();

        assertTrue(vectorStore.closed);
    }

    @Test
    void closeInvokesIndexManagerClose() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        FakeVectorStore vectorStore = new FakeVectorStore("db");
        FakeIndexer indexer = new FakeIndexer("db");
        kb.setVectorStore(vectorStore);
        kb.setIndexManager(indexer);

        kb.close().join();

        assertTrue(vectorStore.closed);
        assertTrue(indexer.closed);
    }

    @Test
    void closeWithNoComponentsDoesNotThrow() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());

        assertDoesNotThrow(() -> kb.close().join());
    }

    @Test
    void closeSwallowsSynchronousCloseFailures() {
        TestKnowledgeBase kb = new TestKnowledgeBase(config());
        FakeVectorStore vectorStore = new FakeVectorStore("db");
        vectorStore.throwOnClose = true;
        kb.setVectorStore(vectorStore);

        assertDoesNotThrow(() -> kb.close().join());
    }

    private static KnowledgeBaseConfig config() {
        return KnowledgeBaseConfig.builder()
                .kbId("kb")
                .indexType("hybrid")
                .build();
    }

    private static final class TestKnowledgeBase extends KnowledgeBase {

        private TestKnowledgeBase(KnowledgeBaseConfig config) {
            super(config);
        }

        private TestKnowledgeBase(
                KnowledgeBaseConfig config,
                VectorStore vectorStore,
                Embedding embedModel,
                Parser parser,
                Chunker chunker,
                Extractor extractor,
                Indexer indexManager,
                BaseModelClient llmClient
        ) {
            super(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient);
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
            return CompletableFuture.completedFuture(List.of());
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
        public CompletableFuture<Map<String, Object>> getStatistics() {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class FakeEmbedding extends Embedding {
        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of(0.1d));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of(List.of(0.1d)));
        }

        @Override
        public int getDimension() {
            return 1;
        }
    }

    private static final class FakeChunker extends Chunker {
        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }
    }

    private static final class FakeExtractor extends Extractor {
        @Override
        public CompletableFuture<List<Triple>> extract(List<TextChunk> chunks) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private static final class FakeModelClient extends BaseModelClient {
        private FakeModelClient() {
            super(null, null);
        }

        @Override
        protected void validateConfig() {
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
            return null;
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

    private static final class SelectiveParser extends Parser {
        private final List<String> fileNamesSeen = new ArrayList<>();

        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            if ("sync-bad.txt".equals(doc)) {
                throw new IllegalStateException("sync broken");
            }
            if ("bad.txt".equals(doc)) {
                return CompletableFuture.failedFuture(new IllegalStateException("broken"));
            }
            fileNamesSeen.add((String) new LinkedHashMap<>(options).get("file_name"));
            return CompletableFuture.completedFuture(List.of(new Document(docId, doc, Map.of())));
        }

        @Override
        public boolean supports(String doc) {
            return doc != null && doc.startsWith("https://");
        }
    }

    static final class FakeVectorStore implements VectorStore {
        private final String databaseName;
        private final String distanceMetric;
        private boolean closed;
        private boolean throwOnClose;

        private FakeVectorStore(String databaseName) {
            this(databaseName, "cosine");
        }

        private FakeVectorStore(String databaseName, String distanceMetric) {
            this.databaseName = databaseName;
            this.distanceMetric = distanceMetric;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getDistanceMetric() {
            return distanceMetric;
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
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStoreFilter filters,
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
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            if (throwOnClose) {
                throw new IllegalStateException("close failed");
            }
            closed = true;
        }
    }

    static final class FakeIndexer extends Indexer {
        private final String databaseName;
        private final String distanceMetric;
        private boolean closed;

        private FakeIndexer(String databaseName) {
            this(databaseName, "cosine");
        }

        private FakeIndexer(String databaseName, String distanceMetric) {
            this.databaseName = databaseName;
            this.distanceMetric = distanceMetric;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getDistanceMetric() {
            return distanceMetric;
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
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> updateIndex(
                List<TextChunk> chunks,
                String docId,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> indexExists(String indexName) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
            return CompletableFuture.completedFuture(Map.of());
        }

        public CompletableFuture<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
