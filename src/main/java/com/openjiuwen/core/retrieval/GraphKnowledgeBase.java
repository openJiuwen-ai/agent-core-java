/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.async.FutureList;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.AgenticRetriever;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code GraphKnowledgeBase} in
 * {@code openjiuwen/core/retrieval/graph_knowledge_base.py}.
 */
public class GraphKnowledgeBase extends KnowledgeBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphKnowledgeBase.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Retriever chunkRetriever;
    private final Retriever tripleRetriever;
    private GraphRetriever graphRetriever;

    public GraphKnowledgeBase(KnowledgeBaseConfig config) {
        this(config, null, null, null, null, null, null, null, null, null);
    }

    public GraphKnowledgeBase(
            KnowledgeBaseConfig config,
            VectorStore vectorStore,
            Embedding embedModel,
            Parser parser,
            Chunker chunker,
            Extractor extractor,
            Indexer indexManager,
            BaseModelClient llmClient,
            Retriever chunkRetriever,
            Retriever tripleRetriever
    ) {
        super(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient);
        this.chunkRetriever = chunkRetriever;
        this.tripleRetriever = tripleRetriever;
    }

    @Override
    public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
        if (chunker == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_CHUNKER_NOT_FOUND,
                    "error_msg",
                    "chunker is required for add_documents"
            );
        }
        if (indexManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND,
                    "error_msg",
                    "index_manager is required for add_documents"
            );
        }
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }

        List<String> docIds = new ArrayList<>();
        for (Document doc : documents) {
            if (doc.getId_() == null || doc.getId_().isBlank()) {
                doc.setId_(UUID.randomUUID().toString());
            }
            docIds.add(doc.getId_());
        }
        List<TextChunk> chunks = chunker.chunkDocuments(documents);
        LOGGER.info("Chunked {} documents into {} chunks", documents.size(), chunks.size());

        Map<String, Object> buildOptions = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        buildOptions.put("database_name", databaseNameFromVectorStore());
        IndexConfig chunkIndexConfig = IndexConfig.builder()
                .indexName(chunkIndexName())
                .indexType(config.getIndexType())
                .useCaptionForImages(config.isUseCaptionForImages())
                .build();
        return indexManager.buildIndex(chunks, chunkIndexConfig, embedModel, buildOptions)
                .thenCompose(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        throw ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_KB_CHUNK_INDEX_BUILD_EXECUTION_ERROR,
                                "error_msg",
                                "Failed to build chunk index"
                        );
                    }
                    if (!config.isUseGraph() || extractor == null) {
                        return CompletableFuture.completedFuture(List.copyOf(docIds));
                    }
                    LOGGER.info("Extracting triples for graph index...");
                    return extractor.extract(chunks)
                            .thenCompose(triples -> buildTripleIndex(triples, buildOptions))
                            .thenApply(ignored -> List.copyOf(docIds));
                });
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> retrieve(
            String query,
            RetrievalConfig retrievalConfig,
            Map<String, Object> kwargs
    ) {
        RetrievalConfig activeConfig = retrievalConfig == null ? new RetrievalConfig() : retrievalConfig;
        boolean useGraph = activeConfig.getUseGraph() != null ? activeConfig.getUseGraph() : config.isUseGraph();
        if (!useGraph) {
            SimpleKnowledgeBase baseKb = new SimpleKnowledgeBase(
                    config,
                    vectorStore,
                    embedModel,
                    parser,
                    chunker,
                    null,
                    indexManager,
                    llmClient,
                    null
            );
            return baseKb.retrieve(query, activeConfig, kwargs);
        }

        GraphRetriever graph = graphRetriever;
        if (graph == null) {
            if (vectorStore == null) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                        "error_msg",
                        "vector_store is required for graph retrieval"
                );
            }
            graph = new GraphRetriever(
                    chunkRetriever,
                    tripleRetriever,
                    vectorStore,
                    embedModel,
                    chunkIndexName(),
                    tripleIndexName()
            );
            graph.setIndexType(config.getIndexType());
            graphRetriever = graph;
        }

        Retriever activeRetriever = activeConfig.isAgentic()
                ? new AgenticRetriever(graph, llmClient)
                : graph;
        Map<String, Object> options = optionsFrom(activeConfig, kwargs);
        String mode = retrievalMode();
        return CompletableFuture.completedFuture(activeRetriever.retrieve(
                query,
                activeConfig.getTopK(),
                activeConfig.getScoreThreshold(),
                mode,
                options
        ));
    }

    @Override
    public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
        if (indexManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND,
                    "error_msg",
                    "index_manager is required for delete_documents"
            );
        }
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }

        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(Boolean.TRUE);
        for (String docId : docIds) {
            chain = chain.thenCompose(previous -> indexManager.deleteIndex(
                            docId,
                            chunkIndexName(),
                            kwargs == null ? Map.of() : kwargs
                    )
                    .thenCompose(chunkDeleted -> {
                        boolean success = previous && Boolean.TRUE.equals(chunkDeleted);
                        if (!config.isUseGraph()) {
                            return CompletableFuture.completedFuture(success);
                        }
                        return indexManager.deleteIndex(docId, tripleIndexName(), kwargs == null ? Map.of() : kwargs)
                                .thenApply(tripleDeleted -> {
                                    if (!Boolean.TRUE.equals(tripleDeleted)) {
                                        LOGGER.warn("Failed to delete triples for doc_id={}", docId);
                                    }
                                    return success;
                                });
                    }));
        }
        return chain;
    }

    @Override
    public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
        List<String> docIds = documents.stream().map(Document::getId_).toList();
        return deleteDocuments(docIds, kwargs).thenCompose(ignored -> addDocuments(documents, kwargs));
    }

    @Override
    protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("kb_id", config.getKbId());
        stats.put("index_type", config.getIndexType());
        stats.put("use_graph", config.isUseGraph());
        if (indexManager == null) {
            stats.put("index_exists", false);
            return CompletableFuture.completedFuture(stats);
        }
        CompletableFuture<Map<String, Object>> chunkInfoFuture = indexManager.getIndexInfo(chunkIndexName());
        CompletableFuture<Map<String, Object>> tripleInfoFuture = config.isUseGraph()
                ? indexManager.getIndexInfo(tripleIndexName())
                : CompletableFuture.completedFuture(null);
        return chunkInfoFuture.thenCombine(tripleInfoFuture, (chunkInfo, tripleInfo) -> {
            stats.put("chunk_index_info", chunkInfo);
            stats.put("triple_index_info", tripleInfo);
            stats.put("has_parser", parser != null);
            stats.put("has_chunker", chunker != null);
            stats.put("has_extractor", extractor != null);
            stats.put("has_embed_model", embedModel != null);
            stats.put("has_vector_store", vectorStore != null);
            stats.put("has_graph_retriever", graphRetriever != null);
            return stats;
        });
    }

    @Override
    public CompletableFuture<Void> closeAsync() {
        if (graphRetriever != null) {
            graphRetriever.close();
        }
        if (chunkRetriever != null) {
            chunkRetriever.close();
        }
        if (tripleRetriever != null) {
            tripleRetriever.close();
        }
        return super.closeAsync();
    }

    public static FutureList<String> retrieveMultiGraphKb(
            List<? extends KnowledgeBase> knowledgeBases,
            String query,
            RetrievalConfig config,
            Integer topK
    ) {
        return SimpleKnowledgeBase.retrieveMultiKb(knowledgeBases, query, config, topK);
    }

    public static CompletableFuture<List<String>> retrieveMultiGraphKbAsync(
            List<? extends KnowledgeBase> knowledgeBases,
            String query,
            RetrievalConfig config,
            Integer topK
    ) {
        return SimpleKnowledgeBase.retrieveMultiKbAsync(knowledgeBases, query, config, topK);
    }

    public static FutureList<MultiKBRetrievalResult> retrieveMultiGraphKbWithSource(
            List<? extends KnowledgeBase> knowledgeBases,
            String query,
            RetrievalConfig config,
            Integer topK
    ) {
        return SimpleKnowledgeBase.retrieveMultiKbWithSource(knowledgeBases, query, config, topK);
    }

    public static CompletableFuture<List<MultiKBRetrievalResult>> retrieveMultiGraphKbWithSourceAsync(
            List<? extends KnowledgeBase> knowledgeBases,
            String query,
            RetrievalConfig config,
            Integer topK
    ) {
        return SimpleKnowledgeBase.retrieveMultiKbWithSourceAsync(knowledgeBases, query, config, topK);
    }

    private CompletableFuture<Void> buildTripleIndex(List<Triple> triples, Map<String, Object> buildOptions) {
        if (triples == null || triples.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("Extracted {} triples", triples.size());
        IndexConfig tripleIndexConfig = IndexConfig.builder()
                .indexName(tripleIndexName())
                .indexType(config.getIndexType())
                .useCaptionForImages(true)
                .build();
        return indexManager.buildIndex(tripleChunks(triples), tripleIndexConfig, embedModel, buildOptions)
                .thenAccept(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        throw ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_KB_TRIPLE_INDEX_BUILD_EXECUTION_ERROR,
                                "error_msg",
                                "Failed to build triple index"
                        );
                    }
                });
    }

    private List<TextChunk> tripleChunks(List<Triple> triples) {
        List<TextChunk> chunks = new ArrayList<>();
        for (int index = 0; index < triples.size(); index++) {
            Triple triple = triples.get(index);
            String text = triple.getSubject() + " " + triple.getPredicate() + " " + triple.getObject();
            Map<String, Object> metadata = new LinkedHashMap<>(triple.getMetadata());
            metadata.put("triple", serializeTriple(triple));
            metadata.put("chunk_index", index);
            metadata.put("chunk_id", String.valueOf(metadata.getOrDefault("chunk_id", "")));
            chunks.add(new TextChunk(
                    UUID.randomUUID().toString(),
                    text,
                    String.valueOf(metadata.getOrDefault("doc_id", "")),
                    metadata
            ));
        }
        return chunks;
    }

    private Map<String, Object> optionsFrom(RetrievalConfig retrievalConfig, Map<String, Object> kwargs) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (kwargs != null) {
            options.putAll(kwargs);
        }
        if (retrievalConfig.getFilters() != null) {
            options.put("filters", retrievalConfig.getFilters());
        }
        options.put("graph_expansion", retrievalConfig.isGraphExpansion());
        return options;
    }

    private String retrievalMode() {
        return switch (config.getIndexType()) {
            case "vector" -> "vector";
            case "bm25" -> "sparse";
            default -> "hybrid";
        };
    }

    private String chunkIndexName() {
        return "kb_" + config.getKbId() + "_chunks";
    }

    private String tripleIndexName() {
        return "kb_" + config.getKbId() + "_triples";
    }

    private String databaseNameFromVectorStore() {
        Object vectorConfig = readNoArg(vectorStore, "getConfig");
        Object databaseName = readNoArg(vectorConfig, "getDatabaseName");
        return databaseName instanceof String text ? text : "";
    }

    private static Object readNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() == 0) {
                return method.invoke(target);
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static String serializeTriple(Triple triple) {
        try {
            return MAPPER.writeValueAsString(List.of(
                    triple.getSubject(),
                    triple.getPredicate(),
                    triple.getObject()
            ));
        } catch (JsonProcessingException exception) {
            return "[\"" + triple.getSubject() + "\",\"" + triple.getPredicate() + "\",\"" + triple.getObject() + "\"]";
        }
    }
}
