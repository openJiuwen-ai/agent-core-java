/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.db_connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.request.DropIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code MilvusConnector} in
 * {@code openjiuwen/extensions/context_evolver/core/db_connector/milvus_connector.py}.
 */
public class MilvusConnector {

    static final String FIELD_ID = "id";
    static final String FIELD_NAMESPACE = "namespace";
    static final String FIELD_CONTENT = "content";
    static final String FIELD_EMBEDDING = "embedding";
    static final String FIELD_METADATA = "metadata";

    static final int ID_MAX_LEN = 256;
    static final int NAMESPACE_MAX_LEN = 256;
    static final int CONTENT_MAX_LEN = 65_535;

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final String host;
    private final int port;
    private final String collectionName;
    private Integer dim;
    private final String alias;
    private final String metricType;
    private MilvusClientV2 client;
    private MilvusCollectionAdapter collection;

    public MilvusConnector() {
        this("localhost", 19530, "vector_nodes", null, "default", "COSINE");
    }

    public MilvusConnector(String host, int port, String collectionName, Integer dim, String alias, String metricType) {
        this(host, port, collectionName, dim, alias, metricType, true);
    }

    MilvusConnector(String host, int port, String collectionName, Integer dim, String alias, String metricType, boolean autoConnect) {
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
        this.dim = dim;
        this.alias = alias;
        this.metricType = metricType == null ? "COSINE" : metricType.toUpperCase();
        if (autoConnect) {
            connect();
            if (dim != null) {
                initCollection(dim);
            }
        }
        LOGGER.info(
                "MilvusConnector initialised (host=%s, port=%s, collection=%s, dim=%s)",
                host,
                port,
                collectionName,
                dim
        );
    }

    private void connect() {
        try {
            ConnectConfig config = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .build();
            client = new MilvusClientV2(config);
            LOGGER.info("Connected to Milvus at %s:%s", host, port);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to connect to Milvus at %s:%s: %s", host, port, exception.getMessage());
            throw exception;
        }
    }

