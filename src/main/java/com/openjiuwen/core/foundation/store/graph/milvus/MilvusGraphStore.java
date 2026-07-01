/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph.milvus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.base_reranker.Reranker;
import com.openjiuwen.core.foundation.store.graph.BaseGraphObject;
import com.openjiuwen.core.foundation.store.graph.BaseRankConfig;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.Episode;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStore;
import com.openjiuwen.core.foundation.store.graph.GraphStoreConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStoreUtils;
import com.openjiuwen.core.foundation.store.graph.RRFRankConfig;
import com.openjiuwen.core.foundation.store.graph.Relation;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import com.openjiuwen.core.foundation.store.query.MilvusQueryLanguage;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.foundation.store.query.QueryExpressions;
import com.openjiuwen.core.foundation.store.query.QueryLanguageRegistry;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.exception.MilvusClientException;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.database.request.DropDatabaseReq;
import io.milvus.v2.service.utility.request.CompactReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Milvus graph store implementation.
 * <p>
 * Mirrors Python's {@code MilvusGraphStore} in
 * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
 * </p>
 */
public class MilvusGraphStore implements GraphStore {

    public static final String PYTHON_MODULE =
            "openjiuwen/core/foundation/store/graph/milvus/milvus_support.py";

    private static final LoggerProtocol STORE_LOGGER = Loggers.STORE;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SIMILARITY_METRICS = Set.of("IP", "COSINE");
    private static final List<String> COLLECTIONS = List.of(
            GraphStoreConstants.ENTITY_COLLECTION,
            GraphStoreConstants.RELATION_COLLECTION,
            GraphStoreConstants.EPISODE_COLLECTION
    );

    private final GraphConfig config;
    private final MilvusClientAdapter client;
    private final String alias;
    private final String metric;
    private final Map<String, Object> fullTextSearchParams;
    private final Map<String, Object> denseSearchParams;
    private final Map<String, List<String>> fieldDef;
    private Embedding embedder;

    static {
        QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", MilvusQueryLanguage.MILVUS_DEF, true);
    }

    public MilvusGraphStore(GraphConfig config) {
        this(config, new DefaultMilvusClientAdapter(config));
    }

