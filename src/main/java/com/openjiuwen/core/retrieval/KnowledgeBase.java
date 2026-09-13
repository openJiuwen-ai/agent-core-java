/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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
 * 
 * @since 0.1.7
 */
public abstract class KnowledgeBase implements AutoCloseable {
    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected final KnowledgeBaseConfig config;

    /**
     * vectorStore.
     * 
     * @since 0.1.7
     */
    protected VectorStore vectorStore;

    /**
     * embedModel.
     * 
     * @since 0.1.7
     */
    protected Embedding embedModel;

    /**
     * parser.
     * 
     * @since 0.1.7
     */
    protected Parser parser;

    /**
     * chunker.
     * 
     * @since 0.1.7
     */
    protected Chunker chunker;

    /**
     * extractor.
     * 
     * @since 0.1.7
     */
    protected Extractor extractor;

    /**
     * indexManager.
     * 
     * @since 0.1.7
     */
    protected Indexer indexManager;
    private boolean autoResolvedIndexManager;

    /**
     * llmClient.
     * 
     * @since 0.1.7
     */
    protected BaseModelClient llmClient;

    /**
     * retriever.
     * 
     * @since 0.1.7
     */
    protected Retriever retriever;

    /**
     * strictValidation.
     * 
     * @since 0.1.7
     */
    protected boolean strictValidation = true;

    /**
     * KnowledgeBase.
     * 
     * @param config config
     * @since 0.1.7
     */
    protected KnowledgeBase(KnowledgeBaseConfig config) {
        this(config, null, null, null, null, null, null, null, null);
    }

