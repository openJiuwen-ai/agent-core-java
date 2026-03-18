/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.spi.store.query.QueryExpr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the foundation {@link GraphStore} contract.
 * <p>
 * Provides a simple in-memory backend for graph storage that can be used for
 * testing and lightweight local development. Registered with
 * {@link GraphStoreFactory} under the name {@code "in_memory"}.
 */
public class InMemoryGraphStore implements GraphStore {

    private final GraphConfig config;
    private final ExecutorService embedExecutor;
    private Embedding embedder;

    /** Collection name -> list of data records. */
    private final Map<String, List<Map<String, Object>>> collections = new ConcurrentHashMap<>();

    private InMemoryGraphStore(GraphConfig config) {
        this.config = config;
        this.embedExecutor = Executors.newFixedThreadPool(
                Math.max(1, config.getWorkerThreads()));
    }

    /**
     * Factory method: create an InMemoryGraphStore from config.
     */
    public static GraphStore fromConfig(GraphConfig config) {
        return new InMemoryGraphStore(config);
    }

    @Override
    public GraphConfig getConfig() {
        return config;
    }

    @Override
    public ExecutorService getEmbedExecutor() {
        return embedExecutor;
    }

    @Override
    public Embedding getEmbedder() {
        return embedder;
    }

    @Override
    public void refresh() {
        // No-op for in-memory store
    }

    @Override
    public void addData(String collection, Iterable<Map<String, Object>> data,
                        boolean flush, boolean upsert) {
        List<Map<String, Object>> coll = collections.computeIfAbsent(
                collection, k -> Collections.synchronizedList(new ArrayList<>()));
        for (Map<String, Object> record : data) {
            if (upsert) {
                Object id = record.get("id");
                if (id != null) {
                    coll.removeIf(existing -> id.equals(existing.get("id")));
                }
            }
            coll.add(new HashMap<>(record));
        }
    }

    @Override
    public void addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object entity : entities) {
            if (entity instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                records.add(typed);
            }
        }
        addData("entities", records, flush, upsert);
    }

    @Override
    public void addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object relation : relations) {
            if (relation instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                records.add(typed);
            }
        }
        addData("relations", records, flush, upsert);
    }

    @Override
    public void addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object episode : episodes) {
            if (episode instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                records.add(typed);
            }
        }
        addData("episodes", records, flush, upsert);
    }

    @Override
    public boolean isEmpty(String collection) {
        List<Map<String, Object>> coll = collections.get(collection);
        return coll == null || coll.isEmpty();
    }

    @Override
    public List<Map<String, Object>> query(String collection, List<Object> ids,
                                           QueryExpr expr, boolean silenceErrors) {
        List<Map<String, Object>> coll = collections.get(collection);
        if (coll == null) {
            return Collections.emptyList();
        }
        if (ids != null && !ids.isEmpty()) {
            return coll.stream()
                    .filter(r -> ids.contains(r.get("id")))
                    .map(HashMap::new)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>(coll);
    }

    @Override
    public Map<String, Object> delete(String collection, List<Object> ids,
                                      QueryExpr expr) {
        List<Map<String, Object>> coll = collections.get(collection);
        if (coll == null) {
            return Map.of("deleted", 0);
        }
        int before = coll.size();
        if (ids != null && !ids.isEmpty()) {
            coll.removeIf(r -> ids.contains(r.get("id")));
        }
        return Map.of("deleted", before - coll.size());
    }

    @Override
    public Map<String, List<Map<String, Object>>> search(String queryText, int k,
                                                          String collection, Object rankerConfig,
                                                          int bfsDepth, int bfsK,
                                                          QueryExpr filterExpr,
                                                          List<String> outputFields,
                                                          List<Float> queryEmbedding,
                                                          Map<String, Object> kwargs) {
        // Simple implementation: return first k records from the collection
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        List<String> targetCollections;
        if ("all".equals(collection)) {
            targetCollections = List.of("entities", "relations", "episodes");
        } else {
            targetCollections = List.of(collection);
        }
        for (String cname : targetCollections) {
            List<Map<String, Object>> coll = collections.getOrDefault(cname, Collections.emptyList());
            List<Map<String, Object>> limited = coll.stream()
                    .limit(k)
                    .map(HashMap::new)
                    .collect(Collectors.toList());
            result.put(cname, limited);
        }
        return result;
    }

    @Override
    public void attachEmbedder(Embedding embedder) {
        if (embedder == null) {
            throw new IllegalArgumentException("Embedder must not be null");
        }
        this.embedder = embedder;
    }

    @Override
    public void close() {
        embedExecutor.shutdown();
        collections.clear();
    }
}
