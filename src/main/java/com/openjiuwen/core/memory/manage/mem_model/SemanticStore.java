/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.common.MemoryBaseUtils;
import com.openjiuwen.core.memory.migration.MigrationPlan;
import com.openjiuwen.core.retrieval.embedding.Embedding;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors Python's {@code SemanticStore} in
 * {@code openjiuwen/core/memory/manage/mem_model/semantic_store.py}.
 */
public class SemanticStore {

    private final BaseVectorStore vectorStore;

    private volatile Embedding embeddingModel;

    private final Set<String> createdCollections = ConcurrentHashMap.newKeySet();

    public SemanticStore(BaseVectorStore vectorStore) {
        this(vectorStore, null);
    }

    public SemanticStore(BaseVectorStore vectorStore, Embedding embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    public void initializeEmbeddingModel(Embedding embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public CompletableFuture<Boolean> addDocs(List<Map.Entry<String, String>> docs, String tableName) {
        return addDocs(docs, tableName, null);
    }

    public CompletableFuture<Boolean> addDocs(
            List<Map.Entry<String, String>> docs,
            String tableName,
            String scopeId
    ) {
        if (embeddingModel == null) {
            logMissingEmbedding(tableName, scopeId, LogEventType.MEMORY_STORE);
            return CompletableFuture.completedFuture(false);
        }
        if (docs == null || docs.isEmpty()) {
            Loggers.MEMORY.error(
                    "Failed to add documents to semantic store. event_type={} scope_id={} collection_name={} reason=empty_docs",
                    LogEventType.MEMORY_STORE.getValue(),
                    scopeId,
                    tableName
            );
            return CompletableFuture.completedFuture(false);
        }

        List<String> memoryIds = new ArrayList<>(docs.size());
        List<String> texts = new ArrayList<>(docs.size());
        for (Map.Entry<String, String> doc : docs) {
            memoryIds.add(doc.getKey());
            texts.add(doc.getValue());
        }

        return embeddingModel.embedDocuments(texts).thenCompose(embeddings -> {
            if (memoryIds.size() != embeddings.size()) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "store_type",
                        "semantic store",
                        "error_msg",
                        "memory_ids and embeddings must have same length"
                );
            }

            CompletableFuture<Void> ensureCollection = embeddings.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : createCollectionIfNotExists(tableName, embeddings.getFirst().size());

            return ensureCollection.thenCompose(ignored -> {
                List<Map<String, Object>> data = new ArrayList<>(memoryIds.size());
                for (int index = 0; index < memoryIds.size(); index++) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", memoryIds.get(index));
                    row.put("embedding", embeddings.get(index));
                    data.add(row);
                }
                return vectorStore.addDocs(tableName, data, Map.of());
            }).thenApply(ignored -> true);
        }).exceptionally(exception -> {
            Loggers.MEMORY.error(
                    "Failed to add documents to semantic store. event_type={} scope_id={} collection_name={} exception={}",
                    LogEventType.MEMORY_STORE.getValue(),
                    scopeId,
                    tableName,
                    rootCauseMessage(exception)
            );
            return false;
        });
    }

    public CompletableFuture<Void> deleteDocs(List<String> ids, String tableName) {
        return vectorStore.collectionExists(tableName, Map.of()).thenCompose(exists -> {
            if (!exists) {
                Loggers.MEMORY.debug(
                        "Collection '{}' does not exist, nothing to delete. event_type={}",
                        tableName,
                        LogEventType.MEMORY_DELETE.getValue()
                );
                return CompletableFuture.completedFuture(null);
            }
            return vectorStore.deleteDocsByIds(tableName, ids, Map.of());
        }).exceptionally(exception -> {
            Loggers.MEMORY.error(
                    "Failed to delete documents from semantic store. event_type={} collection_name={} memory_ids={} exception={}",
                    LogEventType.MEMORY_DELETE.getValue(),
                    tableName,
                    ids,
                    rootCauseMessage(exception)
            );
            return null;
        });
    }

    public CompletableFuture<List<Map.Entry<String, Double>>> search(String query, String tableName) {
        return search(query, tableName, null, 5);
    }

    public CompletableFuture<List<Map.Entry<String, Double>>> search(
            String query,
            String tableName,
            String scopeId,
            int topK
    ) {
        if (embeddingModel == null) {
            logMissingEmbedding(tableName, scopeId, LogEventType.MEMORY_RETRIEVE);
            return CompletableFuture.completedFuture(List.<Map.Entry<String, Double>>of());
        }

        return embeddingModel.embedDocuments(List.of(query)).thenCompose(queryEmbeddings -> {
            if (queryEmbeddings.isEmpty()) {
                Loggers.MEMORY.error(
                        "Failed to embed query. event_type={} query={} collection_name={}",
                        LogEventType.MEMORY_RETRIEVE.getValue(),
                        query,
                        tableName
                );
                return CompletableFuture.completedFuture(List.<Map.Entry<String, Double>>of());
            }

            List<Double> queryEmbedding = queryEmbeddings.getFirst();
            return vectorStore.collectionExists(tableName, Map.of()).thenCompose(exists -> {
                if (!exists) {
                    return CompletableFuture.completedFuture(List.<Map.Entry<String, Double>>of());
                }
                return vectorStore.search(tableName, queryEmbedding, "embedding", topK, Map.of(), Map.of())
                        .thenApply(results -> toIdScorePairs(results));
            });
        }).exceptionally(exception -> {
            Loggers.MEMORY.error(
                    "Failed to embed query. event_type={} query={} scope_id={} collection_name={} exception={}",
                    LogEventType.MEMORY_RETRIEVE.getValue(),
                    query,
                    scopeId,
                    tableName,
                    rootCauseMessage(exception)
            );
            return List.<Map.Entry<String, Double>>of();
        });
    }

    public CompletableFuture<Void> deleteTable(String tableName) {
        return vectorStore.deleteCollection(tableName, Map.of())
                .thenRun(() -> createdCollections.remove(tableName))
                .exceptionally(exception -> {
                    Loggers.MEMORY.error(
                            "Failed to delete table from semantic store. event_type={} collection_name={} exception={}",
                            LogEventType.MEMORY_DELETE.getValue(),
                            tableName,
                            rootCauseMessage(exception)
                    );
                    return null;
                });
    }

    private CompletableFuture<Void> createCollectionIfNotExists(String collectionName, int embeddingDim) {
        if (createdCollections.contains(collectionName)) {
            return CompletableFuture.completedFuture(null);
        }

        return vectorStore.collectionExists(collectionName, Map.of()).thenCompose(exists -> {
            if (exists) {
                createdCollections.add(collectionName);
                return CompletableFuture.completedFuture(null);
            }

            CollectionSchema schema = new CollectionSchema(List.of(), "Semantic memory collection", false);
            schema.addField(new FieldSchema(
                    "id",
                    VectorDataType.VARCHAR,
                    true,
                    false,
                    256,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            schema.addField(new FieldSchema(
                    "embedding",
                    VectorDataType.FLOAT_VECTOR,
                    false,
                    false,
                    null,
                    embeddingDim,
                    null,
                    null,
                    null,
                    null
            ));

            return vectorStore.createCollection(collectionName, schema, Map.of())
                    .thenCompose(ignored -> {
                        String memType = MemoryBaseUtils.parseMemtypeFromIdxName(collectionName);
                        int latestSchemaVersion =
                                MigrationPlan.getVectorRegistry().getCurrentVersion("vector_" + memType);
                        return vectorStore.updateCollectionMetadata(
                                collectionName,
                                Map.of("schema_version", latestSchemaVersion)
                        );
                    })
                    .thenRun(() -> {
                        createdCollections.add(collectionName);
                        Loggers.MEMORY.debug(
                                "Created collection '{}' with embedding dimension {}. event_type={}",
                                collectionName,
                                embeddingDim,
                                LogEventType.MEMORY_STORE.getValue()
                        );
                    });
        });
    }

    private static List<Map.Entry<String, Double>> toIdScorePairs(List<VectorSearchResult> results) {
        List<Map.Entry<String, Double>> pairs = new ArrayList<>();
        for (VectorSearchResult result : results) {
            Object identifier = result.getFields().getOrDefault("id", "");
            pairs.add(new AbstractMap.SimpleEntry<>(String.valueOf(identifier), result.getScore()));
        }
        return pairs;
    }

    private static void logMissingEmbedding(String tableName, String scopeId, LogEventType eventType) {
        Loggers.MEMORY.error(
                "Embedding model not initialized, please call initializeEmbeddingModel first. event_type={} scope_id={} collection_name={}",
                eventType.getValue(),
                scopeId,
                tableName
        );
    }

    private static String rootCauseMessage(Throwable exception) {
        Throwable current = exception;
        while (current instanceof CompletionException completionException && completionException.getCause() != null) {
            current = completionException.getCause();
        }
        return current == null ? null : current.getMessage();
    }
}