    private void initCollection(int dimension) {
        if (collection != null) {
            return;
        }
        dim = dimension;
        if (hasRemoteCollection()) {
            collection = new SdkMilvusCollectionAdapter(client, collectionName);
            LOGGER.info("Reusing existing Milvus collection '%s'", collectionName);
        } else {
            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .enableDynamicField(false)
                    .build();
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_ID)
                    .dataType(DataType.VarChar)
                    .maxLength(ID_MAX_LEN)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_NAMESPACE)
                    .dataType(DataType.VarChar)
                    .maxLength(NAMESPACE_MAX_LEN)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_CONTENT)
                    .dataType(DataType.VarChar)
                    .maxLength(CONTENT_MAX_LEN)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_EMBEDDING)
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_METADATA)
                    .dataType(DataType.JSON)
                    .build());
            client.createCollection(CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .description("VectorNode storage for context evolver")
                    .collectionSchema(schema)
                    .build());
            collection = new SdkMilvusCollectionAdapter(client, collectionName);
            LOGGER.info("Created Milvus collection '%s' (dim=%d)", collectionName, dimension);
        }
        ensureIndex();
        collection.load();
    }

    private void ensureIndex() {
        if (collection == null || collection.hasIndex()) {
            return;
        }
        collection.createIndex("HNSW", metricType, Map.of("M", 16, "efConstruction", 64));
        LOGGER.info("Created default HNSW index on '%s' (metric=%s)", collectionName, metricType);
    }

    private MilvusCollectionAdapter getCollection(Integer requestedDim) {
        if (collection == null) {
            if (requestedDim == null) {
                if (!hasRemoteCollection()) {
                    throw new IllegalArgumentException(
                            "Vector dimension unknown. Provide `dim` in the constructor "
                                    + "or call save_to_db() with non-empty embedded data first."
                    );
                }
                collection = new SdkMilvusCollectionAdapter(client, collectionName);
                dim = collection.getDimension();
                collection.load();
                LOGGER.info("Attached to existing Milvus collection '%s' (dim=%s)", collectionName, dim);
            } else {
                initCollection(requestedDim);
            }
        }
        return collection;
    }

    public static String truncate(String text, int maxBytes) {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (encoded.length <= maxBytes) {
            return text;
        }
        return new String(encoded, 0, maxBytes, StandardCharsets.UTF_8).replaceAll("\uFFFD+$", "");
    }

    public static String idsExpr(List<String> ids) {
        String joined = ids.stream().map(id -> "\"" + id + "\"").reduce((left, right) -> left + ", " + right).orElse("");
        return FIELD_ID + " in [" + joined + "]";
    }

    public void setCollection(MilvusCollectionAdapter injectedCollection) {
        this.collection = injectedCollection;
    }

    public void saveToDb(String namespace, Map<String, Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            LOGGER.info("save_to_db: empty data, nothing to do");
            return;
        }

        Integer detectedDim = null;
        for (Map<String, Object> nodeData : data.values()) {
            List<Double> embedding = toDoubleList(nodeData.get("embedding"));
            if (embedding != null && !embedding.isEmpty()) {
                detectedDim = embedding.size();
                break;
            }
        }

        MilvusCollectionAdapter currentCollection = getCollection(detectedDim);
        List<String> ids = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int skipped = 0;

        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            Map<String, Object> nodeData = entry.getValue();
            List<Double> embedding = toDoubleList(nodeData.get("embedding"));
            if (embedding == null || embedding.isEmpty()) {
                skipped += 1;
                continue;
            }

            String nodeId = truncate(entry.getKey(), ID_MAX_LEN);
            ids.add(nodeId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(FIELD_ID, nodeId);
            row.put(FIELD_NAMESPACE, truncate(namespace, NAMESPACE_MAX_LEN));
            row.put(FIELD_CONTENT, truncate(String.valueOf(nodeData.getOrDefault("content", "")), CONTENT_MAX_LEN));
            row.put(FIELD_EMBEDDING, embedding);
            row.put(FIELD_METADATA, toMetadataMap(nodeData.get("metadata")));
            rows.add(row);
        }

        if (skipped > 0) {
            LOGGER.warning("save_to_db: skipped %d node(s) without embeddings in namespace '%s'", skipped, namespace);
        }
        if (ids.isEmpty()) {
            LOGGER.warning("save_to_db: no embeddable nodes found - nothing saved");
            return;
        }

        try {
            currentCollection.delete(idsExpr(ids));
            currentCollection.insert(rows);
            currentCollection.flush();
            LOGGER.info("Saved %d nodes to namespace '%s' (collection='%s')", ids.size(), namespace, collectionName);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to save data to namespace '%s': %s", namespace, exception.getMessage());
            throw exception;
        }
    }

    public Map<String, Map<String, Object>> loadFromDb(String namespace) {
        try {
            MilvusCollectionAdapter currentCollection = getCollection(null);
            List<Map<String, Object>> results = currentCollection.query(
                    FIELD_NAMESPACE + " == \"" + namespace + "\"",
                    List.of(FIELD_ID, FIELD_NAMESPACE, FIELD_CONTENT, FIELD_EMBEDDING, FIELD_METADATA),
                    null
            );
            Map<String, Map<String, Object>> data = new LinkedHashMap<>();
            for (Map<String, Object> hit : results) {
                String nodeId = String.valueOf(hit.getOrDefault(FIELD_ID, ""));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", nodeId);
                row.put("content", String.valueOf(hit.getOrDefault(FIELD_CONTENT, "")));
                row.put("embedding", toDoubleList(hit.get(FIELD_EMBEDDING)));
                row.put("metadata", toMetadataMap(hit.get(FIELD_METADATA)));
                data.put(nodeId, row);
            }
            LOGGER.info("Loaded %d nodes from namespace '%s'", data.size(), namespace);
            return data;
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to load namespace '%s': %s", namespace, exception.getMessage());
            throw exception;
        }
    }

    public List<Map<String, Object>> search(String namespace, List<Double> embedding, int topK, String metric) {
        String requestedMetric = normalizeMetric(metric);
        String effectiveMetric = metricType.toUpperCase();
        if (!requestedMetric.equals(effectiveMetric)) {
            LOGGER.warning(
                    "search() called with metric=%s but collection index uses %s; falling back to index metric %s.",
                    requestedMetric,
                    effectiveMetric,
                    effectiveMetric
            );
        }

        try {
            MilvusCollectionAdapter currentCollection = getCollection(null);
            List<MilvusSearchHit> rawHits = currentCollection.search(
                    embedding,
                    FIELD_EMBEDDING,
                    effectiveMetric,
                    Map.of("ef", Math.max(topK * 2, 64)),
                    topK,
                    FIELD_NAMESPACE + " == \"" + namespace + "\"",
                    List.of(FIELD_ID, FIELD_CONTENT, FIELD_METADATA)
            );
            List<Map<String, Object>> results = new ArrayList<>();
            for (MilvusSearchHit hit : rawHits) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", hit.id());
                row.put("content", String.valueOf(hit.entity().getOrDefault(FIELD_CONTENT, "")));
                row.put("embedding", null);
                row.put("metadata", toMetadataMap(hit.entity().get(FIELD_METADATA)));
                row.put("score", hit.score());
                results.add(row);
            }
            LOGGER.debug("search returned %d hits from namespace '%s'", results.size(), namespace);
            return results;
        } catch (RuntimeException exception) {
            LOGGER.error("Search failed in namespace '%s': %s", namespace, exception.getMessage());
            throw exception;
        }
    }

    public List<Map<String, Object>> search(String namespace, List<Double> embedding, int topK) {
        return search(namespace, embedding, topK, "cosine");
    }

    public boolean exists(String namespace) {
        try {
            if (collection == null && !hasRemoteCollection()) {
                return false;
            }
            MilvusCollectionAdapter currentCollection = getCollection(null);
            List<Map<String, Object>> results = currentCollection.query(
                    FIELD_NAMESPACE + " == \"" + namespace + "\"",
                    List.of(FIELD_ID),
                    1
            );
            return !results.isEmpty();
        } catch (RuntimeException exception) {
            LOGGER.error("exists() failed for namespace '%s': %s", namespace, exception.getMessage());
            return false;
        }
    }

    public boolean delete(String namespace) {
        try {
            MilvusCollectionAdapter currentCollection = getCollection(null);
            List<Map<String, Object>> results = currentCollection.query(
                    FIELD_NAMESPACE + " == \"" + namespace + "\"",
                    List.of(FIELD_ID),
                    null
            );
            if (results.isEmpty()) {
                LOGGER.info("delete(): namespace '%s' was already empty", namespace);
                return false;
            }
            List<String> ids = results.stream()
                    .map(row -> String.valueOf(row.get(FIELD_ID)))
                    .toList();
            currentCollection.delete(idsExpr(ids));
            currentCollection.flush();
            LOGGER.info("Deleted %d nodes from namespace '%s'", ids.size(), namespace);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("delete() failed for namespace '%s': %s", namespace, exception.getMessage());
            throw exception;
        }
    }

    public boolean deleteNodes(String namespace, List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return true;
        }
        try {
            MilvusCollectionAdapter currentCollection = getCollection(null);
            currentCollection.delete(idsExpr(nodeIds));
            currentCollection.flush();
            LOGGER.info("Deleted %d nodes from namespace '%s'", nodeIds.size(), namespace);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("delete_nodes() failed for namespace '%s': %s", namespace, exception.getMessage());
            throw exception;
        }
    }

    public void createIndex(String indexType, String metricType, int m, int efConstruction, int nlist) {
        MilvusCollectionAdapter currentCollection = getCollection(null);
        if (currentCollection.hasIndex()) {
            currentCollection.dropIndex();
            LOGGER.info("Dropped existing index on '%s'", collectionName);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        if ("HNSW".equals(indexType)) {
            params.put("M", m);
            params.put("efConstruction", efConstruction);
        } else if (indexType.startsWith("IVF")) {
            params.put("nlist", nlist);
        }
        currentCollection.createIndex(indexType, metricType, params);
        currentCollection.load();
        LOGGER.info("Created %s index on '%s' (metric=%s)", indexType, collectionName, metricType);
    }

    public void createIndex() {
        createIndex("HNSW", metricType, 16, 64, 128);
    }

    public List<String> listNamespaces() {
        try {
            MilvusCollectionAdapter currentCollection = getCollection(null);
            List<Map<String, Object>> results = currentCollection.query(
                    FIELD_ID + " != \"\"",
                    List.of(FIELD_NAMESPACE),
                    null
            );
            return new ArrayList<>(new LinkedHashSet<>(results.stream()
                    .map(row -> String.valueOf(row.get(FIELD_NAMESPACE)))
                    .toList()));
        } catch (RuntimeException exception) {
            LOGGER.error("list_namespaces() failed: %s", exception.getMessage());
            return List.of();
        }
    }

    public int count(String namespace) {
        try {
            MilvusCollectionAdapter currentCollection = getCollection(null);
            if (namespace != null) {
                return currentCollection.query(
                        FIELD_NAMESPACE + " == \"" + namespace + "\"",
                        List.of(FIELD_ID),
                        null
                ).size();
            }
            return (int) currentCollection.numEntities();
        } catch (RuntimeException exception) {
            LOGGER.error("count() failed: %s", exception.getMessage());
            return 0;
        }
    }

    public int count() {
        return count(null);
    }

    public void flush() {
        if (collection != null) {
            collection.flush();
            LOGGER.debug("Flushed Milvus collection '%s'", collectionName);
        }
    }

    public void close() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception exception) {
            LOGGER.warning("Error disconnecting from Milvus: %s", exception.getMessage());
        }
        collection = null;
        client = null;
        LOGGER.info("MilvusConnector closed (host=%s, port=%s)", host, port);
    }

    private boolean hasRemoteCollection() {
        if (client == null) {
            return false;
        }
        return client.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build());
    }

    private static String normalizeMetric(String metric) {
        if (metric == null) {
            return "COSINE";
        }
        return switch (metric.toLowerCase()) {
            case "l2" -> "L2";
            case "ip", "inner_product" -> "IP";
            default -> "COSINE";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMetadataMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private static List<Double> toDoubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Double> converted = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Number number) {
                converted.add(number.doubleValue());
            }
        }
        return converted;
    }

    interface MilvusCollectionAdapter {
        void insert(List<Map<String, Object>> rows);

        void delete(String expr);

        void flush();

        List<Map<String, Object>> query(String expr, List<String> outputFields, Integer limit);

        List<MilvusSearchHit> search(
                List<Double> vector,
                String annsField,
                String metricType,
                Map<String, Object> searchParams,
                int limit,
                String expr,
                List<String> outputFields
        );

        boolean hasIndex();

        void createIndex(String indexType, String metricType, Map<String, Object> params);

        void dropIndex();

        void load();

        long numEntities();

        Integer getDimension();
    }

    record MilvusSearchHit(String id, Map<String, Object> entity, double score) {
    }

    private static final class SdkMilvusCollectionAdapter implements MilvusCollectionAdapter {

        private final MilvusClientV2 client;
        private final String collectionName;

        private SdkMilvusCollectionAdapter(MilvusClientV2 client, String collectionName) {
            this.client = Objects.requireNonNull(client);
            this.collectionName = collectionName;
        }

        @Override
        public void insert(List<Map<String, Object>> rows) {
            List<JsonObject> payload = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                JsonObject json = new JsonObject();
                json.addProperty(FIELD_ID, String.valueOf(row.get(FIELD_ID)));
                json.addProperty(FIELD_NAMESPACE, String.valueOf(row.get(FIELD_NAMESPACE)));
                json.addProperty(FIELD_CONTENT, String.valueOf(row.get(FIELD_CONTENT)));
                JsonArray embedding = new JsonArray();
                for (Double value : toDoubleList(row.get(FIELD_EMBEDDING))) {
                    embedding.add(value);
                }
                json.add(FIELD_EMBEDDING, embedding);
                json.addProperty(FIELD_METADATA, new com.google.gson.Gson().toJson(toMetadataMap(row.get(FIELD_METADATA))));
                payload.add(json);
            }
            client.insert(InsertReq.builder().collectionName(collectionName).data(payload).build());
        }

        @Override
        public void delete(String expr) {
            client.delete(DeleteReq.builder().collectionName(collectionName).filter(expr).build());
        }

        @Override
        public void flush() {
            client.flush(FlushReq.builder().collectionNames(List.of(collectionName)).build());
        }

        @Override
        public List<Map<String, Object>> query(String expr, List<String> outputFields, Integer limit) {
            QueryReq.QueryReqBuilder builder = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(expr)
                    .outputFields(outputFields);
            if (limit != null) {
                builder.limit(limit);
            }
            QueryResp response = client.query(builder.build());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (QueryResp.QueryResult result : response.getQueryResults()) {
                rows.add(new LinkedHashMap<>(result.getEntity()));
            }
            return rows;
        }

        @Override
        public List<MilvusSearchHit> search(
                List<Double> vector,
                String annsField,
                String metricType,
                Map<String, Object> searchParams,
                int limit,
                String expr,
                List<String> outputFields
        ) {
            List<Float> values = vector.stream().map(Double::floatValue).toList();
            SearchResp response = client.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(annsField)
                    .metricType(IndexParam.MetricType.valueOf(metricType))
                    .topK(limit)
                    .filter(expr)
                    .outputFields(outputFields)
                    .searchParams(searchParams)
                    .data(List.of(new FloatVec(values)))
                    .build());
            List<MilvusSearchHit> hits = new ArrayList<>();
            for (List<SearchResp.SearchResult> batch : response.getSearchResults()) {
                for (SearchResp.SearchResult hit : batch) {
                    hits.add(new MilvusSearchHit(hit.getPrimaryKey(), new LinkedHashMap<>(hit.getEntity()), hit.getScore()));
                }
            }
            return hits;
        }

        @Override
        public boolean hasIndex() {
            return !client.describeIndex(DescribeIndexReq.builder().collectionName(collectionName).build())
                    .getIndexDescriptions()
                    .isEmpty();
        }

        @Override
        public void createIndex(String indexType, String metricType, Map<String, Object> params) {
            client.createIndex(CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(List.of(
                            IndexParam.builder()
                                    .fieldName(FIELD_EMBEDDING)
                                    .indexName(FIELD_EMBEDDING + "_idx")
                                    .indexType(IndexParam.IndexType.valueOf(indexType))
                                    .metricType(IndexParam.MetricType.valueOf(metricType))
                                    .extraParams(params)
                                    .build()
                    ))
                    .build());
        }

        @Override
        public void dropIndex() {
            client.dropIndex(DropIndexReq.builder().collectionName(collectionName).indexName(FIELD_EMBEDDING + "_idx").build());
        }

        @Override
        public void load() {
            client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        }

        @Override
        public long numEntities() {
            return query(FIELD_ID + " != \"\"", List.of(FIELD_ID), null).size();
        }

        @Override
        public Integer getDimension() {
            CreateCollectionReq.CollectionSchema schema = client.describeCollection(
                    DescribeCollectionReq.builder().collectionName(collectionName).build()
            ).getCollectionSchema();
            CreateCollectionReq.FieldSchema field = schema.getField(FIELD_EMBEDDING);
            return field == null ? null : field.getDimension();
        }
    }
}
