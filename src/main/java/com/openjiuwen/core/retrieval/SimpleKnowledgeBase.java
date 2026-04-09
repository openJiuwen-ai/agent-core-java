/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.AgenticRetriever;
import com.openjiuwen.core.retrieval.retriever.HybridRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.retriever.SparseRetriever;
import com.openjiuwen.core.retrieval.retriever.VectorRetriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Standard chunk-based knowledge base.
 */
public class SimpleKnowledgeBase extends KnowledgeBase {

    public SimpleKnowledgeBase(KnowledgeBaseConfig config) {
        super(config);
    }

    public SimpleKnowledgeBase(KnowledgeBaseConfig config,
                               VectorStore vectorStore,
                               Embedding embedModel,
                               Parser parser,
                               Chunker chunker,
                               Indexer indexManager,
                               BaseModelClient llmClient,
                               Retriever retriever) {
        super(config, vectorStore, embedModel, parser, chunker, null, indexManager, llmClient, retriever);
    }

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
            String docId = document.getId() == null || document.getId().isBlank() ? UUID.randomUUID().toString() : document.getId();
            normalized.add(new Document(docId, document.getText(), document.getMetadata()));
            docIds.add(docId);
        }
        boolean built = activeIndexManager.buildIndex(
                chunker.chunkDocuments(normalized),
                new IndexConfig(chunkIndexName(), config.getIndexType()),
                embedModel,
                Map.of());
        if (!built) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_INDEX_BUILD_EXECUTION_ERROR, "Failed to build index");
        }
        return docIds;
    }

    @Override
    public List<RetrievalResult> retrieve(String query, RetrievalConfig retrievalConfig) {
        RetrievalConfig config = retrievalConfig == null ? new RetrievalConfig() : retrievalConfig;
        Retriever activeRetriever = resolveRetriever(config);
        String mode = switch (this.config.getIndexType()) {
            case "vector" -> "vector";
            case "bm25" -> "sparse";
            default -> "hybrid";
        };
        return activeRetriever.retrieve(query, config.getTopK(), config.getScoreThreshold(), mode, optionsFrom(config));
    }

    @Override
    public boolean deleteDocuments(List<String> docIds) {
        Indexer activeIndexManager = requireIndexManager();
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
        boolean deleted = true;
        for (String docId : docIds) {
            deleted &= activeIndexManager.deleteIndex(docId, chunkIndexName(), Map.of());
        }
        return deleted;
    }

    @Override
    public List<String> updateDocuments(List<Document> documents) {
        if (chunker == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_CHUNKER_NOT_FOUND, "chunker is required");
        }
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
        Indexer activeIndexManager = requireIndexManager();
        List<String> ids = new ArrayList<>();
        for (Document document : documents) {
            String docId = document.getId() == null || document.getId().isBlank() ? UUID.randomUUID().toString() : document.getId();
            ids.add(docId);
            activeIndexManager.updateIndex(
                    chunker.chunkDocuments(List.of(new Document(docId, document.getText(), document.getMetadata()))),
                    docId,
                    new IndexConfig(chunkIndexName(), config.getIndexType()),
                    embedModel,
                    Map.of());
        }
        return ids;
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("kb_id", config.getKbId());
        stats.put("index_type", config.getIndexType());
        Indexer activeIndexManager = resolveIndexManager();
        if (activeIndexManager == null) {
            stats.put("index_exists", false);
            return stats;
        }
        stats.put("index_exists", activeIndexManager.indexExists(chunkIndexName()));
        stats.put("index_info", activeIndexManager.getIndexInfo(chunkIndexName()));
        return stats;
    }

    protected String chunkIndexName() {
        return "kb_" + config.getKbId() + "_chunks";
    }

    protected Map<String, Object> optionsFrom(RetrievalConfig config) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (config.getFilters() != null) {
            options.put("filters", config.getFilters());
        }
        if (config.isGraphExpansion()) {
            options.put("graph_expansion", true);
        }
        return options;
    }

    protected Retriever resolveRetriever(RetrievalConfig retrievalConfig) {
        Retriever baseRetriever = retriever;
        if (baseRetriever == null) {
            if (vectorStore == null) {
                throw RetrievalExceptions.error(
                        StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                        "vector_store or retriever is required");
            }
            baseRetriever = switch (config.getIndexType()) {
                case "vector" -> new VectorRetriever(vectorStore.withCollection(chunkIndexName()), embedModel);
                case "bm25" -> new SparseRetriever(vectorStore.withCollection(chunkIndexName()));
                default -> new HybridRetriever(vectorStore.withCollection(chunkIndexName()), embedModel);
            };
        }
        if (retrievalConfig.isAgentic()) {
            if (llmClient == null) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_RETRIEVER_LLM_CLIENT_NOT_FOUND, "llm_client is required");
            }
            return new AgenticRetriever(baseRetriever, llmClient);
        }
        return baseRetriever;
    }

    public static List<String> retrieveMultiKb(List<? extends KnowledgeBase> knowledgeBases,
                                               String query,
                                               RetrievalConfig config,
                                               Integer topK) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return List.of();
        }
        RetrievalConfig retrievalConfig = config != null ? config : new RetrievalConfig();
        Map<String, Double> byText = new LinkedHashMap<>();
        for (KnowledgeBase kb : knowledgeBases) {
            try {
                for (RetrievalResult result : kb.retrieve(query, retrievalConfig)) {
                    String text = result.getText();
                    double score = result.getScore();
                    Double existing = byText.get(text);
                    if (existing == null || score > existing) {
                        byText.put(text, score);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        int limit = topK != null ? topK : (config != null ? config.getTopK() : 5);
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(byText.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> results = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, ranked.size()); i++) {
            results.add(ranked.get(i).getKey());
        }
        return results;
    }

    /**
     * Convenience overload without config/topK.
     */
    public static List<String> retrieveMultiKb(List<? extends KnowledgeBase> knowledgeBases, String query, int topK) {
        return retrieveMultiKb(knowledgeBases, query, null, topK);
    }

    public static List<MultiKBRetrievalResult> retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases,
                                                                         String query,
                                                                         RetrievalConfig config,
                                                                         Integer topK) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return List.of();
        }
        RetrievalConfig retrievalConfig = config != null ? config : new RetrievalConfig();
        Map<String, MultiKBRetrievalResult> byText = new LinkedHashMap<>();
        for (KnowledgeBase kb : knowledgeBases) {
            String kbId = kb.getConfig().getKbId();
            try {
                for (RetrievalResult result : kb.retrieve(query, retrievalConfig)) {
                    MultiKBRetrievalResult aggregate = byText.get(result.getText());
                    double rawScore = result.getMetadata().get("raw_score") instanceof Number n ? n.doubleValue() : result.getScore();
                    double scaled = result.getMetadata().get("raw_score_scaled") instanceof Number n ? n.doubleValue() : result.getScore();
                    if (aggregate == null) {
                        aggregate = new MultiKBRetrievalResult(
                                result.getText(),
                                result.getScore(),
                                rawScore,
                                scaled,
                                List.of(kbId),
                                result.getMetadata());
                        byText.put(result.getText(), aggregate);
                    } else {
                        if (result.getScore() > aggregate.getScore()) {
                            aggregate.setScore(result.getScore());
                            aggregate.setRawScore(rawScore);
                            aggregate.setRawScoreScaled(scaled);
                            aggregate.setMetadata(result.getMetadata());
                        }
                        LinkedHashSet<String> kbIds = new LinkedHashSet<>(aggregate.getKbIds());
                        kbIds.add(kbId);
                        aggregate.setKbIds(new ArrayList<>(kbIds));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        int limit = topK != null ? topK : (config != null ? config.getTopK() : 5);
        List<MultiKBRetrievalResult> results = new ArrayList<>(byText.values());
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results.size() <= limit ? results : new ArrayList<>(results.subList(0, limit));
    }

    /**
     * Convenience overload without config/topK.
     */
    public static List<MultiKBRetrievalResult> retrieveMultiKbWithSource(List<? extends KnowledgeBase> knowledgeBases,
                                                                         String query,
                                                                         int topK) {
        return retrieveMultiKbWithSource(knowledgeBases, query, null, topK);
    }
}