    MilvusGraphStore(GraphConfig config, MilvusClientAdapter client) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        Map<String, Object> extras = new LinkedHashMap<>(config.getExtras());
        this.alias = String.valueOf(extras.computeIfAbsent("alias", ignored -> "graph-store-" + System.identityHashCode(this)));
        this.metric = normalizeMetric(config.getDbEmbedConfig().getDistanceMetric());
        this.fullTextSearchParams = Collections.unmodifiableMap(Map.of("metric_type", "BM25"));
        this.denseSearchParams = Collections.unmodifiableMap(Map.of("metric_type", metric));
        if (config.getEmbeddingModel() != null) {
            attachEmbedder(config.getEmbeddingModel());
        }
        this.fieldDef = Collections.unmodifiableMap(buildFieldDef());
        ensureDatabaseReady();
        buildIndices();
    }

    public static MilvusGraphStore fromConfig(GraphConfig config) {
        return fromConfig(config, Map.of());
    }

    public static MilvusGraphStore fromConfig(GraphConfig config, Map<String, Object> kwargs) {
        return new MilvusGraphStore(config);
    }

    public String getAlias() {
        return alias;
    }

    public String getMetric() {
        return metric;
    }

    public Map<String, Object> getFullTextSearchParams() {
        return fullTextSearchParams;
    }

    public Map<String, Object> getDenseSearchParams() {
        return denseSearchParams;
    }

    public Map<String, List<String>> getFieldDef() {
        return fieldDef;
    }

    @Override
    public GraphConfig getConfig() {
        return config;
    }

    @Override
    public Optional<Semaphore> getSemophore() {
        return embedder == null ? Optional.empty() : Optional.ofNullable(embedder.getLimiter());
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
        for (String collection : client.listCollections()) {
            client.dropCollection(collection);
        }
        if (!config.getName().isBlank()) {
            client.dropDatabase(config.getName());
            client.createDatabase(config.getName());
            client.useDatabase(config.getName());
        }
        buildIndices();
    }

    @Override
    public CompletableFuture<Void> refresh(boolean skipCompact, Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            for (String collection : fieldDef.keySet()) {
                flushAndCompact(collection, skipCompact);
            }
        });
    }

    @Override
    public CompletableFuture<Void> addData(String collection,
                                           Iterable<Map<String, Object>> data,
                                           boolean flush,
                                           boolean upsert,
                                           Map<String, Object> kwargs) {
        return CompletableFuture.runAsync(() -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : data) {
                rows.add(new LinkedHashMap<>(item));
            }
            client.write(collection, rows, upsert);
            if (flush) {
                client.flush(collection);
            }
        });
    }

    @Override
    public CompletableFuture<Void> addEntity(Iterable<?> entities, boolean flush, boolean upsert, boolean noEmbed) {
        return addGraphObjects(GraphStoreConstants.ENTITY_COLLECTION, entities, flush, upsert, noEmbed);
    }

    @Override
    public CompletableFuture<Void> addRelation(Iterable<?> relations, boolean flush, boolean upsert, boolean noEmbed) {
        return addGraphObjects(GraphStoreConstants.RELATION_COLLECTION, relations, flush, upsert, noEmbed);
    }

    @Override
    public CompletableFuture<Void> addEpisode(Iterable<?> episodes, boolean flush, boolean upsert, boolean noEmbed) {
        return addGraphObjects(GraphStoreConstants.EPISODE_COLLECTION, episodes, flush, upsert, noEmbed);
    }

    @Override
    public boolean isEmpty(String collection) {
        return client.rowCount(collection) == 0L;
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> query(String collection,
                                                              List<?> ids,
                                                              QueryExpr expr,
                                                              boolean silenceErrors,
                                                              Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            String exprString;
            if (expr != null) {
                exprString = String.valueOf(expr.toExpr("milvus"));
            } else if ((ids == null || ids.isEmpty()) && !kwargs.containsKey("limit")) {
                throw graphParamInvalid("Argument \"limit\" must be set to positive integer when \"expr\" and \"ids\" are None");
            } else {
                exprString = null;
            }
            List<String> outputFields = outputFields(collection, kwargs);
            try {
                return client.query(collection, ids, exprString, outputFields, kwargs);
            } catch (MilvusClientException exception) {
                if (silenceErrors) {
                    return List.of();
                }
                throw exception;
            } catch (RuntimeException exception) {
                if (silenceErrors) {
                    return List.of();
                }
                throw exception;
            }
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> delete(String collection,
                                                         List<?> ids,
                                                         QueryExpr expr,
                                                         Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            String exprString;
            if (ids != null && !ids.isEmpty()) {
                exprString = String.valueOf(QueryExpressions.inList("uuid", ids).toExpr("milvus"));
            } else if (expr != null) {
                exprString = String.valueOf(expr.toExpr("milvus"));
            } else {
                throw graphParamInvalid("Either \"ids\" or \"expr\" must be supplied");
            }
            return client.delete(collection, exprString, kwargs);
        });
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
        return CompletableFuture.supplyAsync(() -> searchSync(
                query,
                k,
                collection,
                rankerConfig,
                reranker,
                bfsDepth,
                bfsK,
                filterExpr,
                outputFields,
                queryEmbedding,
                kwargs == null ? Map.of() : kwargs
        ));
    }

    @Override
    public void attachEmbedder(Embedding embedder) {
        if (embedder == null) {
            throw graphParamInvalid("Embedder must be instance of Embedding or a subclass of it, got null instead.");
        }
        if (config.getEmbedDim() != embedder.getDimension()) {
            throw graphParamInvalid("MilvusGraphStore has different config.embed_dim and embedder.dimension ("
                    + config.getEmbedDim() + " != " + embedder.getDimension() + ")");
        }
        if (this.embedder != null) {
            STORE_LOGGER.warning("{} .embedder has been redefined from {} to {}",
                    getClass().getSimpleName(), this.embedder, embedder);
        }
        this.embedder = embedder;
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception exception) {
            STORE_LOGGER.error("Failed to close milvus graph store connection: {}", exception);
        }
    }

    static String normalizeMetric(String metric) {
        return metric.replace("dot", "ip").replace("euclidean", "l2").toUpperCase(Locale.ROOT);
    }

    private void ensureDatabaseReady() {
        if (config.getName().isBlank()) {
            return;
        }
        if (!client.listDatabases().contains(config.getName())) {
            client.createDatabase(config.getName());
        }
        client.useDatabase(config.getName());
    }

    private void buildIndices() {
        if (embedder != null && config.getEmbedDim() != embedder.getDimension()) {
            throw graphParamInvalid("MilvusGraphStore has different config.embed_dim and embedder.dimension ("
                    + config.getEmbedDim() + " != " + embedder.getDimension() + ")");
        }

        for (String collection : COLLECTIONS) {
            if (client.hasCollection(collection)) {
                try {
                    client.loadCollection(collection);
                    continue;
                } catch (MilvusClientException exception) {
                    STORE_LOGGER.error("Milvus graph store failed to load collection ({}/{}): {}",
                            config.getName(), collection, exception);
                    rebuild();
                    return;
                }
            }
            GenerateMilvusSchema.SchemaResult schema = GenerateMilvusSchema.generateSchemaAndIndex(
                    collection,
                    config.getDbStorageConfig(),
                    config.getDbEmbedConfig(),
                    config.getEmbedDim(),
                    true
            );
            client.createCollection(collection, schema, config.getEmbedDim(), metric);
            client.loadCollection(collection);
        }
    }

    private List<Map<String, Object>> rankResults(String query,
                                                  List<Map<String, Object>> candidates,
                                                  Reranker reranker,
                                                  String language,
                                                  double minScore) {
        boolean isSimilarity = SIMILARITY_METRICS.contains(metric);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            double score = numberAsDouble(candidate.get("distance"));
            if ((isSimilarity && score >= minScore) || (!isSimilarity && score <= minScore)) {
                filtered.add(candidate);
            }
        }
        if (reranker != null) {
            rerank(query, filtered, reranker, language, Map.of());
            return filtered;
        }
        filtered.sort(Comparator.comparingDouble(row -> numberAsDouble(row.get("distance"))));
        if (isSimilarity) {
            Collections.reverse(filtered);
        }
        return filtered;
    }

    public static void rerank(String query,
                              List<Map<String, Object>> candidates,
                              Reranker reranker,
                              String language,
                              Map<String, Object> kwargs) {
        Map<String, Object> rerankKwargs = new LinkedHashMap<>(kwargs == null ? Map.of() : kwargs);
        rerankKwargs.put("language", language);
        List<Object> docs = candidates.stream()
                .map(row -> row.get("content"))
                .collect(Collectors.toList());
        Map<String, Double> llmScores = reranker.rerank(query, docs, null, rerankKwargs).join();
        for (Map<String, Object> doc : candidates) {
            String content = String.valueOf(doc.get("content"));
            doc.put("distance", llmScores.getOrDefault(content, 0.0d));
        }
        candidates.sort(Comparator.comparingDouble(row -> numberAsDouble(row.get("distance"))));
        Collections.reverse(candidates);
    }

    private Map<String, List<Map<String, Object>>> searchSync(String query,
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
        String language = String.valueOf(kwargs.getOrDefault("language", "en"));
        double minScore = numberAsDouble(kwargs.getOrDefault("min_score", 0.0d));
        Map<String, List<Map<String, Object>>> outputDict = new LinkedHashMap<>();
        for (String col : COLLECTIONS) {
            outputDict.put(col, new ArrayList<>());
        }
        if ("all".equalsIgnoreCase(collection.strip())) {
            for (String col : COLLECTIONS) {
                Map<String, List<Map<String, Object>>> result = searchSync(
                        query,
                        k,
                        col,
                        rankerConfig,
                        null,
                        bfsDepth,
                        bfsK,
                        filterExpr,
                        outputFields,
                        queryEmbedding,
                        Map.of("language", language, "min_score", minScore)
                );
                outputDict.put(col, result.getOrDefault(col, List.of()));
            }
            combinedRerank(query, outputDict, reranker, language, minScore);
            return outputDict;
        }

        List<String> fields = outputFields == null || outputFields.isEmpty()
                ? fieldDef.getOrDefault(collection, List.of())
                : outputFields;
        QueryExpr expr = filterExpr;
        if (bfsDepth > 0
                && (GraphStoreConstants.ENTITY_COLLECTION.equals(collection)
                || GraphStoreConstants.RELATION_COLLECTION.equals(collection))) {
            Set<String> uuids = new LinkedHashSet<>();
            Map<String, Map<String, Object>> allResults = new LinkedHashMap<>();
            List<Double> embedding = queryEmbedding == null ? queryEmbedding(query) : queryEmbedding;
            boolean isSimilarity = SIMILARITY_METRICS.contains(metric);
            for (int depth = 0; depth <= bfsDepth; depth++) {
                boolean graphExpansion = depth < bfsDepth;
                List<Map<String, Object>> results = rawHybridSearch(
                        query,
                        k,
                        collection,
                        rankerConfig,
                        true,
                        embedding,
                        expr,
                        fields,
                        language,
                        null,
                        minScore
                );
                Map<String, Map<String, Object>> newResults = new LinkedHashMap<>();
                for (Map<String, Object> doc : results) {
                    Object uuid = doc.get("uuid");
                    if (uuid != null) {
                        newResults.put(String.valueOf(uuid), doc);
                    }
                }
                Set<String> newUuids = new LinkedHashSet<>(newResults.keySet());
                newUuids.removeAll(uuids);
                allResults.putAll(newResults);
                if (graphExpansion && !newUuids.isEmpty()) {
                    if (GraphStoreConstants.ENTITY_COLLECTION.equals(collection)) {
                        newUuids = expandEntities(filterExpr, newUuids);
                    } else {
                        newUuids = expandRelations(filterExpr, newUuids, newResults);
                    }
                    newUuids.removeAll(uuids);
                    if (newUuids.isEmpty()) {
                        break;
                    }
                    uuids.addAll(newUuids);
                    if (bfsK < newUuids.size()) {
                        newUuids = limitExpansionUuids(newUuids, newResults, bfsK, isSimilarity);
                    }
                    if (GraphStoreConstants.ENTITY_COLLECTION.equals(collection)) {
                        expr = QueryExpressions.inList("uuid", newUuids);
                    } else {
                        expr = QueryExpressions.inList("lhs", newUuids).or(QueryExpressions.inList("rhs", newUuids));
                    }
                    if (filterExpr != null) {
                        expr = filterExpr.and(expr);
                    }
                }
            }
            List<Map<String, Object>> ranked = rankResults(
                    query,
                    new ArrayList<>(allResults.values()),
                    reranker,
                    language,
                    minScore
            );
            outputDict.put(collection, ranked.subList(0, Math.min(k, ranked.size())));
            return outputDict;
        }

        List<Map<String, Object>> results = rawHybridSearch(
                query,
                k,
                collection,
                rankerConfig,
                false,
                queryEmbedding,
                expr,
                fields,
                language,
                reranker,
                minScore
        );
        outputDict.put(collection, results);
        return outputDict;
    }

    private void combinedRerank(String query,
                                Map<String, List<Map<String, Object>>> results,
                                Reranker reranker,
                                String language,
                                double minScore) {
        if (reranker == null) {
            return;
        }
        List<Map<String, Object>> entities = deepCopyRows(results.get(GraphStoreConstants.ENTITY_COLLECTION));
        List<Map<String, Object>> relations = deepCopyRows(results.get(GraphStoreConstants.RELATION_COLLECTION));
        Map<String, Map<String, Object>> relUuids = new LinkedHashMap<>();
        for (Map<String, Object> relation : relations) {
            Object uuid = relation.get("uuid");
            if (uuid != null) {
                relUuids.put(String.valueOf(uuid), relation);
            }
        }
        for (Map<String, Object> entity : entities) {
            String originalContent = String.valueOf(entity.getOrDefault("content", ""));
            entity.put("original_content", originalContent);
            List<RelationContent> content = new ArrayList<>();
            for (Object relationId : asCollection(entity.get("relations"))) {
                Map<String, Object> relation = relUuids.get(String.valueOf(relationId));
                if (relation != null) {
                    content.add(new RelationContent(
                            String.valueOf(relation.getOrDefault("content", "")),
                            numberAsDouble(relation.get("distance"))
                    ));
                }
            }
            content.sort(Comparator.comparingDouble(RelationContent::distance).reversed());
            if (!content.isEmpty()) {
                List<String> lines = new ArrayList<>();
                lines.add(originalContent);
                lines.add("----------");
                content.forEach(item -> lines.add(item.content()));
                entity.put("content", String.join("\n - ", lines));
            }
        }
        entities = rankResults(query, entities, reranker, language, minScore);
        for (Map<String, Object> entity : entities) {
            entity.put("content", entity.remove("original_content"));
        }
        results.put(GraphStoreConstants.ENTITY_COLLECTION, entities);
    }

    private Set<String> expandEntities(QueryExpr expr, Set<String> uuids) {
        if (uuids.isEmpty()) {
            return Set.of();
        }
        QueryExpr finalExpr = QueryExpressions.inList("lhs", uuids).or(QueryExpressions.inList("rhs", uuids));
        if (expr != null) {
            finalExpr = expr.and(finalExpr);
        }
        List<Map<String, Object>> rows = client.query(
                GraphStoreConstants.RELATION_COLLECTION,
                null,
                String.valueOf(finalExpr.toExpr("milvus")),
                List.of("lhs", "rhs"),
                Map.of()
        );
        Set<String> expansionResults = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row.get("lhs") != null) {
                expansionResults.add(String.valueOf(row.get("lhs")));
            }
            if (row.get("rhs") != null) {
                expansionResults.add(String.valueOf(row.get("rhs")));
            }
        }
        return expansionResults;
    }

    private Set<String> expandRelations(QueryExpr expr, Set<String> uuids, Map<String, Map<String, Object>> lookup) {
        if (uuids.isEmpty()) {
            return Set.of();
        }
        List<String> nodeUuids = new ArrayList<>();
        for (String relationUuid : uuids) {
            Map<String, Object> relation = lookup.get(relationUuid);
            if (relation != null) {
                if (relation.get("lhs") != null) {
                    nodeUuids.add(String.valueOf(relation.get("lhs")));
                }
                if (relation.get("rhs") != null) {
                    nodeUuids.add(String.valueOf(relation.get("rhs")));
                }
            }
        }
        QueryExpr finalExpr = QueryExpressions.inList("uuid", nodeUuids);
        if (expr != null) {
            finalExpr = expr.and(finalExpr);
        }
        List<Map<String, Object>> rows = client.query(
                GraphStoreConstants.ENTITY_COLLECTION,
                null,
                String.valueOf(finalExpr.toExpr("milvus")),
                List.of("relations"),
                Map.of()
        );
        Set<String> expansionResults = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            for (Object relationId : asCollection(row.get("relations"))) {
                expansionResults.add(String.valueOf(relationId));
            }
        }
        return expansionResults;
    }

    private List<Double> queryEmbedding(String query) {
        if (embedder == null) {
            throw graphParamInvalid("MilvusGraphStore requires an embedder before vector search.");
        }
        return embedder.embedQuery(query).join();
    }

    private CompletableFuture<Void> addGraphObjects(String collection,
                                                    Iterable<?> data,
                                                    boolean flush,
                                                    boolean upsert,
                                                    boolean noEmbed) {
        return CompletableFuture.runAsync(() -> {
            long startNanos = System.nanoTime();
            List<BaseGraphObject> graphObjects = new ArrayList<>();
            for (Object item : data) {
                if (!(item instanceof BaseGraphObject graphObject)) {
                    throw graphParamInvalid("Graph data item must be a BaseGraphObject, got " + item);
                }
                graphObjects.add(graphObject);
            }
            if (!graphObjects.isEmpty()) {
                embedGraphObjects(graphObjects, noEmbed);
                List<Map<String, Object>> rows = graphObjects.stream()
                        .map(this::graphObjectToMap)
                        .collect(Collectors.toCollection(ArrayList::new));
                try {
                    client.write(collection, rows, upsert);
                } catch (MilvusClientException exception) {
                    retryBatchedInsert(collection, graphObjects, rows, upsert, exception);
                }
            }
            if (flush) {
                client.flush(collection);
            }
            STORE_LOGGER.debug("Add graph memory [{}] took {}s",
                    collection, (System.nanoTime() - startNanos) / 1_000_000_000.0d);
        });
    }

    private void embedGraphObjects(List<BaseGraphObject> graphObjects, boolean noEmbed) {
        if (noEmbed) {
            return;
        }
        if (embedder == null) {
            throw graphParamInvalid("MilvusGraphStore requires an embedder unless no_embed is true.");
        }
        List<BaseGraphObject.EmbeddingTask> tasks = new ArrayList<>();
        for (BaseGraphObject graphObject : graphObjects) {
            tasks.addAll(graphObject.fetchEmbedTask());
        }
        if (tasks.isEmpty()) {
            return;
        }
        List<String> texts = tasks.stream()
                .map(BaseGraphObject.EmbeddingTask::contentToEmbed)
                .collect(Collectors.toList());
        List<List<Double>> embeddings = embedder.embedDocuments(texts, config.getEmbedBatchSize()).join();
        for (int index = 0; index < tasks.size(); index++) {
            applyEmbedding(tasks.get(index), embeddings.get(index));
        }
    }

    private void retryBatchedInsert(String collection,
                                    List<BaseGraphObject> graphObjects,
                                    List<Map<String, Object>> rows,
                                    boolean upsert,
                                    RuntimeException original) {
        STORE_LOGGER.info("Milvus data addition failed, try batching with size of {}", config.getEmbedBatchSize());
        try {
            client.delete(collection, String.valueOf(QueryExpressions.inList(
                    "uuid",
                    graphObjects.stream().map(BaseGraphObject::getUuid).collect(Collectors.toList())
            ).toExpr("milvus")), Map.of());
        } catch (RuntimeException cleanupException) {
            STORE_LOGGER.warning("Milvus data addition failure clean up failed: {}", cleanupException);
        }
        for (List<Map<String, Object>> batch : GraphStoreUtils.batched(rows, config.getEmbedBatchSize())) {
            client.write(collection, batch, upsert);
        }
    }

    private Map<String, Object> graphObjectToMap(BaseGraphObject item) {
        truncateForStorage(item);
        Map<String, Object> row = OBJECT_MAPPER.convertValue(item, new TypeReference<>() {
        });
        if (item instanceof Entity entity) {
            row.put("relations", entity.serializeRelations());
            row.put("episodes", entity.serializeEpisodes());
        } else if (item instanceof Relation relation) {
            row.put("lhs", relation.serializeLhs());
            row.put("rhs", relation.serializeRhs());
        } else if (item instanceof Episode episode) {
            row.put("entities", episode.serializeEntities());
        }
        row.entrySet().removeIf(entry -> entry.getValue() == null);
        return row;
    }

    private void truncateForStorage(BaseGraphObject item) {
        int contentLimit = config.getDbStorageConfig().getContent();
        if (item.getContent().length() > contentLimit) {
            item.setContent(truncateWithEllipsis(item.getContent(), contentLimit));
        }
        if (item instanceof Entity entity && entity.getName().length() > config.getDbStorageConfig().getName()) {
            entity.setName(truncateWithEllipsis(entity.getName(), config.getDbStorageConfig().getName()));
        } else if (item instanceof Relation relation && relation.getName().length() > config.getDbStorageConfig().getName()) {
            relation.setName(truncateWithEllipsis(relation.getName(), config.getDbStorageConfig().getName()));
        }
    }

    private static String truncateWithEllipsis(String value, int limit) {
        if (limit <= 3) {
            return value.substring(0, limit);
        }
        return value.substring(0, limit - 3) + "...";
    }

    private void applyEmbedding(BaseGraphObject.EmbeddingTask task, List<Double> embedding) {
        if ("content_embedding".equals(task.attributeName())) {
            task.graphObject().setContentEmbedding(embedding);
        } else if ("name_embedding".equals(task.attributeName()) && task.graphObject() instanceof Entity entity) {
            entity.setNameEmbedding(embedding);
        } else {
            throw graphParamInvalid("Unsupported graph embedding attribute: " + task.attributeName());
        }
    }

    private List<Map<String, Object>> rawHybridSearch(String query,
                                                      int k,
                                                      String collection,
                                                      BaseRankConfig rankerConfig,
                                                      boolean skipRanking,
                                                      List<Double> queryEmbedding,
                                                      QueryExpr expr,
                                                      List<String> outputFields,
                                                      String language,
                                                      Reranker reranker,
                                                      double minScore) {
        List<Double> embedding = queryEmbedding == null ? queryEmbedding(query) : queryEmbedding;
        String exprString = expr == null ? "" : String.valueOf(expr.toExpr("milvus"));
        List<SearchRequest> requests = getSearchRequests(query, embedding, k, exprString);
        RankerAndRequests rankerAndRequests = getRankerAndRequests(rankerConfig, collection, requests);
        List<Map<String, Object>> result = client.hybridSearch(
                collection,
                rankerAndRequests.requests(),
                rankerAndRequests.rankerSpec(),
                k,
                outputFields
        );
        if (skipRanking) {
            return result;
        }
        return rankResults(query, result, reranker, language, minScore);
    }

    private void flushAndCompact(String collection, boolean skipCompact) {
        client.flush(collection);
        if (!skipCompact) {
            client.compact(collection);
        }
    }

    private RankerAndRequests getRankerAndRequests(BaseRankConfig rankerConfig,
                                                   String collection,
                                                   List<SearchRequest> searchRequests) {
        List<Double> weights;
        RankerSpec rankerSpec;
        if (rankerConfig instanceof WeightedRankConfig weighted) {
            weights = new ArrayList<>(List.of(
                    weighted.getNameDense(),
                    weighted.getContentDense(),
                    weighted.getContentSparse()
            ));
            if (GraphStoreConstants.EPISODE_COLLECTION.equals(collection)) {
                weights.set(0, 0.0d);
                weights.set(1, 0.0d);
            } else if (GraphStoreConstants.RELATION_COLLECTION.equals(collection)) {
                weights.set(0, 0.0d);
            }
            rankerSpec = new RankerSpec("weighted", normalizePositiveWeights(weights));
        } else {
            weights = rankerConfig.getIsActive().stream()
                    .map(Integer::doubleValue)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (GraphStoreConstants.EPISODE_COLLECTION.equals(collection)) {
                weights.set(0, 0.0d);
                weights.set(1, 0.0d);
            } else if (GraphStoreConstants.RELATION_COLLECTION.equals(collection)) {
                weights.set(0, 0.0d);
            }
            rankerSpec = new RankerSpec(rankerConfig.getName(), rankerConfig.getArgs().getPositional());
        }
        List<SearchRequest> activeRequests = new ArrayList<>();
        for (int i = 0; i < Math.min(searchRequests.size(), weights.size()); i++) {
            if (weights.get(i) > 0.0d) {
                activeRequests.add(searchRequests.get(i));
            }
        }
        return new RankerAndRequests(rankerSpec, activeRequests);
    }

    private List<SearchRequest> getSearchRequests(String query, List<Double> queryEmbedding, int k, String expr) {
        int limit = Math.min(k * 3, 20);
        return List.of(
                new SearchRequest("name_embedding", queryEmbedding, denseSearchParams, limit, expr),
                new SearchRequest("content_embedding", queryEmbedding, denseSearchParams, limit, expr),
                new SearchRequest("content_bm25", query, fullTextSearchParams, limit, expr)
        );
    }

    private List<String> outputFields(String collection, Map<String, Object> kwargs) {
        Object value = kwargs.get("output_fields");
        if (value instanceof Collection<?> collectionValue) {
            return collectionValue.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return fieldDef.getOrDefault(collection, List.of());
    }

    private static Map<String, List<String>> buildFieldDef() {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        List<String> base = List.of(
                "uuid",
                "created_at",
                "user_id",
                "obj_type",
                "language",
                "metadata",
                "content",
                "content_embedding"
        );
        fields.put(GraphStoreConstants.ENTITY_COLLECTION, mergeFields(
                base,
                List.of("name", "name_embedding", "relations", "episodes", "attributes")
        ));
        fields.put(GraphStoreConstants.RELATION_COLLECTION, mergeFields(
                base,
                List.of("valid_since", "valid_until", "offset_since", "offset_until", "name", "lhs", "rhs")
        ));
        fields.put(GraphStoreConstants.EPISODE_COLLECTION, mergeFields(base, List.of("valid_since", "entities")));
        return fields;
    }

    private static List<String> mergeFields(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static List<Object> normalizePositiveWeights(List<Double> weights) {
        List<Double> active = weights.stream().filter(weight -> weight > 0.0d).collect(Collectors.toList());
        double total = active.stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0d) {
            return List.of();
        }
        return active.stream().map(weight -> (Object) (weight / total)).collect(Collectors.toList());
    }

    private static Set<String> limitExpansionUuids(Set<String> newUuids,
                                                   Map<String, Map<String, Object>> newResults,
                                                   int bfsK,
                                                   boolean isSimilarity) {
        Comparator<String> comparator = Comparator.comparingDouble(uuid -> {
            Map<String, Object> row = newResults.get(uuid);
            Object distance = row == null ? null : row.get("distance");
            return distance == null ? (isSimilarity ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY)
                    : numberAsDouble(distance);
        });
        if (isSimilarity) {
            comparator = comparator.reversed();
        }
        return newUuids.stream()
                .sorted(comparator)
                .limit(bfsK)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Map<String, Object>> deepCopyRows(List<Map<String, Object>> rows) {
        if (rows == null) {
            return new ArrayList<>();
        }
        return rows.stream()
                .map(LinkedHashMap::new)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value);
    }

    private static double numberAsDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0d;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static RuntimeException graphParamInvalid(String message) {
        return ErrorHelper.buildError(StatusCode.STORE_GRAPH_PARAM_INVALID, "error_msg", message);
    }

    /**
     * Mirrors Python's {@code AnnSearchRequest} preparation in
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
     */
    record SearchRequest(String fieldName, Object data, Map<String, Object> params, int limit, String expr) {
    }

    /**
     * Mirrors Python's ranker class and argument pair in
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
     */
    record RankerSpec(String name, List<Object> args) {
    }

    /**
     * Mirrors Python's {@code _get_ranker_and_reqs} return values in
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
     */
    private record RankerAndRequests(RankerSpec rankerSpec, List<SearchRequest> requests) {
    }

    /**
     * Mirrors Python's relation content tuple used during combined rerank in
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
     */
    private record RelationContent(String content, double distance) {
    }

    /**
     * Mirrors Python's {@code pymilvus.MilvusClient} boundary in
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
     */
    interface MilvusClientAdapter extends AutoCloseable {

        List<String> listDatabases();

        void createDatabase(String database);

        void useDatabase(String database);

        void dropDatabase(String database);

        List<String> listCollections();

        boolean hasCollection(String collection);

        void createCollection(String collection, GenerateMilvusSchema.SchemaResult schema, int dimension, String metric);

        void loadCollection(String collection);

        void dropCollection(String collection);

        long rowCount(String collection);

        void flush(String collection);

        void compact(String collection);

        void write(String collection, List<Map<String, Object>> rows, boolean upsert);

        List<Map<String, Object>> query(String collection,
                                        List<?> ids,
                                        String filter,
                                        List<String> outputFields,
                                        Map<String, Object> kwargs);

        Map<String, Object> delete(String collection, String filter, Map<String, Object> kwargs);

        List<Map<String, Object>> hybridSearch(String collection,
                                               List<SearchRequest> searchRequests,
                                               RankerSpec ranker,
                                               int limit,
                                               List<String> outputFields);

        @Override
        void close();
    }

    /**
     * Mirrors Python's direct {@code MilvusClient(...)} integration in
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
     */
    static final class DefaultMilvusClientAdapter implements MilvusClientAdapter {

        private static final Gson GSON = new Gson();

        private final GraphConfig config;
        private final MilvusClientV2 client;

        DefaultMilvusClientAdapter(GraphConfig config) {
            this.config = config;
            long timeoutMs = Math.max(1L, Math.round(config.getTimeout() * 1000.0d));
            ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                    .uri(config.getUri())
                    .connectTimeoutMs(timeoutMs)
                    .rpcDeadlineMs(timeoutMs)
                    .enablePrecheck(false);
            if (!config.getToken().isBlank()) {
                builder.token(config.getToken());
            }
            if (!config.getName().isBlank()) {
                builder.dbName(config.getName());
            }
            this.client = new MilvusClientV2(builder.build());
        }

        @Override
        public List<String> listDatabases() {
            return client.listDatabases().getDatabaseNames();
        }

        @Override
        public void createDatabase(String database) {
            client.createDatabase(CreateDatabaseReq.builder().databaseName(database).build());
        }

        @Override
        public void useDatabase(String database) {
            try {
                client.useDatabase(database);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while switching Milvus database", exception);
            }
        }

        @Override
        public void dropDatabase(String database) {
            client.dropDatabase(DropDatabaseReq.builder().databaseName(database).build());
        }

        @Override
        public List<String> listCollections() {
            return client.listCollections().getCollectionNames();
        }

        @Override
        public boolean hasCollection(String collection) {
            return client.hasCollection(HasCollectionReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .build());
        }

        @Override
        public void createCollection(String collection,
                                     GenerateMilvusSchema.SchemaResult schema,
                                     int dimension,
                                     String metric) {
            CreateCollectionReq.CollectionSchema collectionSchema = toCollectionSchema(schema);
            List<IndexParam> indexes = schema.getIndexes().stream()
                    .map(DefaultMilvusClientAdapter::toIndexParam)
                    .collect(Collectors.toList());
            client.createCollection(CreateCollectionReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .primaryFieldName("uuid")
                    .idType(DataType.VarChar)
                    .vectorFieldName("content_embedding")
                    .dimension(dimension)
                    .metricType(metric)
                    .autoID(false)
                    .enableDynamicField(schema.isDynamicField())
                    .collectionSchema(collectionSchema)
                    .indexParams(indexes)
                    .build());
        }

        @Override
        public void loadCollection(String collection) {
            client.loadCollection(LoadCollectionReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .sync(true)
                    .timeout(Math.round(config.getTimeout() * 1000.0d))
                    .build());
        }

        @Override
        public void dropCollection(String collection) {
            client.dropCollection(DropCollectionReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .timeout(Math.round(config.getTimeout() * 1000.0d))
                    .build());
        }

        @Override
        public long rowCount(String collection) {
            Long count = client.getCollectionStats(GetCollectionStatsReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .build()).getNumOfEntities();
            return count == null ? 0L : count;
        }

        @Override
        public void flush(String collection) {
            client.flush(FlushReq.builder()
                    .databaseName(config.getName())
                    .collectionNames(List.of(collection))
                    .waitFlushedTimeoutMs(Math.round(config.getTimeout() * 1000.0d))
                    .build());
        }

        @Override
        public void compact(String collection) {
            client.compact(CompactReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .build());
        }

        @Override
        public void write(String collection, List<Map<String, Object>> rows, boolean upsert) {
            List<JsonObject> data = rows.stream()
                    .map(row -> GSON.toJsonTree(row).getAsJsonObject())
                    .collect(Collectors.toList());
            if (upsert) {
                client.upsert(UpsertReq.builder()
                        .databaseName(config.getName())
                        .collectionName(collection)
                        .data(data)
                        .build());
                return;
            }
            client.insert(InsertReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .data(data)
                    .build());
        }

        @Override
        public List<Map<String, Object>> query(String collection,
                                               List<?> ids,
                                               String filter,
                                               List<String> outputFields,
                                               Map<String, Object> kwargs) {
            QueryReq.QueryReqBuilder builder = QueryReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .outputFields(outputFields);
            if (ids != null && !ids.isEmpty()) {
                builder.ids(new ArrayList<>(ids));
            } else if (filter != null) {
                builder.filter(filter);
            }
            if (kwargs.get("limit") instanceof Number limit) {
                builder.limit(limit.longValue());
            }
            QueryResp response = client.query(builder.build());
            return response.getQueryResults().stream()
                    .map(QueryResp.QueryResult::getEntity)
                    .map(LinkedHashMap::new)
                    .collect(Collectors.toList());
        }

        @Override
        public Map<String, Object> delete(String collection, String filter, Map<String, Object> kwargs) {
            long count = client.delete(DeleteReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .filter(filter)
                    .build()).getDeleteCnt();
            return Map.of("delete_count", count);
        }

        @Override
        public List<Map<String, Object>> hybridSearch(String collection,
                                                      List<SearchRequest> searchRequests,
                                                      RankerSpec ranker,
                                                      int limit,
                                                      List<String> outputFields) {
            List<AnnSearchReq> annSearchReqs = searchRequests.stream()
                    .map(DefaultMilvusClientAdapter::toAnnSearchReq)
                    .collect(Collectors.toList());
            SearchResp response = client.hybridSearch(HybridSearchReq.builder()
                    .databaseName(config.getName())
                    .collectionName(collection)
                    .searchRequests(annSearchReqs)
                    .ranker(toMilvusRanker(ranker))
                    .limit(limit)
                    .outFields(outputFields)
                    .build());
            if (response.getSearchResults().isEmpty()) {
                return List.of();
            }
            return response.getSearchResults().get(0).stream()
                    .map(result -> {
                        Map<String, Object> row = result.getEntity() == null
                                ? new LinkedHashMap<>()
                                : new LinkedHashMap<>(result.getEntity());
                        row.put("distance", result.getScore());
                        return row;
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public void close() {
            client.close();
        }

        private static CreateCollectionReq.CollectionSchema toCollectionSchema(GenerateMilvusSchema.SchemaResult schema) {
            List<CreateCollectionReq.FieldSchema> fields = schema.getFields().values().stream()
                    .map(DefaultMilvusClientAdapter::toFieldSchema)
                    .collect(Collectors.toList());
            List<CreateCollectionReq.Function> functions = schema.getFunctions().stream()
                    .map(DefaultMilvusClientAdapter::toFunction)
                    .collect(Collectors.toList());
            return CreateCollectionReq.CollectionSchema.builder()
                    .fieldSchemaList(fields)
                    .enableDynamicField(schema.isDynamicField())
                    .functionList(functions)
                    .build();
        }

        private static CreateCollectionReq.FieldSchema toFieldSchema(Map<String, Object> spec) {
            CreateCollectionReq.FieldSchema.FieldSchemaBuilder builder = CreateCollectionReq.FieldSchema.builder()
                    .name(String.valueOf(spec.get("name")))
                    .dataType(dataType(String.valueOf(spec.get("type"))));
            if (spec.get("max_length") instanceof Number maxLength) {
                builder.maxLength(maxLength.intValue());
            }
            if (spec.get("dim") instanceof Number dim) {
                builder.dimension(dim.intValue());
            }
            if (Boolean.TRUE.equals(spec.get("is_primary"))) {
                builder.isPrimaryKey(true);
            }
            if (spec.get("auto_id") instanceof Boolean autoId) {
                builder.autoID(autoId);
            }
            if (spec.get("element_type") != null) {
                builder.elementType(dataType(String.valueOf(spec.get("element_type"))));
            }
            if (spec.get("max_capacity") instanceof Number maxCapacity) {
                builder.maxCapacity(maxCapacity.intValue());
            }
            if (spec.get("enable_analyzer") instanceof Boolean enableAnalyzer) {
                builder.enableAnalyzer(enableAnalyzer);
            }
            if (spec.get("enable_match") instanceof Boolean enableMatch) {
                builder.enableMatch(enableMatch);
            }
            if (spec.get("analyzer_params") instanceof Map<?, ?> analyzer) {
                builder.analyzerParams(copyStringObjectMap(analyzer));
            }
            return builder.build();
        }

        private static CreateCollectionReq.Function toFunction(Map<String, Object> spec) {
            return CreateCollectionReq.Function.builder()
                    .name(String.valueOf(spec.get("name")))
                    .functionType(FunctionType.fromName(String.valueOf(spec.get("function_type"))))
                    .inputFieldNames(toStringList(spec.get("input_field_names")))
                    .outputFieldNames(toStringList(spec.get("output_field_names")))
                    .build();
        }

        private static IndexParam toIndexParam(Map<String, Object> spec) {
            Map<String, Object> extra = new LinkedHashMap<>(spec);
            String fieldName = String.valueOf(extra.remove("field_name"));
            String indexName = String.valueOf(extra.remove("index_name"));
            String indexType = String.valueOf(extra.remove("index_type"));
            String metricType = String.valueOf(extra.remove("metric_type"));
            return IndexParam.builder()
                    .fieldName(fieldName)
                    .indexName(indexName)
                    .indexType(IndexParam.IndexType.valueOf(indexType))
                    .metricType(IndexParam.MetricType.valueOf(metricType))
                    .extraParams(extra)
                    .build();
        }

        private static AnnSearchReq toAnnSearchReq(SearchRequest request) {
            BaseVector vector;
            if (request.data() instanceof String text) {
                vector = new EmbeddedText(text);
            } else {
                @SuppressWarnings("unchecked")
                List<Double> doubles = (List<Double>) request.data();
                vector = new FloatVec(doubles.stream().map(Double::floatValue).collect(Collectors.toList()));
            }
            return AnnSearchReq.builder()
                    .vectorFieldName(request.fieldName())
                    .vectors(List.of(vector))
                    .params(toJson(request.params()))
                    .limit(request.limit())
                    .filter(request.expr())
                    .metricType(IndexParam.MetricType.valueOf(String.valueOf(request.params().get("metric_type"))))
                    .build();
        }

        private static CreateCollectionReq.Function toMilvusRanker(RankerSpec ranker) {
            if ("rrf".equals(ranker.name())) {
                int k = ranker.args().isEmpty() ? 40 : ((Number) ranker.args().get(0)).intValue();
                return RRFRanker.builder().k(k).build();
            }
            List<Float> weights = ranker.args().stream()
                    .map(value -> ((Number) value).floatValue())
                    .collect(Collectors.toList());
            return WeightedRanker.builder().weights(weights).build();
        }

        private static String toJson(Map<String, Object> value) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Cannot serialize Milvus search params", exception);
            }
        }

        private static DataType dataType(String value) {
            return switch (value) {
                case "VARCHAR" -> DataType.VarChar;
                case "FLOAT_VECTOR" -> DataType.FloatVector;
                case "SPARSE_FLOAT_VECTOR" -> DataType.SparseFloatVector;
                case "JSON" -> DataType.JSON;
                case "ARRAY" -> DataType.Array;
                case "INT64" -> DataType.Int64;
                case "INT8" -> DataType.Int8;
                default -> DataType.valueOf(value);
            };
        }
    }

    private static Map<String, Object> copyStringObjectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).collect(Collectors.toList());
        }
        return List.of();
    }
}
