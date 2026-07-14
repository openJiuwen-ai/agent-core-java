/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.StorageCodec;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * Mirrors Python's {@code SimpleMemoryIndex} in
 * {@code openjiuwen/core/foundation/store/index/simple_memory_index.py}.
 */
public class SimpleMemoryIndex extends BaseMemoryIndex {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String KV_PREFIX = "UMD";

    private static final String KV_SEP = "/";

    private static final String IDS_SUFFIX = "ids";

    private static final String TEXT_FIELD = "text";

    private static final int BYTE_NUM_PER_ID = 24;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss");

    private static final DateTimeFormatter LEGACY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern KV_SEP_PATTERN = Pattern.compile(Pattern.quote(KV_SEP));

    private final BaseKVStore kvStore;

    private final BaseVectorStore vectorStore;

    private final Set<String> createdCollections = ConcurrentHashMap.newKeySet();

    private final Map<String, Integer> backups = new ConcurrentHashMap<>();

    private volatile Embedding embeddingModel;

    private volatile int schemaVersion;

    private volatile StorageCodec codec;

    public SimpleMemoryIndex(BaseKVStore kvStore, BaseVectorStore vectorStore) {
        this(kvStore, vectorStore, null);
    }

    public SimpleMemoryIndex(BaseKVStore kvStore, BaseVectorStore vectorStore, Embedding embeddingModel) {
        this.kvStore = Objects.requireNonNull(kvStore, "kvStore");
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.embeddingModel = embeddingModel;
    }

