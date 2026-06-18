/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

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
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.AgenticRetriever;
import com.openjiuwen.core.retrieval.retriever.HybridRetriever;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.retriever.SparseRetriever;
import com.openjiuwen.core.retrieval.retriever.VectorRetriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code SimpleKnowledgeBase} in
 * {@code openjiuwen/core/retrieval/simple_knowledge_base.py}.
 */
public class SimpleKnowledgeBase extends KnowledgeBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleKnowledgeBase.class);

    private Retriever retriever;

    public SimpleKnowledgeBase(KnowledgeBaseConfig config) {
        this(config, null, null, null, null, null, null, null, null);
    }

    public SimpleKnowledgeBase(
            KnowledgeBaseConfig config,
            VectorStore vectorStore,
            Embedding embedModel,
            Parser parser,
            Chunker chunker,
            Extractor extractor,
            Indexer indexManager,
            BaseModelClient llmClient,
            Retriever retriever
    ) {
        super(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient);
        this.retriever = retriever;
    }

    public Retriever getRetriever() {
        return retriever;
    }

    public void setRetriever(Retriever retriever) {
        this.retriever = retriever;
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
        Map<String, Object> buildOptions = new LinkedHashMap<>();
        buildOptions.put("database_name", databaseNameFromVectorStore());

        IndexConfig indexConfig = IndexConfig.builder()
                .indexName(chunkIndexName())
                .indexType(config.getIndexType())
                .useCaptionForImages(config.isUseCaptionForImages())
                .build();
        return indexManager.buildIndex(chunks, indexConfig, embedModel, buildOptions)
                .thenApply(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        throw ErrorHelper.buildError(
                                StatusCode.RETRIEVAL_KB_INDEX_BUILD_EXECUTION_ERROR,
                                "error_msg",
                                "Failed to build index"
                        );
                    }
                    return List.copyOf(docIds);
                });
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> retrieve(
            String query,
            RetrievalConfig retrievalConfig,
            Map<String, Object> kwargs
    ) {
        RetrievalConfig activeConfig = retrievalConfig == null ? new RetrievalConfig() : retrievalConfig;
        Retriever activeRetriever = resolveRetriever(activeConfig, kwargs == null ? Map.of() : kwargs);
        String mode = retrievalMode();
        Map<String, Object> options = optionsFrom(activeConfig);
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
        if (docIds == null || docIds.isEmpty()) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(Boolean.TRUE);
        for (String docId : docIds) {
            chain = chain.thenCompose(previous -> indexManager.deleteIndex(
                            docId,
                            chunkIndexName(),
                            kwargs == null ? Map.of() : kwargs
                    )
                    .thenApply(result -> previous && Boolean.TRUE.equals(result)));
        }
        return chain;
    }

    @Override
    public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
        if (chunker == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_CHUNKER_NOT_FOUND,
                    "error_msg",
                    "chunker is required for update_documents"
            );
        }
        if (indexManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND,
                    "error_msg",
                    "index_manager is required for update_documents"
            );
        }
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }

        List<TextChunk> chunks = chunker.chunkDocuments(documents);
        IndexConfig indexConfig = IndexConfig.builder()
                .indexName(chunkIndexName())
                .indexType(config.getIndexType())
                .build();
        List<String> docIds = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Document doc : documents) {
            List<TextChunk> docChunks = chunks.stream()
                    .filter(chunk -> doc.getId_() != null && doc.getId_().equals(chunk.getDocId()))
                    .toList();
            if (docChunks.isEmpty()) {
                continue;
            }
            chain = chain.thenCompose(ignored -> indexManager.updateIndex(
                            docChunks,
                            doc.getId_(),
                            indexConfig,
                            embedModel,
                            kwargs == null ? Map.of() : kwargs
                    )
                    .thenAccept(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            docIds.add(doc.getId_());
                        }
                    }));
        }
        return chain.thenApply(ignored -> List.copyOf(docIds));
    }

    @Override
    public CompletableFuture<Map<String, Object>> getStatistics() {
        if (indexManager == null) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("kb_id", config.getKbId());
            stats.put("index_exists", false);
            return CompletableFuture.completedFuture(stats);
        }
        return indexManager.getIndexInfo(chunkIndexName()).thenApply(indexInfo -> {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("kb_id", config.getKbId());
            stats.put("index_type", config.getIndexType());
            stats.put("index_info", indexInfo);
            stats.put("has_parser", parser != null);
            stats.put("has_chunker", chunker != null);
            stats.put("has_extractor", extractor != null);
            stats.put("has_embed_model", embedModel != null);
            stats.put("has_vector_store", vectorStore != null);
            return stats;
        });
    }

    public static CompletableFuture<List<String>> retrieveMultiKb(
            List<? extends KnowledgeBase> knowledgeBases,
            String query,
            RetrievalConfig config,
            Integer topK
    ) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        RetrievalConfig activeConfig = config == null ? new RetrievalConfig() : config;
        List<CompletableFuture<List<RetrievalResult>>> tasks = knowledgeBases.stream()
                .map(kb -> kb.retrieve(query, activeConfig)
                        .exceptionally(exception -> {
                            LOGGER.warn("retrieve_multi_kb: kb_id={} failed: {}",
                                    kb.getConfig().getKbId(),
                                    exception.getMessage());
                            return List.of();
                        }))
                .toList();
        return allResults(tasks).thenApply(results -> {
            Map<String, Double> merged = new LinkedHashMap<>();
            for (List<RetrievalResult> oneKbResults : results) {
                for (RetrievalResult result : oneKbResults) {
                    double score = result.getScore();
                    merged.merge(result.getText(), score, Math::max);
                }
            }
            int limit = pythonLimit(topK, config);
            List<String> ranked = merged.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .map(Map.Entry::getKey)
                    .toList();
            return pythonSlice(ranked, limit);
        });
    }

    public static CompletableFuture<List<MultiKBRetrievalResult>> retrieveMultiKbWithSource(
            List<? extends KnowledgeBase> knowledgeBases,
            String query,
            RetrievalConfig config,
            Integer topK
    ) {
        if (knowledgeBases == null || knowledgeBases.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        RetrievalConfig activeConfig = config == null ? new RetrievalConfig() : config;
        List<CompletableFuture<List<RetrievalResult>>> tasks = knowledgeBases.stream()
                .map(kb -> kb.retrieve(query, activeConfig)
                        .exceptionally(exception -> {
                            LOGGER.warn("retrieve_multi_kb_with_source: kb_id={} failed: {}",
                                    kb.getConfig().getKbId(),
                                    exception.getMessage());
                            return List.of();
                        }))
                .toList();
        return allResults(tasks).thenApply(results -> {
            Map<String, MultiKBRetrievalResult> merged = new LinkedHashMap<>();
            for (int index = 0; index < knowledgeBases.size(); index++) {
                Object kbId = knowledgeBases.get(index).getConfig().getKbId();
                List<RetrievalResult> oneKbResults = results.get(index);
                if (oneKbResults == null) {
                    continue;
                }
                for (RetrievalResult result : oneKbResults) {
                    mergeWithSource(merged, result, kbId);
                }
            }
            int limit = pythonLimit(topK, config);
            List<MultiKBRetrievalResult> ranked = merged.values().stream()
                    .sorted((left, right) -> Double.compare(right.getScore(), left.getScore()))
                    .toList();
            return pythonSlice(ranked, limit);
        });
    }

    private Retriever resolveRetriever(RetrievalConfig retrievalConfig, Map<String, Object> kwargs) {
        Retriever activeRetriever = retriever;
        if (activeRetriever == null) {
            if (vectorStore == null) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                        "error_msg",
                        "vector_store or retriever is required for retrieve"
                );
            }
            activeRetriever = switch (config.getIndexType()) {
                case "vector" -> new VectorRetriever(vectorStore, embedModel);
                case "bm25" -> new SparseRetriever(vectorStore);
                default -> new HybridRetriever(vectorStore, embedModel);
            };
            retriever = activeRetriever;
        }
        if (retrievalConfig.isAgentic()) {
            activeRetriever = new AgenticRetriever(activeRetriever, llmClient, agenticMaxIter(kwargs));
        }
        return activeRetriever;
    }

    private String retrievalMode() {
        return switch (config.getIndexType()) {
            case "vector" -> "vector";
            case "bm25" -> "sparse";
            default -> "hybrid";
        };
    }

    private Map<String, Object> optionsFrom(RetrievalConfig config) {
        Map<String, Object> options = new LinkedHashMap<>();
        if (config.getFilters() != null) {
            options.put("filters", config.getFilters());
        }
        return options;
    }

    private String chunkIndexName() {
        return "kb_" + config.getKbId() + "_chunks";
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

    private static CompletableFuture<List<List<RetrievalResult>>> allResults(
            List<CompletableFuture<List<RetrievalResult>>> tasks
    ) {
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> tasks.stream().map(CompletableFuture::join).toList());
    }

    private static void mergeWithSource(
            Map<String, MultiKBRetrievalResult> merged,
            RetrievalResult result,
            Object kbId
    ) {
        String text = result.getText();
        Map<String, Object> metadata = result.getMetadata() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(result.getMetadata());
        double score = result.getScore();
        double rawScore = metadata.get("raw_score") instanceof Number number ? number.doubleValue() : score;
        double scaled = metadata.get("raw_score_scaled") instanceof Number number ? number.doubleValue() : score;
        MultiKBRetrievalResult existing = merged.get(text);
        if (existing == null) {
            merged.put(text, new MultiKBRetrievalResult(
                    text,
                    score,
                    rawScore,
                    scaled,
                    mutableSingleton(kbId),
                    metadata
            ));
            return;
        }
        if (score > existing.getScore()) {
            existing.setScore(score);
        }
        existing.setRawScore(Math.max(existing.getRawScore(), rawScore));
        existing.setRawScoreScaled(Math.max(existing.getRawScoreScaled(), scaled));
        LinkedHashSet<Object> kbIds = new LinkedHashSet<>(existing.getKbIds());
        kbIds.add(kbId);
        existing.setKbIds(sortedKbIds(kbIds));
    }

    private static int agenticMaxIter(Map<String, Object> kwargs) {
        if (kwargs == null || kwargs.isEmpty()) {
            return 2;
        }
        Object value = kwargs.containsKey("max_iter") ? kwargs.get("max_iter") : kwargs.get("maxIter");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 2;
            }
        }
        return 2;
    }

    private static int pythonLimit(Integer topK, RetrievalConfig config) {
        if (topK != null && topK != 0) {
            return topK;
        }
        if (config != null && config.getTopK() != 0) {
            return config.getTopK();
        }
        return 5;
    }

    private static <T> List<T> pythonSlice(List<T> ranked, int limit) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        int end = limit >= 0 ? Math.min(limit, ranked.size()) : Math.max(ranked.size() + limit, 0);
        return List.copyOf(ranked.subList(0, end));
    }

    private static List<Object> sortedKbIds(LinkedHashSet<Object> kbIds) {
        return kbIds.stream()
                .sorted(Comparator.comparing(value -> value == null ? "" : String.valueOf(value)))
                .toList();
    }

    private static List<Object> mutableSingleton(Object value) {
        List<Object> values = new ArrayList<>(1);
        values.add(value);
        return values;
    }
}
