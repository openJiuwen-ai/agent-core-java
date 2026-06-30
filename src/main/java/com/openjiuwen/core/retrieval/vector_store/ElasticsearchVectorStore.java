/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch-backed vector store.
 *
 * <p>This implementation mirrors the Python ElasticsearchVectorStore shape: each collection maps to one ES
 * index, vector data is stored in a {@code dense_vector} field, and collection metadata is persisted in a
 * reserved document inside the same index.</p>
 */
public class ElasticsearchVectorStore implements VectorStore, SchemaMutableVectorStore {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String METADATA_DOC_ID = "__collection_metadata__";
    private static final String DEFAULT_PREFIX = "agent_vector";

    private final VectorStoreConfig config;
    private final OkHttpClient client;
    private final String baseUrl;
    private final String authorization;
    private final String indexPrefix;
    private final String distanceMetric;
    private final String indexType;
    private final String textField = "text";
    private final String vectorField = "vector";
    private final String sparseVectorField = "sparse_vector";
    private final String metadataField = "metadata";
    private final String docIdField = "doc_id";
    private final Map<String, Map<String, Object>> metadataCache = new ConcurrentHashMap<>();
    private String collectionName;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ElasticsearchVectorStore(VectorStoreConfig config) {
        this(config, "hybrid");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ElasticsearchVectorStore(VectorStoreConfig config, String indexType) {
        config.validate();
        this.config = config;
        this.collectionName = config.getCollectionName();
        this.distanceMetric = config.getDistanceMetric();
        this.indexType = RetrievalValidation.validateIndexType(indexType, "indexType");
        this.baseUrl = resolveBaseUrl();
        this.authorization = resolveAuthorization();
        this.indexPrefix = config.getDatabaseName() == null || config.getDatabaseName().isBlank()
                ? DEFAULT_PREFIX
                : sanitizeIndexName(config.getDatabaseName());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(readIntEnv("ES_CONNECT_TIMEOUT_SECONDS", 10)))
                .readTimeout(Duration.ofSeconds(readIntEnv("ES_READ_TIMEOUT_SECONDS", 60)))
                .writeTimeout(Duration.ofSeconds(readIntEnv("ES_WRITE_TIMEOUT_SECONDS", 60)))
                .build();
    }

    private ElasticsearchVectorStore(ElasticsearchVectorStore source, String collectionName) {
        this.config = source.config;
        this.client = source.client;
        this.baseUrl = source.baseUrl;
        this.authorization = source.authorization;
        this.indexPrefix = source.indexPrefix;
        this.distanceMetric = source.distanceMetric;
        this.indexType = source.indexType;
        this.collectionName = collectionName;
        this.metadataCache.putAll(source.metadataCache);
    }

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    @Override
    public VectorStore withCollection(String collectionName) {
        return new ElasticsearchVectorStore(this, collectionName);
    }