    public void setEmbeddingModel(Embedding embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void setStorageCodec(StorageCodec codec) {
        this.codec = codec;
    }

    @Override
    public CompletableFuture<Void> addMemories(String userId, String scopeId, List<MemoryDoc> memories) {
        if (memories == null || memories.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        Embedding model = embeddingModel;
        if (model == null) {
            Loggers.MEMORY.error(
                    "Embedding model not initialized. event_type={}",
                    LogEventType.MEMORY_STORE.getValue()
            );
            return CompletableFuture.failedFuture(
                    buildError(
                            StatusCode.MEMORY_ADD_MEMORY_EXECUTION_ERROR,
                            "memory_type",
                            "vector store",
                            "error_msg",
                            "vector store failed: embedding model not initialized"
                    )
            );
        }

        Map<String, List<MemoryDoc>> docsByType = new LinkedHashMap<>();
        for (MemoryDoc memory : memories) {
            docsByType.computeIfAbsent(memory.getType(), ignored -> new ArrayList<>()).add(memory);
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<String, List<MemoryDoc>> entry : docsByType.entrySet()) {
            String memType = entry.getKey();
            List<MemoryDoc> docs = entry.getValue();
            chain = chain.thenCompose(ignored -> addDocsForType(userId, scopeId, memType, docs, model));
        }
        return chain;
    }

    @Override
    public CompletableFuture<List<MemorySearchResult>> search(
            String userId,
            String scopeId,
            String query,
            List<String> memTypes,
            int topK
    ) {
        Embedding model = embeddingModel;
        if (model == null) {
            Loggers.MEMORY.error(
                    "Embedding model not initialized. event_type={}",
                    LogEventType.MEMORY_RETRIEVE.getValue()
            );
            return CompletableFuture.completedFuture(List.of());
        }

        return model.embedQuery(query).thenCompose(queryVector -> {
            CompletableFuture<List<String>> typesFuture = memTypes != null
                    ? CompletableFuture.completedFuture(memTypes)
                    : collectionsFor(userId, scopeId).thenApply(collections -> {
                        List<String> discoveredTypes = new ArrayList<>();
                        for (String collection : collections) {
                            String memType = parseMemTypeFromCollection(collection);
                            if (memType != null) {
                                discoveredTypes.add(memType);
                            }
                        }
                        return discoveredTypes;
                    });
            return typesFuture.thenCompose(types -> searchAcrossTypes(userId, scopeId, queryVector, types, topK));
        });
    }

    @Override
    public CompletableFuture<Void> updateMemories(String userId, String scopeId, List<MemoryDoc> memories) {
        if (memories == null || memories.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<String> ids = memories.stream().map(MemoryDoc::getId).toList();
        return deleteMemories(userId, scopeId, ids).thenCompose(ignored -> addMemories(userId, scopeId, memories));
    }

    @Override
    public CompletableFuture<Void> deleteMemories(String userId, String scopeId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String memoryId : ids) {
            chain = chain.thenCompose(ignored -> deleteSingleMemory(userId, scopeId, memoryId));
        }
        return chain.thenCompose(ignored -> collectionsFor(userId, scopeId))
                .thenCompose(collections -> {
                    CompletableFuture<Void> deleteChain = CompletableFuture.completedFuture(null);
                    for (String collection : collections) {
                        deleteChain = deleteChain.thenCompose(
                                ignored -> vectorStore.deleteDocsByIds(collection, ids, Map.of())
                        );
                    }
                    return deleteChain;
                });
    }

    @Override
    public CompletableFuture<Void> deleteByUser(String userId) {
        String kvPrefix = KV_PREFIX + KV_SEP + userId + KV_SEP;
        return kvStore.deleteByPrefix(kvPrefix, null)
                .thenCompose(ignored -> vectorStore.listCollectionNames())
                .thenCompose(collections -> {
                    CompletableFuture<Void> deleteChain = CompletableFuture.completedFuture(null);
                    String marker = "uid_" + userId + "_gid_";
                    for (String collection : collections) {
                        if (!collection.startsWith(marker)) {
                            continue;
                        }
                        deleteChain = deleteChain.thenCompose(ignored ->
                                vectorStore.deleteCollection(collection, Map.of())
                                        .thenRun(() -> createdCollections.remove(collection))
                        );
                    }
                    return deleteChain;
                });
    }

    @Override
    public CompletableFuture<Void> deleteByScope(String scopeId) {
        String kvPrefix = KV_PREFIX + KV_SEP;
        return kvStore.getByPrefix(kvPrefix).thenCompose(allKv -> {
            List<String> keysToDelete = new ArrayList<>();
            for (String key : allKv.keySet()) {
                String[] parts = KV_SEP_PATTERN.split(key);
                if (parts.length >= 3 && Objects.equals(parts[2], scopeId)) {
                    keysToDelete.add(key);
                }
            }
            CompletableFuture<?> deleteKvFuture = keysToDelete.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : kvStore.batchDelete(keysToDelete, null);
            return deleteKvFuture.thenCompose(ignored -> vectorStore.listCollectionNames());
        }).thenCompose(collections -> {
            CompletableFuture<Void> deleteChain = CompletableFuture.completedFuture(null);
            String scopeMarker = "_gid_" + scopeId + "_mtype_";
            for (String collection : collections) {
                if (!collection.startsWith("uid_") || !collection.contains(scopeMarker)) {
                    continue;
                }
                deleteChain = deleteChain.thenCompose(ignored ->
                        vectorStore.deleteCollection(collection, Map.of())
                                .thenRun(() -> createdCollections.remove(collection))
                );
            }
            return deleteChain;
        });
    }

    @Override
    public CompletableFuture<Void> deleteByUserAndScope(String userId, String scopeId) {
        String kvPrefix = KV_PREFIX + KV_SEP + userId + KV_SEP + scopeId + KV_SEP;
        return kvStore.deleteByPrefix(kvPrefix, null)
                .thenCompose(ignored -> collectionsFor(userId, scopeId))
                .thenCompose(collections -> {
                    CompletableFuture<Void> deleteChain = CompletableFuture.completedFuture(null);
                    for (String collection : collections) {
                        deleteChain = deleteChain.thenCompose(ignored ->
                                vectorStore.deleteCollection(collection, Map.of())
                                        .thenRun(() -> createdCollections.remove(collection))
                        );
                    }
                    return deleteChain;
                });
    }

    @Override
    public CompletableFuture<MemoryDoc> getById(String userId, String scopeId, String memId) {
        return kvStore.get(kvMemKey(userId, scopeId, memId)).thenApply(rawValue -> {
            String rawText = readKvValue(rawValue);
            if (rawText == null) {
                return null;
            }
            Map<String, Object> data = readJsonMap(rawText);
            decodeMemoryField(data);
            return kvDataToMemoryDoc(data, memId);
        });
    }

    @Override
    public CompletableFuture<List<MemoryDoc>> listMemories(
            String userId,
            String scopeId,
            int offset,
            int limit,
            List<String> memTypes
    ) {
        return kvStore.get(kvIdsKey(userId, scopeId)).thenCompose(rawIds -> {
            String idsValue = readKvValue(rawIds);
            if (idsValue == null || idsValue.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }

            List<String> allIds = parseAllIds(idsValue);
            if (allIds.isEmpty()) {
                return CompletableFuture.completedFuture(List.of());
            }

            List<String> keys = allIds.stream().map(memoryId -> kvMemKey(userId, scopeId, memoryId)).toList();
            return kvStore.mget(keys).thenApply(values -> {
                List<MemoryDoc> docs = new ArrayList<>();
                for (int index = 0; index < allIds.size(); index++) {
                    String rawValue = readKvValue(values.get(index));
                    if (rawValue == null) {
                        continue;
                    }
                    Map<String, Object> data = readJsonMap(rawValue);
                    decodeMemoryField(data);
                    MemoryDoc doc = kvDataToMemoryDoc(data, allIds.get(index));
                    if (memTypes == null || memTypes.contains(doc.getType())) {
                        docs.add(doc);
                    }
                }
                if (memTypes != null) {
                    Map<String, Integer> typeOrder = new LinkedHashMap<>();
                    for (int index = 0; index < memTypes.size(); index++) {
                        typeOrder.put(memTypes.get(index), index);
                    }
                    docs.sort(
                            Comparator.comparingInt((MemoryDoc doc) -> typeOrder.getOrDefault(doc.getType(), typeOrder.size()))
                                    .thenComparing(
                                            MemoryDoc::getTimestamp,
                                            Comparator.nullsLast(Comparator.reverseOrder())
                                    )
                    );
                }
                int start = Math.max(0, Math.min(offset, docs.size()));
                int end = limit <= 0 ? docs.size() : Math.min(docs.size(), start + limit);
                return new ArrayList<>(docs.subList(start, end));
            });
        });
    }

    @Override
    public int getSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public void updateSchemaVersion(int version) {
        this.schemaVersion = version;
    }

    @Override
    public CompletableFuture<String> createBackup() {
        String backupId = UUID.randomUUID().toString();
        backups.put(backupId, schemaVersion);
        return CompletableFuture.completedFuture(backupId);
    }

    @Override
    public CompletableFuture<Void> restoreBackup(String backupId) {
        Integer version = backups.get(backupId);
        if (version == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Backup " + backupId + " not found"));
        }
        schemaVersion = version;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> cleanupBackup(String backupId) {
        backups.remove(backupId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<UserScopeKey>> listUserScopes() {
        String kvPrefix = KV_PREFIX + KV_SEP;
        return kvStore.getByPrefix(kvPrefix).thenApply(allKv -> {
            Set<UserScopeKey> scopes = new LinkedHashSet<>();
            for (String key : allKv.keySet()) {
                String[] parts = KV_SEP_PATTERN.split(key);
                if (parts.length >= 3) {
                    scopes.add(new UserScopeKey(parts[1], parts[2]));
                }
            }
            return new ArrayList<>(scopes);
        });
    }

    private CompletableFuture<Void> addDocsForType(
            String userId,
            String scopeId,
            String memType,
            List<MemoryDoc> docs,
            Embedding model
    ) {
        String collectionName = getCollectionName(userId, scopeId, memType);
        List<String> texts = docs.stream().map(MemoryDoc::getText).toList();
        return model.embedDocuments(texts).thenCompose(embeddings -> {
            CompletableFuture<Void> ensureCollectionFuture = embeddings.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : ensureCollection(collectionName, embeddings.get(0).size());
            return ensureCollectionFuture.thenCompose(ignored -> {
                List<Map<String, Object>> vectorDocs = new ArrayList<>();
                for (int index = 0; index < docs.size(); index++) {
                    MemoryDoc doc = docs.get(index);
                    Map<String, Object> vectorDoc = new LinkedHashMap<>();
                    vectorDoc.put("id", doc.getId());
                    vectorDoc.put(TEXT_FIELD, doc.getText() == null ? "" : doc.getText());
                    vectorDoc.put("embedding", embeddings.get(index));
                    vectorDocs.add(vectorDoc);
                }
                return vectorStore.addDocs(collectionName, vectorDocs, Map.of());
            }).thenCompose(ignored -> {
                CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);
                for (MemoryDoc doc : docs) {
                    writeChain = writeChain.thenCompose(innerIgnored -> storeSingleMemory(userId, scopeId, memType, doc));
                }
                return writeChain;
            });
        });
    }

    private CompletableFuture<Void> storeSingleMemory(String userId, String scopeId, String memType, MemoryDoc doc) {
        String key = kvMemKey(userId, scopeId, doc.getId());
        Map<String, Object> kvData = memoryDocToKvData(doc, userId, scopeId);
        if (codec != null) {
            kvData.put("mem", codec.encode((String) kvData.getOrDefault("mem", "")));
        }
        return kvStore.set(key, writeKvValue(writeJsonMap(kvData)))
                .thenCompose(ignored -> addIdToTracking(userId, scopeId, doc.getId(), memType));
    }

    private CompletableFuture<Void> deleteSingleMemory(String userId, String scopeId, String memoryId) {
        String kvKey = kvMemKey(userId, scopeId, memoryId);
        return kvStore.get(kvKey).thenCompose(rawValue -> {
            String rawText = readKvValue(rawValue);
            String memType = rawText == null ? null : Objects.toString(readJsonMap(rawText).get("mem_type"), null);
            return kvStore.delete(kvKey)
                    .thenCompose(ignored -> removeIdFromTracking(userId, scopeId, memoryId, memType));
        });
    }

    private CompletableFuture<Void> addIdToTracking(String userId, String scopeId, String memId, String memType) {
        String globalKey = kvIdsKey(userId, scopeId);
        return kvStore.get(globalKey).thenCompose(rawGlobal -> {
            String globalValue = readKvValue(rawGlobal);
            String updatedGlobal = globalValue == null ? "" : globalValue;
            CompletableFuture<Void> globalFuture = parseAllIds(updatedGlobal).contains(memId)
                    ? CompletableFuture.completedFuture(null)
                    : kvStore.set(globalKey, writeKvValue(appendId(updatedGlobal, memId)));

            String typeKey = kvIdsKey(userId, scopeId, memType);
            return globalFuture.thenCompose(ignored -> kvStore.get(typeKey)).thenCompose(rawType -> {
                String typeValue = readKvValue(rawType);
                String updatedType = typeValue == null ? "" : typeValue;
                if (parseAllIds(updatedType).contains(memId)) {
                    return CompletableFuture.completedFuture(null);
                }
                return kvStore.set(typeKey, writeKvValue(appendId(updatedType, memId)));
            });
        });
    }

    private CompletableFuture<Void> removeIdFromTracking(String userId, String scopeId, String memId, String memType) {
        String globalKey = kvIdsKey(userId, scopeId);
        return kvStore.get(globalKey).thenCompose(rawGlobal -> {
            String globalValue = readKvValue(rawGlobal);
            String newGlobal = removeId(globalValue == null ? "" : globalValue, memId);
            CompletableFuture<Void> globalFuture = newGlobal.isEmpty()
                    ? kvStore.delete(globalKey)
                    : kvStore.set(globalKey, writeKvValue(newGlobal));

            if (memType == null || memType.isEmpty()) {
                return globalFuture;
            }

            String typeKey = kvIdsKey(userId, scopeId, memType);
            return globalFuture.thenCompose(ignored -> kvStore.get(typeKey)).thenCompose(rawType -> {
                String typeValue = readKvValue(rawType);
                String newType = removeId(typeValue == null ? "" : typeValue, memId);
                if (newType.isEmpty()) {
                    return kvStore.delete(typeKey);
                }
                return kvStore.set(typeKey, writeKvValue(newType));
            });
        });
    }

    private CompletableFuture<Void> ensureCollection(String collectionName, int dim) {
        if (createdCollections.contains(collectionName)) {
            return CompletableFuture.completedFuture(null);
        }
        return vectorStore.collectionExists(collectionName, Map.of()).thenCompose(exists -> {
            if (exists) {
                createdCollections.add(collectionName);
                return CompletableFuture.completedFuture(null);
            }

            CollectionSchema schema = new CollectionSchema(List.of(), "Semantic memory collection", false);
            schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null, null, null, null, null));
            schema.addField(new FieldSchema(TEXT_FIELD, VectorDataType.VARCHAR, false, false, 65535, null, null, null, null, null));
            schema.addField(new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, dim, null, null, null, null));
            return vectorStore.createCollection(collectionName, schema, Map.of())
                    .thenRun(() -> createdCollections.add(collectionName));
        });
    }

    private CompletableFuture<List<String>> collectionsFor(String userId, String scopeId) {
        String prefix = "uid_" + userId + "_gid_" + scopeId + "_mtype_";
        return vectorStore.listCollectionNames().thenApply(names -> {
            List<String> collections = new ArrayList<>();
            for (String name : names) {
                if (name.startsWith(prefix)) {
                    collections.add(name);
                }
            }
            return collections;
        });
    }

    private CompletableFuture<List<MemorySearchResult>> searchAcrossTypes(
            String userId,
            String scopeId,
            List<Double> queryVector,
            List<String> memTypes,
            int topK
    ) {
        if (topK <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<MemorySearchResult> results = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String memType : memTypes) {
            chain = chain.thenCompose(ignored ->
                    searchSingleType(userId, scopeId, queryVector, memType, topK).thenAccept(results::addAll)
            );
        }
        return chain.thenApply(ignored -> {
            results.sort(Comparator.comparingDouble(MemorySearchResult::score).reversed());
            if (results.size() <= topK) {
                return results;
            }
            return new ArrayList<>(results.subList(0, topK));
        });
    }

    private CompletableFuture<List<MemorySearchResult>> searchSingleType(
            String userId,
            String scopeId,
            List<Double> queryVector,
            String memType,
            int topK
    ) {
        if (topK <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        String collectionName = getCollectionName(userId, scopeId, memType);
        return vectorStore.collectionExists(collectionName, Map.of()).thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(List.of());
            }

            return vectorStore.search(collectionName, queryVector, "embedding", topK, Map.of(), Map.of())
                    .thenCompose(hits -> {
                        List<String> hitIds = new ArrayList<>();
                        Map<String, Double> scores = new LinkedHashMap<>();
                        for (VectorSearchResult hit : hits) {
                            Object id = hit.getFields().get("id");
                            if (id == null) {
                                continue;
                            }
                            String memoryId = String.valueOf(id);
                            hitIds.add(memoryId);
                            scores.put(memoryId, hit.getScore());
                        }
                        if (hitIds.isEmpty()) {
                            return CompletableFuture.completedFuture(List.of());
                        }

                        List<String> keys = hitIds.stream().map(memoryId -> kvMemKey(userId, scopeId, memoryId)).toList();
                        return kvStore.mget(keys).thenApply(values -> {
                            List<MemorySearchResult> partial = new ArrayList<>();
                            for (int index = 0; index < hitIds.size(); index++) {
                                String rawValue = readKvValue(values.get(index));
                                if (rawValue == null) {
                                    continue;
                                }
                                Map<String, Object> data = readJsonMap(rawValue);
                                decodeMemoryField(data);
                                partial.add(new MemorySearchResult(
                                        kvDataToMemoryDoc(data, hitIds.get(index)),
                                        scores.getOrDefault(hitIds.get(index), 0.0d)
                                ));
                            }
                            return partial;
                        });
                    });
        });
    }

    private String kvMemKey(String userId, String scopeId, String memId) {
        return KV_PREFIX + KV_SEP + userId + KV_SEP + scopeId + KV_SEP + memId;
    }

    private String kvIdsKey(String userId, String scopeId) {
        return KV_PREFIX + KV_SEP + userId + KV_SEP + scopeId + KV_SEP + IDS_SUFFIX;
    }

    private String kvIdsKey(String userId, String scopeId, String memType) {
        return KV_PREFIX + KV_SEP + userId + KV_SEP + scopeId + KV_SEP + memType + KV_SEP + IDS_SUFFIX;
    }

    private static List<String> parseAllIds(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        int total = raw.length() / BYTE_NUM_PER_ID;
        List<String> ids = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int start = index * BYTE_NUM_PER_ID;
            ids.add(raw.substring(start, start + BYTE_NUM_PER_ID));
        }
        return ids;
    }

    private static String appendId(String raw, String memId) {
        return raw + memId;
    }

    private static String removeId(String raw, String memId) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int total = raw.length() / BYTE_NUM_PER_ID;
        for (int index = 0; index < total; index++) {
            int start = index * BYTE_NUM_PER_ID;
            int end = start + BYTE_NUM_PER_ID;
            if (raw.substring(start, end).equals(memId)) {
                return raw.substring(0, start) + raw.substring(end);
            }
        }
        return raw;
    }

    private static Map<String, Object> memoryDocToKvData(MemoryDoc doc, String userId, String scopeId) {
        ZonedDateTime timestamp = doc.getTimestamp() == null ? ZonedDateTime.now(ZoneId.systemDefault()) : doc.getTimestamp();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", doc.getId());
        data.put("user_id", userId);
        data.put("scope_id", scopeId);
        data.put("mem", doc.getText());
        data.put("mem_type", doc.getType());
        data.put("timestamp", TIMESTAMP_FORMAT.format(timestamp));
        data.putAll(doc.getFields());
        return data;
    }

    private static MemoryDoc kvDataToMemoryDoc(Map<String, Object> data, String memId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!Set.of("id", "mem", "mem_type", "timestamp", "user_id", "scope_id").contains(entry.getKey())) {
                fields.put(entry.getKey(), entry.getValue());
            }
        }

        return new MemoryDoc(
                memId,
                Objects.toString(data.getOrDefault("mem", ""), ""),
                Objects.toString(data.getOrDefault("mem_type", ""), ""),
                parseTimestamp(data.get("timestamp")),
                fields
        );
    }

    private static ZonedDateTime parseTimestamp(Object rawTimestamp) {
        if (rawTimestamp instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (rawTimestamp instanceof Number number) {
            return Instant.ofEpochMilli((long) (number.doubleValue() * 1000)).atZone(ZoneOffset.UTC);
        }
        if (rawTimestamp instanceof String text && !text.isEmpty()) {
            for (DateTimeFormatter formatter : List.of(TIMESTAMP_FORMAT, LEGACY_TIMESTAMP_FORMAT)) {
                try {
                    return LocalDateTime.parse(text, formatter).atZone(ZoneOffset.UTC);
                } catch (DateTimeParseException ignored) {
                }
            }
            try {
                return ZonedDateTime.parse(text);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(text).atZone(ZoneId.systemDefault());
                } catch (DateTimeParseException ignoredAgain) {
                    return ZonedDateTime.now(ZoneId.systemDefault());
                }
            }
        }
        return ZonedDateTime.now(ZoneId.systemDefault());
    }

    private static String getCollectionName(String userId, String scopeId, String memType) {
        return "uid_" + userId + "_gid_" + scopeId + "_mtype_" + memType;
    }

    private static String parseMemTypeFromCollection(String collectionName) {
        int marker = collectionName.lastIndexOf("_mtype_");
        if (marker < 0) {
            return null;
        }
        return collectionName.substring(marker + "_mtype_".length());
    }

    private static String readKvValue(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(rawValue);
    }

    private static byte[] writeKvValue(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, Object> readJsonMap(String rawText) {
        try {
            return OBJECT_MAPPER.readValue(rawText, MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse SimpleMemoryIndex KV payload", exception);
        }
    }

    private static String writeJsonMap(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize SimpleMemoryIndex KV payload", exception);
        }
    }

    private void decodeMemoryField(Map<String, Object> data) {
        if (codec == null || !data.containsKey("mem")) {
            return;
        }
        data.put("mem", codec.decode(Objects.toString(data.get("mem"), "")));
    }
}
