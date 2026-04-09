/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.indexer.IndexerFactory;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract knowledge base.
 */
public abstract class KnowledgeBase implements AutoCloseable {

    protected final KnowledgeBaseConfig config;
    protected VectorStore vectorStore;
    protected Embedding embedModel;
    protected Parser parser;
    protected Chunker chunker;
    protected Extractor extractor;
    protected Indexer indexManager;
    private boolean autoResolvedIndexManager;
    protected BaseModelClient llmClient;
    protected Retriever retriever;
    protected boolean strictValidation = true;

    protected KnowledgeBase(KnowledgeBaseConfig config) {
        this(config, null, null, null, null, null, null, null, null);
    }

    protected KnowledgeBase(KnowledgeBaseConfig config,
                            VectorStore vectorStore,
                            Embedding embedModel,
                            Parser parser,
                            Chunker chunker,
                            Extractor extractor,
                            Indexer indexManager,
                            BaseModelClient llmClient,
                            Retriever retriever) {
        this(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, retriever, true);
    }

    protected KnowledgeBase(KnowledgeBaseConfig config,
                            VectorStore vectorStore,
                            Embedding embedModel,
                            Parser parser,
                            Chunker chunker,
                            Extractor extractor,
                            Indexer indexManager,
                            BaseModelClient llmClient,
                            Retriever retriever,
                            boolean strictValidation) {
        if (config == null) {
            throw RetrievalExceptions.validation("KnowledgeBaseConfig is required");
        }
        config.validate();
        this.config = config;
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
        this.parser = parser;
        this.chunker = chunker;
        this.extractor = extractor;
        this.indexManager = indexManager;
        this.llmClient = llmClient;
        this.retriever = retriever;
        this.strictValidation = strictValidation;
        validateIndex();
    }

    public KnowledgeBaseConfig getConfig() {
        return config;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStore vectorStore) {
        if (autoResolvedIndexManager) {
            this.indexManager = null;
            this.autoResolvedIndexManager = false;
        }
        this.vectorStore = vectorStore;
        validateIndex();
    }

    public Embedding getEmbedModel() {
        return embedModel;
    }

    public void setEmbedModel(Embedding embedModel) {
        this.embedModel = embedModel;
    }

    public Parser getParser() {
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public Chunker getChunker() {
        return chunker;
    }

    public void setChunker(Chunker chunker) {
        this.chunker = chunker;
    }

    public Extractor getExtractor() {
        return extractor;
    }

    public void setExtractor(Extractor extractor) {
        this.extractor = extractor;
    }

    public Indexer getIndexManager() {
        return resolveIndexManager();
    }

    public void setIndexManager(Indexer indexManager) {
        this.indexManager = indexManager;
        this.autoResolvedIndexManager = false;
        validateIndex();
    }

    public BaseModelClient getLlmClient() {
        return llmClient;
    }

    public void setLlmClient(BaseModelClient llmClient) {
        this.llmClient = llmClient;
    }

    public Retriever getRetriever() {
        return retriever;
    }

    public void setRetriever(Retriever retriever) {
        this.retriever = retriever;
    }

    public List<Document> parseFiles(List<String> filePaths) {
        return parseFiles(filePaths, Map.of());
    }

    public List<Document> parseFiles(List<String> filePaths, Map<String, Object> options) {
        if (parser == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_PARSER_NOT_FOUND, "parser is required");
        }
        List<Document> documents = new ArrayList<>();
        if (filePaths == null) {
            return documents;
        }
        for (String filePath : filePaths) {
            try {
                String fileName = filePath == null ? "" : java.nio.file.Path.of(filePath).getFileName().toString();
                Map<String, Object> parseOptions = new java.util.LinkedHashMap<>();
                if (options != null) {
                    parseOptions.putAll(options);
                }
                parseOptions.putIfAbsent("file_name", fileName);
                documents.addAll(parser.parse(filePath, UUID.randomUUID().toString(), llmClient, parseOptions));
            } catch (Exception ignored) {
            }
        }
        return documents;
    }

    public List<Document> parseUrls(List<String> urls) {
        return parseUrls(urls, Map.of());
    }

    public List<Document> parseUrls(List<String> urls, Map<String, Object> options) {
        if (parser == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_PARSER_NOT_FOUND, "parser is required");
        }
        List<Document> documents = new ArrayList<>();
        if (urls == null) {
            return documents;
        }
        for (String url : urls) {
            try {
                if (!parser.supports(url)) {
                    continue;
                }
                documents.addAll(parser.parse(url, UUID.randomUUID().toString(), llmClient, options == null ? Map.of() : options));
            } catch (Exception ignored) {
            }
        }
        return documents;
    }

    public boolean isStrictValidation() {
        return strictValidation;
    }

    public void setStrictValidation(boolean strictValidation) {
        this.strictValidation = strictValidation;
    }

    /**
     * Delete a collection from current database.
     */
    public void deleteCollection(String collection) {
        if (vectorStore == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "vector_store is required for delete_collection");
        }
        vectorStore.deleteTable(collection);
    }

    public abstract List<String> addDocuments(List<Document> documents);

    public abstract List<RetrievalResult> retrieve(String query, RetrievalConfig config);

    public abstract boolean deleteDocuments(List<String> docIds);

    public abstract List<String> updateDocuments(List<Document> documents);

    public abstract Map<String, Object> getStatistics();

    @Override
    public void close() {
        closeQuietly(retriever);
        closeQuietly(vectorStore);
        closeQuietly(indexManager);
    }

    protected void validateIndex() {
        if (vectorStore == null || indexManager == null) {
            return;
        }
        compareConfig("database_name", vectorStore.getDatabaseName(), indexManager.getDatabaseName(), vectorStore, indexManager);
        compareConfig("distance_metric", vectorStore.getDistanceMetric(), indexManager.getDistanceMetric(), vectorStore, indexManager);
        compareConfig("text_field", vectorStore.getTextField(), indexManager.getTextField(), vectorStore, indexManager);
        compareConfig("vector_field", vectorStore.getVectorField(), indexManager.getVectorField(), vectorStore, indexManager);
        compareConfig(
                "sparse_vector_field",
                vectorStore.getSparseVectorField(),
                indexManager.getSparseVectorField(),
                vectorStore,
                indexManager);
        compareConfig("metadata_field", vectorStore.getMetadataField(), indexManager.getMetadataField(), vectorStore, indexManager);
        compareConfig("doc_id_field", vectorStore.getDocIdField(), indexManager.getDocIdField(), vectorStore, indexManager);
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
    }

    protected static void compareConfig(String field,
                                        Object left,
                                        Object right,
                                        IndexBackendConfig leftOwner,
                                        IndexBackendConfig rightOwner) {
        if (left == null && right == null) {
            return;
        }
        if (left == null || right == null || !left.equals(right)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "incompatible " + field + " configs between "
                            + leftOwner.getClass().getSimpleName() + "=" + left
                            + " and " + rightOwner.getClass().getSimpleName() + "=" + right);
        }
    }

    protected Indexer resolveIndexManager() {
        if (indexManager != null || vectorStore == null) {
            return indexManager;
        }
        indexManager = IndexerFactory.createIndexer(vectorStore);
        autoResolvedIndexManager = true;
        validateIndex();
        return indexManager;
    }

    protected Indexer requireIndexManager() {
        Indexer activeIndexManager = resolveIndexManager();
        if (activeIndexManager == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND,
                    "index_manager is required");
        }
        return activeIndexManager;
    }

    protected static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
