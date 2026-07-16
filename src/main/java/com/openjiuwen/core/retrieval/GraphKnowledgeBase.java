/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.LLMTripleExtractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.AgenticRetriever;
import com.openjiuwen.core.retrieval.retriever.GraphRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Knowledge base with optional graph index.
 * 
 * @since 0.1.7
 */
public class GraphKnowledgeBase extends KnowledgeBase {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Retriever chunkRetriever;
    private final Retriever tripleRetriever;
    private GraphRetriever graphRetriever;

    /**
     * GraphKnowledgeBase.
     * 
     * @param config config
     * @since 0.1.7
     */
    public GraphKnowledgeBase(KnowledgeBaseConfig config) {
        this(config, null, null, null, null, null, null, null, null, null);
    }

    /**
     * GraphKnowledgeBase.
     * 
     * @param config config
     * @param vectorStore vectorStore
     * @param embedModel embedModel
     * @param parser parser
     * @param chunker chunker
     * @param extractor extractor
     * @param indexManager indexManager
     * @param llmClient llmClient
     * @param chunkRetriever chunkRetriever
     * @param tripleRetriever tripleRetriever
     * @since 0.1.7
     */
    public GraphKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Embedding embedModel, Parser parser,
            Chunker chunker, Extractor extractor, Indexer indexManager, BaseModelClient llmClient,
            Retriever chunkRetriever, Retriever tripleRetriever) {
        super(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, null);
        this.chunkRetriever = chunkRetriever;
        this.tripleRetriever = tripleRetriever;
    }

    /**
     * addDocuments.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> addDocuments(List<Document> documents) {
        if (chunker == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_CHUNKER_NOT_FOUND, "chunker is required");
        }
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
        Indexer activeIndexManager = requireIndexManager();
        List<Document> normalized = new ArrayList<>();
        List<String> docIds = new ArrayList<>();
        for (Document document : documents) {
            String docId = document.getId() == null || document.getId().isBlank()
                    ? UUID.randomUUID().toString()
                    : document.getId();
            normalized.add(new Document(docId, document.getText(), document.getMetadata()));
            docIds.add(docId);
        }
        List<TextChunk> chunks = chunker.chunkDocuments(normalized);
        boolean isChunkBuilt = activeIndexManager.buildIndex(chunks,
                new IndexConfig(chunkIndexName(), config.getIndexType()), embedModel, Map.of());
        if (!isChunkBuilt) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_CHUNK_INDEX_BUILD_EXECUTION_ERROR,
                    "Failed to build chunk index");
        }
        if (config.isUseGraph()) {
            Extractor activeExtractor = extractor;
            if (activeExtractor == null && llmClient != null) {
                activeExtractor = new LLMTripleExtractor(llmClient, null);
            }
            if (activeExtractor == null) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_TRIPLE_EXTRACTION_PROCESS_ERROR,
                        "extractor is required when use_graph is enabled");
            }
            List<Triple> triples = activeExtractor.extract(chunks, Map.of());
            boolean tripleBuilt = activeIndexManager.buildIndex(tripleChunks(triples),
                    new IndexConfig(tripleIndexName(), config.getIndexType()), embedModel, Map.of());
            if (!tripleBuilt) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_TRIPLE_INDEX_BUILD_EXECUTION_ERROR,
                        "Failed to build triple index");
            }
        }
        return docIds;
    }

    /**
     * retrieve.
     * 
     * @param query query
     * @param retrievalConfig retrievalConfig
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<RetrievalResult> retrieve(String query, RetrievalConfig retrievalConfig) {
        RetrievalConfig config = retrievalConfig == null ? new RetrievalConfig() : retrievalConfig;
        boolean useGraph = config.getUseGraph() != null ? config.getUseGraph() : this.config.isUseGraph();
        if (!useGraph) {
            SimpleKnowledgeBase delegate = new SimpleKnowledgeBase(this.config, vectorStore, embedModel, parser,
                    chunker, indexManager, llmClient, chunkRetriever);
            return delegate.retrieve(query, config);
        }
        GraphRetriever graph = graphRetriever != null
                ? graphRetriever
                : new GraphRetriever(chunkRetriever, tripleRetriever, vectorStore, embedModel, chunkIndexName(),
                        tripleIndexName());
        graph.setIndexType(this.config.getIndexType());
        graphRetriever = graph;
        Retriever activeRetriever = config.isAgentic() ? new AgenticRetriever(graph, llmClient) : graph;
        Map<String, Object> options = new LinkedHashMap<>();
        if (config.getFilters() != null) {
            options.put("filters", config.getFilters());
        }
        options.put("graph_expansion", config.isGraphExpansion());
        return activeRetriever.retrieve(query, config.getTopK(), config.getScoreThreshold(),
                switch (this.config.getIndexType()) {
                    case "vector" -> "vector";
                    case "bm25" -> "sparse";
                    default -> "hybrid";
                }, options);
    }

    /**
     * deleteDocuments.
     * 
     * @param docIds docIds
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean deleteDocuments(List<String> docIds) {
        Indexer activeIndexManager = requireIndexManager();
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
        boolean deleted = true;
        for (String docId : docIds) {
            deleted &= activeIndexManager.deleteIndex(docId, chunkIndexName(), Map.of());
            if (config.isUseGraph()) {
                deleted &= activeIndexManager.deleteIndex(docId, tripleIndexName(), Map.of());
            }
        }
        return deleted;
    }

    /**
     * updateDocuments.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> updateDocuments(List<Document> documents) {
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
        List<String> docIds = new ArrayList<>();
        for (Document document : documents) {
            String docId = document.getId() == null || document.getId().isBlank()
                    ? UUID.randomUUID().toString()
                    : document.getId();
            docIds.add(docId);
            deleteDocuments(List.of(docId));
            addDocuments(List.of(new Document(docId, document.getText(), document.getMetadata())));
        }
        return docIds;
    }

    /**
     * getStatistics.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("kb_id", config.getKbId());
        stats.put("use_graph", config.isUseGraph());
        Indexer activeIndexManager = resolveIndexManager();
        if (activeIndexManager == null) {
            stats.put("index_exists", false);
            return stats;
        }
        stats.put("chunk_index_info", activeIndexManager.getIndexInfo(chunkIndexName()));
        if (config.isUseGraph()) {
            stats.put("triple_index_info", activeIndexManager.getIndexInfo(tripleIndexName()));
        }
        return stats;
    }

    /**
     * close.
     * 
     * @since 0.1.7
     */
    @Override
    public void close() {
        closeQuietly(graphRetriever);
        closeQuietly(chunkRetriever);
        closeQuietly(tripleRetriever);
        super.close();
    }

    /**
     * chunkIndexName.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String chunkIndexName() {
        return "kb_" + config.getKbId() + "_chunks";
    }

    /**
     * tripleIndexName.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String tripleIndexName() {
        return "kb_" + config.getKbId() + "_triples";
    }

    /**
     * tripleChunks.
     * 
     * @param triples triples
     * @return the result
     * @since 0.1.7
     */
    private static List<TextChunk> tripleChunks(List<Triple> triples) {
        List<TextChunk> chunks = new ArrayList<>();
        for (Triple triple : triples) {
            String text = triple.getSubject() + " " + triple.getPredicate() + " " + triple.getObject();
            Map<String, Object> metadata = new LinkedHashMap<>(triple.getMetadata());
            metadata.putIfAbsent("doc_id", metadata.get("doc_id"));
            metadata.putIfAbsent("chunk_id", metadata.get("chunk_id"));
            metadata.put("triple", serializeTriple(triple));
            chunks.add(new TextChunk(UUID.randomUUID().toString(), text,
                    metadata.get("doc_id") == null ? "" : String.valueOf(metadata.get("doc_id")), metadata, null));
        }
        return chunks;
    }

    /**
     * serializeTriple.
     * 
     * @param triple triple
     * @return the result
     * @since 0.1.7
     */
    private static String serializeTriple(Triple triple) {
        try {
            return MAPPER.writeValueAsString(List.of(triple.getSubject(), triple.getPredicate(), triple.getObject()));
        } catch (JsonProcessingException e) {
            return "[\"" + triple.getSubject() + "\",\"" + triple.getPredicate() + "\",\"" + triple.getObject() + "\"]";
        }
    }

    // ========= Multi-Knowledge Base Retrieval Helpers =========

    /**
     * Perform retrieval on multiple graph knowledge bases, returns text list.
     * 
     * @param knowledgeBases knowledgeBases
     * @param query query
     * @param config config
     * @param topK topK
     * @return the result
     * @since 0.1.7
     */
    public static List<String> retrieveMultiGraphKb(List<? extends KnowledgeBase> knowledgeBases, String query,
            RetrievalConfig config, Integer topK) {
        return SimpleKnowledgeBase.retrieveMultiKb(knowledgeBases, query, config, topK);
    }

    /**
     * Perform retrieval on multiple graph knowledge bases, includes source information.
     * 
     * @param knowledgeBases knowledgeBases
     * @param query query
     * @param config config
     * @param topK topK
     * @return the result
     * @since 0.1.7
     */
    public static List<MultiKBRetrievalResult> retrieveMultiGraphKbWithSource(
            List<? extends KnowledgeBase> knowledgeBases, String query, RetrievalConfig config, Integer topK) {
        return SimpleKnowledgeBase.retrieveMultiKbWithSource(knowledgeBases, query, config, topK);
    }
}