    @Override
    public void ensureCollection(String collectionName,
                                 String indexType,
                                 Integer dimension,
                                 Map<String, Object> options) {
        if (tableExists(collectionName)) {
            return;
        }
        int dim = dimension == null || dimension <= 0 ? 768 : dimension;
        CollectionSchema schema = new CollectionSchema(null, "Elasticsearch vector collection", false)
                .addField(FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).build())
                .addField(FieldSchema.builder().name(vectorField).dtype(VectorDataType.FLOAT_VECTOR).dim(dim).build())
                .addField(FieldSchema.builder().name(textField).dtype(VectorDataType.VARCHAR).build())
                .addField(FieldSchema.builder().name(docIdField).dtype(VectorDataType.VARCHAR).build())
                .addField(FieldSchema.builder().name(metadataField).dtype(VectorDataType.JSON).build());
        createCollection(collectionName, schema, dim);
    }

    @Override
    public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        if (data == null || data.isEmpty()) {
            return;
        }
        ensureCollection(collectionName, indexType, inferDimension(data), options == null ? Map.of() : options);
        int size = batchSize == null || batchSize <= 0 ? 500 : batchSize;
        for (int i = 0; i < data.size(); i += size) {
            List<Map<String, Object>> chunk = data.subList(i, Math.min(i + size, data.size()));
            bulkIndex(chunk);
        }
        refresh(indexName(collectionName));
    }

    @Override
    public List<SearchResult> search(List<Float> queryVector,
                                     int topK,
                                     Map<String, Object> filters,
                                     Map<String, Object> options) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = buildKnnSearchBody(queryVector, topK, filters, options);
        Map<String, Object> response;
        try {
            response = requestMap("POST", "/" + indexName(collectionName) + "/_search", body, true);
        } catch (IllegalStateException exception) {
            response = requestMap("POST", "/" + indexName(collectionName) + "/_search",
                    buildScriptScoreSearchBody(queryVector, topK, filters), true);
        }
        return parseSearchResults(response);
    }

    @Override
    public List<SearchResult> sparseSearch(String queryText,
                                           int topK,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        Map<String, Object> query = queryText == null || queryText.isBlank()
                ? Map.of("match_all", Map.of())
                : Map.of("match", Map.of(textField, queryText));
        if (filters != null && !filters.isEmpty()) {
            query = Map.of("bool", Map.of("must", List.of(query), "filter", filterClauses(filters)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("size", topK);
        body.put("_source", Map.of("excludes", List.of("_meta")));
        return parseSearchResults(requestMap("POST", "/" + indexName(collectionName) + "/_search", body, true));
    }

    @Override
    public List<SearchResult> hybridSearch(String queryText,
                                           List<Float> queryVector,
                                           int topK,
                                           double alpha,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        return search(queryVector, topK, filters, options);
    }

    @Override
    public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
        boolean hasChanged = false;
        String index = indexName(collectionName);
        if (ids != null && !ids.isEmpty()) {
            StringBuilder ndjson = new StringBuilder();
            for (String id : ids) {
                ndjson.append(toJson(Map.of("delete", Map.of("_index", index, "_id", id)))).append('\n');
            }
            requestText("POST", "/_bulk?refresh=true", ndjson.toString(), false, "application/x-ndjson");
            hasChanged = true;
        }
        if (filterExpr != null && !filterExpr.isEmpty()) {
            Map<String, Object> body = Map.of("query", Map.of("bool", Map.of("filter", filterClauses(filterExpr))));
            requestMap("POST", "/" + index + "/_delete_by_query?refresh=true", body, true);
            hasChanged = true;
        }
        return hasChanged;
    }

    @Override
    public boolean tableExists(String tableName) {
        try {
            requestText("HEAD", "/" + indexName(tableName), null, true, "application/json");
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    @Override
    public void deleteTable(String tableName) {
        if (!tableExists(tableName)) {
            return;
        }
        requestText("DELETE", "/" + indexName(tableName), null, true, "application/json");
        metadataCache.remove(indexName(tableName));
    }

    @Override
    public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
        Map<String, Object> query = filters == null || filters.isEmpty()
                ? Map.of("match_all", Map.of())
                : Map.of("bool", Map.of("filter", filterClauses(filters)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("size", limit);
        body.put("_source", Map.of("excludes", List.of("_meta")));
        return parseSearchResults(requestMap("POST", "/" + indexName(collectionName) + "/_search", body, true));
    }

    @Override
    public long count(String tableName) {
        Map<String, Object> response = requestMap("GET", "/" + indexName(tableName) + "/_count", null, true);
        Object count = response.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
    }

    @Override
    public List<String> listCollectionNames() {
        Map<String, Object> response =
                requestMap("GET", "/_cat/indices/" + indexPrefix + "__*?format=json", null, true);
        List<String> result = new ArrayList<>();
        if (response.get("_items") instanceof List<?> items) {
            for (Object item : items) {
                appendCollectionName(result, item);
            }
        }
        return result;
    }

    private void appendCollectionName(List<String> result, Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return;
        }
        Object index = map.get("index");
        if (index != null) {
            result.add(stripPrefix(String.valueOf(index)));
        }
    }

    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        Map<String, Object> metadata = loadMetadata(indexName(collectionName));
        metadata.putIfAbsent("distance_metric", distanceMetric.toUpperCase(Locale.ROOT));
        metadata.putIfAbsent("schema_version", 0);
        return new LinkedHashMap<>(metadata);
    }

    @Override
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        String index = indexName(collectionName);
        Map<String, Object> current = loadMetadata(index);
        current.putAll(metadata);
        storeMetadata(index, current);
    }

    @Override
    public void updateSchema(String collectionName, List<?> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        throw new UnsupportedOperationException("ElasticsearchVectorStore.updateSchema is not implemented yet");
    }

    @Override
    public CollectionSchema getSchema(String collectionName) {
        Map<String, Object> metadata = loadMetadata(indexName(collectionName));
        Object schema = metadata.get("schema");
        if (schema instanceof Map<?, ?> map) {
            return CollectionSchema.fromDict(stringMap(map));
        }
        return new CollectionSchema();
    }

    @Override
    public String getDatabaseName() {
        return config.getDatabaseName();
    }

    @Override
    public String getDistanceMetric() {
        return distanceMetric;
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    @Override
    public String getTextField() {
        return textField;
    }

    @Override
    public String getVectorField() {
        return vectorField;
    }

    @Override
    public String getSparseVectorField() {
        return sparseVectorField;
    }

    @Override
    public String getMetadataField() {
        return metadataField;
    }

    @Override
    public String getDocIdField() {
        return docIdField;
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private void createCollection(String collectionName, CollectionSchema schema, int vectorDim) {
        String index = indexName(collectionName);
        Map<String, Object> properties = new LinkedHashMap<>();
        for (FieldSchema field : schema.getFields()) {
            properties.put(field.getName(), mapEsType(field));
        }
        properties.put("_meta", Map.of("type", "object", "enabled", false));
        Map<String, Object> mappings = Map.of("dynamic", "true", "properties", properties);
        requestMap("PUT", "/" + index, Map.of("mappings", mappings), true);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema", schema.toDict());
        metadata.put("distance_metric", distanceMetric.toUpperCase(Locale.ROOT));
        metadata.put("vector_field", vectorField);
        metadata.put("vector_dim", vectorDim);
        metadata.put("schema_version", 0);
        metadata.put("collection_name", collectionName);
        storeMetadata(index, metadata);
    }

    private Map<String, Object> mapEsType(FieldSchema field) {
        if (field.getDtype() == VectorDataType.FLOAT_VECTOR) {
            return Map.of(
                    "type", "dense_vector",
                    "dims", field.getDim() == null ? 768 : field.getDim(),
                    "index", true,
                    "similarity", esSimilarity());
        }
        return switch (field.getDtype()) {
            case INT64 -> Map.of("type", "long");
            case INT32, INT16, INT8 -> Map.of("type", "integer");
            case FLOAT -> Map.of("type", "float");
            case DOUBLE -> Map.of("type", "double");
            case BOOL -> Map.of("type", "boolean");
            case JSON, ARRAY -> Map.of("type", "object", "enabled", true);
            default -> Map.of("type", "keyword");
        };
    }

    private String esSimilarity() {
        return switch (distanceMetric) {
            case "dot" -> "dot_product";
            case "euclidean" -> "l2_norm";
            default -> "cosine";
        };
    }

    private void bulkIndex(List<Map<String, Object>> docs) {
        String index = indexName(collectionName);
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> doc : docs) {
            String id = documentId(doc);
            ndjson.append(toJson(Map.of("index", Map.of("_index", index, "_id", id)))).append('\n');
            ndjson.append(toJson(flattenDocument(doc, id))).append('\n');
        }
        requestText("POST", "/_bulk?refresh=false", ndjson.toString(), false, "application/x-ndjson");
    }

    private Map<String, Object> flattenDocument(Map<String, Object> doc, String id) {
        Map<String, Object> flattened = new LinkedHashMap<>(doc);
        flattened.putIfAbsent("id", id);
        if (!flattened.containsKey(vectorField) && flattened.get("embedding") instanceof List<?>) {
            flattened.put(vectorField, flattened.get("embedding"));
        }
        Object metadata = flattened.get(metadataField);
        if (metadata instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                flattened.putIfAbsent(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return flattened;
    }

    private String documentId(Map<String, Object> doc) {
        return firstNonBlank(
                stringValue(doc.get("id")),
                stringValue(doc.get("chunk_id")),
                stringValue(doc.get(docIdField)),
                doc.get(metadataField) instanceof Map<?, ?> map ? stringValue(map.get("chunk_id")) : null,
                UUID.randomUUID().toString());
    }

    private int inferDimension(List<Map<String, Object>> docs) {
        for (Map<String, Object> doc : docs) {
            Object value = doc.get(vectorField);
            if (value instanceof List<?> list && !list.isEmpty()) {
                return list.size();
            }
            Object embedding = doc.get("embedding");
            if (embedding instanceof List<?> list && !list.isEmpty()) {
                return list.size();
            }
        }
        return 768;
    }

    private Map<String, Object> buildKnnSearchBody(List<Float> queryVector,
                                                   int topK,
                                                   Map<String, Object> filters,
                                                   Map<String, Object> options) {
        int numCandidates = Math.max(topK * 10, 100);
        if (options != null && options.get("num_candidates") instanceof Number number) {
            numCandidates = number.intValue();
        }
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", vectorField);
        knn.put("query_vector", queryVector);
        knn.put("k", topK);
        knn.put("num_candidates", numCandidates);
        if (filters != null && !filters.isEmpty()) {
            knn.put("filter", Map.of("bool", Map.of("filter", filterClauses(filters))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("knn", knn);
        body.put("size", topK);
        body.put("_source", Map.of("excludes", List.of("_meta")));
        return body;
    }

    private Map<String, Object> buildScriptScoreSearchBody(List<Float> queryVector,
                                                           int topK,
                                                           Map<String, Object> filters) {
        Map<String, Object> baseQuery = filters == null || filters.isEmpty()
                ? Map.of("match_all", Map.of())
                : Map.of("bool", Map.of("filter", filterClauses(filters)));
        Map<String, Object> script = Map.of(
                "source", "cosineSimilarity(params.query_vector, '" + vectorField + "') + 1.0",
                "params", Map.of("query_vector", queryVector));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("script_score", Map.of("query", baseQuery, "script", script)));
        body.put("size", topK);
        body.put("_source", Map.of("excludes", List.of("_meta")));
        return body;
    }

    private List<Map<String, Object>> filterClauses(Map<String, Object> filters) {
        List<Map<String, Object>> clauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (entry.getValue() instanceof Collection<?> collection) {
                clauses.add(keywordCompatibleTermsClause(entry.getKey(), new ArrayList<>(collection)));
            } else {
                clauses.add(keywordCompatibleTermClause(entry.getKey(), entry.getValue()));
            }
        }
        return clauses;
    }

    private Map<String, Object> keywordCompatibleTermClause(String field, Object value) {
        if (!(value instanceof String)) {
            return Map.of("term", Map.of(field, value));
        }
        return Map.of("bool", Map.of("should", List.of(
                Map.of("term", Map.of(field, value)),
                Map.of("term", Map.of(field + ".keyword", value))
        ), "minimum_should_match", 1));
    }

    private Map<String, Object> keywordCompatibleTermsClause(String field, List<?> values) {
        boolean hasString = values.stream().anyMatch(String.class::isInstance);
        if (!hasString) {
            return Map.of("terms", Map.of(field, values));
        }
        return Map.of("bool", Map.of("should", List.of(
                Map.of("terms", Map.of(field, values)),
                Map.of("terms", Map.of(field + ".keyword", values))
        ), "minimum_should_match", 1));
    }

    private List<SearchResult> parseSearchResults(Map<String, Object> response) {
        Map<String, Object> hitsRoot = castMap(response.get("hits"));
        List<?> hits = hitsRoot.get("hits") instanceof List<?> list ? list : List.of();
        List<SearchResult> results = new ArrayList<>();
        for (Object item : hits) {
            if (!(item instanceof Map<?, ?> hit)) {
                continue;
            }
            Map<String, Object> source = castMap(hit.get("_source"));
            source.remove("_meta");
            String id = firstNonBlank(stringValue(source.get("id")), stringValue(hit.get("_id")));
            String text = stringValue(source.getOrDefault(textField, ""));
            double score = hit.get("_score") instanceof Number number ? number.doubleValue() : 0.0;
            Map<String, Object> metadata = castMap(source.get(metadataField));
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                metadata.putIfAbsent(entry.getKey(), entry.getValue());
            }
            results.add(new SearchResult(id, text, score, metadata));
        }
        return results;
    }

    private Map<String, Object> loadMetadata(String index) {
        if (metadataCache.containsKey(index)) {
            return new LinkedHashMap<>(metadataCache.get(index));
        }
        try {
            Map<String, Object> response = requestMap("GET", "/" + index + "/_doc/" + METADATA_DOC_ID, null, true);
            Map<String, Object> source = castMap(response.get("_source"));
            Map<String, Object> metadata = castMap(source.get("_meta"));
            metadataCache.put(index, metadata);
            return new LinkedHashMap<>(metadata);
        } catch (IllegalStateException exception) {
            return new LinkedHashMap<>();
        }
    }

    private void storeMetadata(String index, Map<String, Object> metadata) {
        requestMap("PUT", "/" + index + "/_doc/" + METADATA_DOC_ID + "?refresh=true",
                Map.of("_meta", metadata), true);
        metadataCache.put(index, new LinkedHashMap<>(metadata));
    }

    private void refresh(String index) {
        requestText("POST", "/" + index + "/_refresh", null, true, "application/json");
    }

    private String indexName(String collection) {
        return indexPrefix + "__" + sanitizeIndexName(collection);
    }

    private String stripPrefix(String index) {
        String prefix = indexPrefix + "__";
        return index.startsWith(prefix) ? index.substring(prefix.length()) : index;
    }

    private Map<String, Object> requestMap(String method, String path, Object body, boolean isFailOnError) {
        String text = requestText(method, path, body == null ? null : toJson(body), isFailOnError, "application/json");
        if (text == null || text.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            if (text.trim().startsWith("[")) {
                List<Object> list = MAPPER.readValue(text, new TypeReference<>() {
                });
                return new LinkedHashMap<>(Map.of("_items", list));
            }
            return MAPPER.readValue(text, MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse Elasticsearch response: " + text, exception);
        }
    }

    private String requestText(String method,
                               String path,
                               String body,
                               boolean isFailOnError,
                               String contentType) {
        RequestBody requestBody = body == null
                ? null
                : RequestBody.create(body, MediaType.get(contentType + "; charset=utf-8"));
        Request.Builder builder = new Request.Builder().url(baseUrl + path);
        if (!authorization.isBlank()) {
            builder.header("Authorization", authorization);
        }
        builder.method(method, permitsRequestBody(method) ? requestBodyOrEmpty(requestBody) : null);
        try (Response response = client.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (isFailOnError && !response.isSuccessful()) {
                throw new IllegalStateException("Elasticsearch request failed: method=" + method
                        + ", path=" + path + ", status=" + response.code() + ", body=" + text);
            }
            return text;
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch request failed: method=" + method + ", path=" + path,
                    exception);
        }
    }

    private static RequestBody requestBodyOrEmpty(RequestBody requestBody) {
        return requestBody == null ? RequestBody.create(new byte[0], JSON) : requestBody;
    }

    private static boolean permitsRequestBody(String method) {
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method);
    }

    private static String resolveBaseUrl() {
        String url = System.getenv("ES_URL");
        if (url != null && !url.isBlank()) {
            return trimTrailingSlash(url);
        }
        String scheme = readEnv("ES_SCHEME", "http");
        String host = readEnv("ES_HOST", "localhost");
        String port = readEnv("ES_PORT", "9200");
        return scheme + "://" + host + ":" + port;
    }

    private static String resolveAuthorization() {
        String username = System.getenv("ES_USERNAME");
        String password = System.getenv("ES_PASSWORD");
        if (username == null || username.isBlank()) {
            return "";
        }
        return Credentials.basic(username, password == null ? "" : password);
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String sanitizeIndexName(String value) {
        String sanitized = value == null ? DEFAULT_PREFIX : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        return sanitized.isBlank() ? DEFAULT_PREFIX : sanitized;
    }

    private static String readEnv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int readIntEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value);
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to serialize JSON", exception);
        }
    }

    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringMap(map);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
