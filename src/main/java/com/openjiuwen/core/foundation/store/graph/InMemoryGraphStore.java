/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.query.QueryExpr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Local deterministic graph store backend used when Python-compatible tests request
 * {@code backend="in_memory"}.
 *
 * <p>Mirrors Python's graph-store backend protocol in
 * {@code openjiuwen/core/foundation/store/graph/base_graph_store.py}.</p>
 */
public final class InMemoryGraphStore implements GraphStore {

    public static final String ENTITY_COLLECTION = "entity";
    public static final String RELATION_COLLECTION = "relation";
    public static final String EPISODE_COLLECTION = "episode";
    private static final String ALL_COLLECTIONS = "all";
    private static final Map<String, String> COLLECTION_ALIASES = Map.of(
            "entities", ENTITY_COLLECTION,
            "relations", RELATION_COLLECTION,
            "episodes", EPISODE_COLLECTION
    );

    private final GraphConfig config;
    private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();
    private Embedding embedder;

    public InMemoryGraphStore(GraphConfig config) {
        this.config = config;
        this.embedder = config.getEmbeddingModel();
        collections.put(ENTITY_COLLECTION, new ConcurrentHashMap<>());
        collections.put(RELATION_COLLECTION, new ConcurrentHashMap<>());
        collections.put(EPISODE_COLLECTION, new ConcurrentHashMap<>());
    }

    public static InMemoryGraphStore fromConfig(GraphConfig config) {
        return fromConfig(config, Map.of());
    }

    public static InMemoryGraphStore fromConfig(GraphConfig config, Map<String, Object> kwargs) {
        return new InMemoryGraphStore(config);
    }

    @Override
    public GraphConfig getConfig() {
        return config;
    }

    @Override
    public Optional<Semaphore> getSemophore() {
        return Optional.empty();
    }

    @Override
    public Optional<Embedding> getEmbedder() {
        return Optional.ofNullable(embedder);
    }

    @Override
    public boolean isReturnSimilarityScore() {
        return true;
    }

    @Override
    public void rebuild() {
        collections.values().forEach(Map::clear);
    }

    @Override
    public CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> addData(String collection,
                                           Iterable<Map<String, Object>> data,
                                           boolean flush,
                                           boolean upsert,
                                           Map<String, Object> kwargs) {
        Map<String, Map<String, Object>> target = collection(collection);
        for (Map<String, Object> row : data) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            String uuid = String.valueOf(copy.getOrDefault("uuid", GraphStoreUtils.getUuid()));
            copy.put("uuid", uuid);
            target.put(uuid, copy);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
        return addObjects(ENTITY_COLLECTION, entities);
    }

