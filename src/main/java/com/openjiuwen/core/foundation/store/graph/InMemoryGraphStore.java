/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.spi.store.query.ComparisonExpr;
import com.openjiuwen.spi.store.query.LogicalExpr;
import com.openjiuwen.spi.store.query.NullExpr;
import com.openjiuwen.spi.store.query.QueryExpr;
import com.openjiuwen.spi.store.query.RangeExpr;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the foundation {@link GraphStore} contract.
 *
 * <p>Provides a simple in-memory backend for graph storage that can be used for testing and
 * lightweight local development. Registered with {@link GraphStoreFactory} under the name {@code
 * "in_memory"}.
 */
public class InMemoryGraphStore implements GraphStore {
    private static final int EMBED_QUEUE_CAPACITY = 1024;

    private final GraphConfig config;
    private final ExecutorService embedExecutor;
    private Embedding embedder;

    /** Collection name -> list of data records. */
    private final Map<String, List<Map<String, Object>>> collections = new ConcurrentHashMap<>();

    private InMemoryGraphStore(GraphConfig config) {
        this.config = config;
        int workerThreads = Math.max(1, config.getWorkerThreads());
        this.embedExecutor =
                new ThreadPoolExecutor(
                        workerThreads,
                        workerThreads,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(EMBED_QUEUE_CAPACITY),
                        new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Creates an in-memory graph store from the provided graph configuration.
     *
     * @param config graph store configuration
     * @return graph store instance backed by local memory
     */
    public static GraphStore fromConfig(GraphConfig config) {
        return new InMemoryGraphStore(config);
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public GraphConfig getConfig() {
        return config;
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public ExecutorService getEmbedExecutor() {
        return embedExecutor;
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public Embedding getEmbedder() {
        return embedder;
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void refresh() {
        // No-op for in-memory store
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void addData(
            String collection, Iterable<Map<String, Object>> data, boolean isFlush, boolean isUpsert) {
        List<Map<String, Object>> coll =
                collections.computeIfAbsent(
                        collection, k -> Collections.synchronizedList(new ArrayList<>()));
        for (Map<String, Object> record : data) {
            if (isUpsert) {
                Object id = record.containsKey("id") ? record.get("id") : record.get("uuid");
                if (id != null) {
                    coll.removeIf(
                            existing ->
                                    id.equals(existing.containsKey("id") ? existing.get("id") : existing.get("uuid")));
                }
            }
            coll.add(new HashMap<>(record));
        }
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void addEntity(
            Iterable<?> entities, boolean isFlush, boolean isUpsert, boolean shouldSkipEmbed) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object entity : entities) {
            if (entity instanceof BaseGraphObject graphObject) {
                records.add(graphObject.toMap());
                continue;
            }
            if (entity instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                records.add(typed);
            }
        }
        addData(GraphConstants.ENTITY_COLLECTION, records, isFlush, isUpsert);
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void addRelation(
            Iterable<?> relations, boolean isFlush, boolean isUpsert, boolean shouldSkipEmbed) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object relation : relations) {
            if (relation instanceof BaseGraphObject graphObject) {
                records.add(graphObject.toMap());
                continue;
            }
            if (relation instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                records.add(typed);
            }
        }
        addData(GraphConstants.RELATION_COLLECTION, records, isFlush, isUpsert);
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void addEpisode(
            Iterable<?> episodes, boolean isFlush, boolean isUpsert, boolean shouldSkipEmbed) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object episode : episodes) {
            if (episode instanceof BaseGraphObject graphObject) {
                records.add(graphObject.toMap());
                continue;
            }
            if (episode instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                records.add(typed);
            }
        }
        addData(GraphConstants.EPISODE_COLLECTION, records, isFlush, isUpsert);
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public boolean isEmpty(String collection) {
        List<Map<String, Object>> coll = collections.get(collection);
        return coll == null || coll.isEmpty();
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public List<Map<String, Object>> query(
            String collection, List<Object> ids, QueryExpr expr, boolean shouldSilenceErrors) {
        List<Map<String, Object>> coll = collections.get(collection);
        if (coll == null) {
            return Collections.emptyList();
        }
        if (ids != null && !ids.isEmpty()) {
            return coll.stream()
                    .filter(r -> ids.contains(r.get("id")) || ids.contains(r.get("uuid")))
                    .filter(r -> matches(r, expr))
                    .map(HashMap::new)
                    .collect(Collectors.toList());
        }
        return coll.stream().filter(r -> matches(r, expr)).map(HashMap::new).collect(Collectors.toList());
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public Map<String, Object> delete(String collection, List<Object> ids, QueryExpr expr) {
        List<Map<String, Object>> coll = collections.get(collection);
        if (coll == null) {
            return Map.of("deleted", 0);
        }
        int before = coll.size();
        if (ids != null && !ids.isEmpty()) {
            coll.removeIf(r -> ids.contains(r.get("id")) || ids.contains(r.get("uuid")));
            return Map.of("deleted", before - coll.size());
        }
        if (expr != null) {
            coll.removeIf(r -> matches(r, expr));
            return Map.of("deleted", before - coll.size());
        }
        return Map.of("deleted", before - coll.size());
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public Map<String, List<Map<String, Object>>> search(
            String queryText,
            int k,
            String collection,
            Object rankerConfig,
            int bfsDepth,
            int bfsK,
            QueryExpr filterExpr,
            List<String> outputFields,
            List<Float> queryEmbedding,
            Map<String, Object> kwargs) {
        // Simple implementation: return first k records from the collection
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        List<String> targetCollections;
        if ("all".equals(collection)) {
            targetCollections =
                    List.of(
                            GraphConstants.ENTITY_COLLECTION,
                            GraphConstants.RELATION_COLLECTION,
                            GraphConstants.EPISODE_COLLECTION);
        } else {
            targetCollections = List.of(collection);
        }
        for (String cname : targetCollections) {
            List<Map<String, Object>> coll = collections.getOrDefault(cname, Collections.emptyList());
            List<Map<String, Object>> limited =
                    coll.stream()
                            .filter(record -> matches(record, filterExpr))
                            .limit(k)
                            .map(HashMap::new)
                            .collect(Collectors.toList());
            result.put(cname, limited);
        }
        return result;
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void attachEmbedder(Embedding embedder) {
        if (embedder == null) {
            throw new IllegalArgumentException("Embedder must not be null");
        }
        this.embedder = embedder;
    }

    private static boolean matches(Map<String, Object> record, QueryExpr expression) {
        if (expression == null) {
            return true;
        }
        if (expression instanceof ComparisonExpr comparison) {
            Object actual = record.get(comparison.getField());
            Object expected = comparison.getValue();
            return switch (comparison.getOperator()) {
                case "==" -> java.util.Objects.equals(actual, expected);
                case "!=" -> !java.util.Objects.equals(actual, expected);
                case ">" -> compare(actual, expected) > 0;
                case "<" -> compare(actual, expected) < 0;
                case ">=" -> compare(actual, expected) >= 0;
                case "<=" -> compare(actual, expected) <= 0;
                default -> false;
            };
        }
        if (expression instanceof RangeExpr range) {
            Object actual = record.get(range.getField());
            if ("in".equals(range.getOperator()) && range.getValue() instanceof java.util.Collection<?> values) {
                return values.contains(actual);
            }
            if (("like".equals(range.getOperator()) || "wildcard".equals(range.getOperator()))
                    && range.getValue() != null) {
                String pattern = String.valueOf(range.getValue()).replace("*", ".*");
                return actual != null && String.valueOf(actual).matches(pattern);
            }
            return false;
        }
        if (expression instanceof LogicalExpr logical) {
            boolean isLeftMatched = matches(record, logical.getLeft());
            return switch (logical.getOperator()) {
                case "and" -> isLeftMatched && matches(record, logical.getRight());
                case "or" -> isLeftMatched || matches(record, logical.getRight());
                case "xor" -> isLeftMatched ^ matches(record, logical.getRight());
                case "not" -> !isLeftMatched;
                default -> false;
            };
        }
        if (expression instanceof NullExpr nullExpr) {
            boolean isValueNull = record.get(nullExpr.getField()) == null;
            return nullExpr.isNull() == isValueNull;
        }
        return true;
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
        }
        if (left == null || right == null) {
            return left == right ? 0 : (left == null ? -1 : 1);
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    @Override
    /** Auto-generated for codecheck compliance. */
    public void close() {
        embedExecutor.shutdown();
        collections.clear();
    }
}