    /**
     * KnowledgeBase.
     * 
     * @param config config
     * @param vectorStore vectorStore
     * @param embedModel embedModel
     * @param parser parser
     * @param chunker chunker
     * @param extractor extractor
     * @param indexManager indexManager
     * @param llmClient llmClient
     * @param retriever retriever
     * @since 0.1.7
     */
    protected KnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser,
            Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient,
            Retriever retriever) {
        this(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, retriever, true);
    }

    /**
     * KnowledgeBase.
     * 
     * @param config config
     * @param vectorStore vectorStore
     * @param embedModel embedModel
     * @param parser parser
     * @param chunker chunker
     * @param extractor extractor
     * @param indexManager indexManager
     * @param llmClient llmClient
     * @param retriever retriever
     * @param strictValidation strictValidation
     * @since 0.1.7
     */
    protected KnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser,
            Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient, Retriever retriever,
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

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public KnowledgeBaseConfig getConfig() {
        return config;
    }

    /**
     * getVectorStore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public VectorStore getVectorStore() {
        return vectorStore;
    }

    /**
     * setVectorStore.
     * 
     * @param vectorStore vectorStore
     * @since 0.1.7
     */
    public void setVectorStore(VectorStore vectorStore) {
        if (autoResolvedIndexManager) {
            this.indexManager = null;
            this.autoResolvedIndexManager = false;
        }
        this.vectorStore = vectorStore;
        validateIndex();
    }

    /**
     * getEmbedModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Embedding getEmbedModel() {
        return embedModel;
    }

    /**
     * setEmbedModel.
     * 
     * @param embedModel embedModel
     * @since 0.1.7
     */
    public void setEmbedModel(Embedding embedModel) {
        this.embedModel = embedModel;
    }

    /**
     * getParser.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Parser getParser() {
        return parser;
    }

    /**
     * setParser.
     * 
     * @param parser parser
     * @since 0.1.7
     */
    public void setParser(Parser parser) {
        this.parser = parser;
    }

    /**
     * getChunker.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Chunker getChunker() {
        return chunker;
    }

    /**
     * setChunker.
     * 
     * @param chunker chunker
     * @since 0.1.7
     */
    public void setChunker(Chunker chunker) {
        this.chunker = chunker;
    }

    /**
     * getExtractor.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Extractor getExtractor() {
        return extractor;
    }

    /**
     * setExtractor.
     * 
     * @param extractor extractor
     * @since 0.1.7
     */
    public void setExtractor(Extractor extractor) {
        this.extractor = extractor;
    }

    /**
     * getIndexManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Indexer getIndexManager() {
        return resolveIndexManager();
    }

    /**
     * setIndexManager.
     * 
     * @param indexManager indexManager
     * @since 0.1.7
     */
    public void setIndexManager(Indexer indexManager) {
        this.indexManager = indexManager;
        this.autoResolvedIndexManager = false;
        validateIndex();
    }

    /**
     * getLlmClient.
     * 
     * @return the result
     * @since 0.1.7
     */
    public BaseModelClient getLlmClient() {
        return llmClient;
    }

    /**
     * setLlmClient.
     * 
     * @param llmClient llmClient
     * @since 0.1.7
     */
    public void setLlmClient(BaseModelClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * getRetriever.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Retriever getRetriever() {
        return retriever;
    }

    /**
     * setRetriever.
     * 
     * @param retriever retriever
     * @since 0.1.7
     */
    public void setRetriever(Retriever retriever) {
        this.retriever = retriever;
    }

    /**
     * parseFiles.
     * 
     * @param filePaths filePaths
     * @return the result
     * @since 0.1.7
     */
    public List<Document> parseFiles(List<String> filePaths) {
        return parseFiles(filePaths, Map.of());
    }

    /**
     * parseFiles.
     * 
     * @param filePaths filePaths
     * @param options options
     * @return the result
     * @since 0.1.7
     */
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

                // Ignore.
            }
        }
        return documents;
    }

    /**
     * parseUrls.
     * 
     * @param urls urls
     * @return the result
     * @since 0.1.7
     */
    public List<Document> parseUrls(List<String> urls) {
        return parseUrls(urls, Map.of());
    }

    /**
     * parseUrls.
     * 
     * @param urls urls
     * @param options options
     * @return the result
     * @since 0.1.7
     */
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
                documents.addAll(parser.parse(url, UUID.randomUUID().toString(), llmClient,
                        options == null ? Map.of() : options));
            } catch (Exception ignored) {

                // Ignore.
            }
        }
        return documents;
    }

    /**
     * isStrictValidation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isStrictValidation() {
        return strictValidation;
    }

    /**
     * setStrictValidation.
     * 
     * @param strictValidation strictValidation
     * @since 0.1.7
     */
    public void setStrictValidation(boolean strictValidation) {
        this.strictValidation = strictValidation;
    }

    /**
     * Delete a collection from current database.
     * 
     * @param collection collection
     * @since 0.1.7
     */
    public void deleteCollection(String collection) {
        if (vectorStore == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "vector_store is required for delete_collection");
        }
        vectorStore.deleteTable(collection);
    }

    /**
     * addDocuments.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    public abstract List<String> addDocuments(List<Document> documents);

    /**
     * retrieve.
     * 
     * @param query query
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public abstract List<RetrievalResult> retrieve(String query, RetrievalConfig config);

    /**
     * deleteDocuments.
     * 
     * @param docIds docIds
     * @return the result
     * @since 0.1.7
     */
    public abstract boolean deleteDocuments(List<String> docIds);

    /**
     * updateDocuments.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    public abstract List<String> updateDocuments(List<Document> documents);

    /**
     * getStatistics.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract Map<String, Object> getStatistics();

    /**
     * close.
     * 
     * @since 0.1.7
     */
    @Override
    public void close() {
        closeQuietly(retriever);
        closeQuietly(vectorStore);
        closeQuietly(indexManager);
    }

    /**
     * validateIndex.
     * 
     * @since 0.1.7
     */
    protected void validateIndex() {
        if (vectorStore == null || indexManager == null) {
            return;
        }
        compareConfig("database_name", vectorStore.getDatabaseName(), indexManager.getDatabaseName(), vectorStore,
                indexManager);
        compareConfig("distance_metric", vectorStore.getDistanceMetric(), indexManager.getDistanceMetric(), vectorStore,
                indexManager);
        compareConfig("text_field", vectorStore.getTextField(), indexManager.getTextField(), vectorStore, indexManager);
        compareConfig("vector_field", vectorStore.getVectorField(), indexManager.getVectorField(), vectorStore,
                indexManager);
        compareConfig("sparse_vector_field", vectorStore.getSparseVectorField(), indexManager.getSparseVectorField(),
                vectorStore, indexManager);
        compareConfig("metadata_field", vectorStore.getMetadataField(), indexManager.getMetadataField(), vectorStore,
                indexManager);
        compareConfig("doc_id_field", vectorStore.getDocIdField(), indexManager.getDocIdField(), vectorStore,
                indexManager);
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
    }

    /**
     * compareConfig.
     * 
     * @param field field
     * @param left left
     * @param right right
     * @param leftOwner leftOwner
     * @param rightOwner rightOwner
     * @since 0.1.7
     */
    protected static void compareConfig(String field, Object left, Object right, IndexBackendConfig leftOwner,
            IndexBackendConfig rightOwner) {
        if (left == null && right == null) {
            return;
        }
        if (left == null || right == null || !left.equals(right)) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "incompatible " + field + " configs between " + leftOwner.getClass().getSimpleName() + "=" + left
                            + " and " + rightOwner.getClass().getSimpleName() + "=" + right);
        }
    }

    /**
     * resolveIndexManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected Indexer resolveIndexManager() {
        if (indexManager != null || vectorStore == null) {
            return indexManager;
        }
        indexManager = IndexerFactory.createIndexer(vectorStore);
        autoResolvedIndexManager = true;
        validateIndex();
        return indexManager;
    }

    /**
     * requireIndexManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    protected Indexer requireIndexManager() {
        Indexer activeIndexManager = resolveIndexManager();
        if (activeIndexManager == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND,
                    "index_manager is required");
        }
        return activeIndexManager;
    }

    /**
     * closeQuietly.
     * 
     * @param closeable closeable
     * @since 0.1.7
     */
    protected static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {

            // Ignore.
        }
    }
}