    @Override
    public CompletableFuture<Void> addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
        return addObjects(RELATION_COLLECTION, relations);
    }

    @Override
    public CompletableFuture<Void> addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
        return addObjects(EPISODE_COLLECTION, episodes);
    }

    @Override
    public boolean isEmpty(String collection) {
        if (ALL_COLLECTIONS.equals(collection)) {
            return collection(ENTITY_COLLECTION).isEmpty()
                    && collection(RELATION_COLLECTION).isEmpty()
                    && collection(EPISODE_COLLECTION).isEmpty();
        }
        return collection(collection).isEmpty();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                              List<?> ids,
                                                              QueryExpr expr,
                                                              boolean silenceErrors,
                                                              Map<String, Object> kwargs) {
        Map<String, Map<String, Object>> source = collection(collection);
        if (ids == null) {
            List<Map<String, Object>> rows = source.values().stream()
                    .map(row -> new LinkedHashMap<String, Object>(row))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(rows);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object id : ids) {
            Map<String, Object> row = source.get(String.valueOf(id));
            if (row != null) {
                rows.add(new LinkedHashMap<>(row));
            }
        }
        return CompletableFuture.completedFuture(rows);
    }

    @Override
    public CompletableFuture<Map<String, Object>> delete(String collection,
                                                         List<?> ids,
                                                         QueryExpr expr,
                                                         Map<String, Object> kwargs) {
        Map<String, Map<String, Object>> target = collection(collection);
        if (ids == null) {
            target.clear();
        } else {
            ids.forEach(id -> target.remove(String.valueOf(id)));
        }
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, List<Map<String, Object>>>> search(String query,
                                                                            int k,
                                                                            String collection,
                                                                            BaseRankConfig rankerConfig,
                                                                            Reranker reranker,
                                                                            int bfsDepth,
                                                                            int bfsK,
                                                                            QueryExpr filterExpr,
                                                                            List<String> outputFields,
                                                                            List<Double> queryEmbedding,
                                                                            Map<String, Object> kwargs) {
        int limit = Math.max(0, k);
        if (ALL_COLLECTIONS.equals(collection)) {
            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
            result.put("entities", searchRows(ENTITY_COLLECTION, limit));
            result.put("relations", searchRows(RELATION_COLLECTION, limit));
            result.put("episodes", searchRows(EPISODE_COLLECTION, limit));
            return CompletableFuture.completedFuture(result);
        }
        String normalizedCollection = normalizeCollection(collection);
        String outputKey = collection;
        return CompletableFuture.completedFuture(Map.of(outputKey, searchRows(normalizedCollection, limit)));
    }

    @Override
    public void attachEmbedder(Embedding embedder) {
        this.embedder = embedder;
    }

    @Override
    public void close() {
        collections.values().forEach(Map::clear);
    }

    private CompletableFuture<Void> addObjects(String collection, Iterable<?> objects) {
        Map<String, Map<String, Object>> target = collection(collection);
        for (Object object : objects) {
            Map<String, Object> row = graphObjectToMap(object);
            target.put(String.valueOf(row.get("uuid")), row);
        }
        return CompletableFuture.completedFuture(null);
    }

    private Map<String, Map<String, Object>> collection(String collection) {
        return collections.computeIfAbsent(normalizeCollection(collection), ignored -> new ConcurrentHashMap<>());
    }

    private List<Map<String, Object>> searchRows(String collection, int limit) {
        return collection(collection).values().stream()
                .limit(limit)
                .map(row -> {
                    Map<String, Object> copy = new LinkedHashMap<>(row);
                    copy.putIfAbsent("distance", 1.0d);
                    return copy;
                })
                .toList();
    }

    private static String normalizeCollection(String collection) {
        return COLLECTION_ALIASES.getOrDefault(collection, collection);
    }

    private static Map<String, Object> graphObjectToMap(Object object) {
        if (object instanceof Entity entity) {
            Map<String, Object> row = baseMap(entity);
            row.put("name", entity.getName());
            row.put("relations", entity.serializeRelations());
            row.put("episodes", entity.serializeEpisodes());
            row.put("attributes", entity.getAttributes());
            return row;
        }
        if (object instanceof Relation relation) {
            Map<String, Object> row = baseMap(relation);
            row.put("name", relation.getName());
            row.put("lhs", relation.serializeLhs());
            row.put("rhs", relation.serializeRhs());
            row.put("valid_since", relation.getValidSince());
            row.put("valid_until", relation.getValidUntil());
            row.put("offset_since", relation.getOffsetSince());
            row.put("offset_until", relation.getOffsetUntil());
            return row;
        }
        if (object instanceof Episode episode) {
            Map<String, Object> row = baseMap(episode);
            row.put("entities", episode.serializeEntities());
            row.put("valid_since", episode.getValidSince());
            return row;
        }
        if (object instanceof Map<?, ?> map) {
            Map<String, Object> row = stringObjectMap(map);
            row.putIfAbsent("uuid", GraphStoreUtils.getUuid());
            return row;
        }
        throw new IllegalArgumentException("Unsupported graph object: " + object);
    }

    private static Map<String, Object> baseMap(BaseGraphObject object) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("uuid", object.getUuid());
        row.put("created_at", object.getCreatedAt());
        row.put("user_id", object.getUserId());
        row.put("obj_type", object.getObjType());
        row.put("language", object.getLanguage());
        row.put("content", object.getContent());
        row.put("metadata", object.getMetadata());
        return row;
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return normalized;
    }
}
