/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.db_connector;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DropIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.db_connector.milvus_connector.MilvusConnector}.
 * 
 * Connector for saving and loading VectorNode data via Milvus.
 * 
 * The embedding field uses Milvus FLOAT_VECTOR type with an HNSW index by default,
 * enabling fast approximate nearest-neighbour search.
 * 
 * Note: Milvus requires every inserted vector to be non-null. Nodes whose
 * embedding field is null are skipped during saveToDb with a warning.
 */
public class MilvusConnector {
    
    private static final Logger log = LoggerFactory.getLogger(MilvusConnector.class);
    
    // Schema / size constants
    private static final String FIELD_ID = "id";
    private static final String FIELD_NS = "namespace";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_METADATA = "metadata";
    
    private static final int ID_MAX_LEN = 256;
    private static final int NS_MAX_LEN = 256;
    // Milvus VARCHAR has a hard ceiling of 65 535 bytes; content may be long text,
    // so we truncate at this limit rather than raise an error.
    private static final int CONTENT_MAX_LEN = 65535;
    
    // Instance fields
    private final String host;
    private final int port;
    private final String collectionName;
    private Integer dim;
    private final String alias;
    private final String metricType;
    
    private MilvusClientV2 client;
    private boolean collectionInitialized = false;
    
    /**
     * Create a MilvusConnector with default settings.
     */
    public MilvusConnector() {
        this("localhost", 19530, "vector_nodes", null, "default", "COSINE");
    }
    
    /**
     * Create a MilvusConnector.
     *
     * @param host            Milvus server hostname (default: "localhost")
     * @param port            Milvus gRPC port (default: 19530)
     * @param collectionName  Milvus collection to use (default: "vector_nodes")
     * @param dim             Embedding dimension. Auto-detected from the first saveToDb call if not provided
     * @param alias           Connection alias (default: "default")
     * @param metricType      Index and search metric: "COSINE" (default), "L2", or "IP"
     */
    public MilvusConnector(
        String host,
        int port,
        String collectionName,
        Integer dim,
        String alias,
        String metricType
    ) {
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
        this.dim = dim;
        this.alias = alias;
        this.metricType = metricType;
        
        connect();
        
        if (dim != null) {
            initCollection(dim);
        }
        
        log.info("MilvusConnector initialised (host={}, port={}, collection={}, dim={})", 
            host, port, collectionName, dim);
    }
    
    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------
    
    private void connect() {
        try {
            String connectUri = String.format("http://%s:%d", host, port);
            ConnectConfig config = ConnectConfig.builder()
                .uri(connectUri)
                .build();
            this.client = new MilvusClientV2(config);
            log.info("Connected to Milvus at {}:{}", host, port);
        } catch (Exception e) {
            log.error("Failed to connect to Milvus at {}:{}", host, port, e.getMessage());
            throw new RuntimeException("Failed to connect to Milvus", e);
        }
    }
    
    private void initCollection(int dim) {
        if (collectionInitialized) {
            return;
        }
        
        this.dim = dim;
        
        try {
            // Check if collection exists
            HasCollectionReq hasReq = HasCollectionReq.builder()
                .collectionName(collectionName)
                .build();
            boolean hasCollection = client.hasCollection(hasReq);
            
            if (hasCollection) {
                LoadCollectionReq loadReq = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
                client.loadCollection(loadReq);
                log.info("Reusing existing Milvus collection '{}'", collectionName);
            } else {
                // Create collection with schema
                CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .build();
                
                // Add fields
                schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_ID)
                    .dataType(DataType.VarChar)
                    .maxLength(ID_MAX_LEN)
                    .isPrimaryKey(true)
                    .autoID(false)
                    .build());
                
                schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_NS)
                    .dataType(DataType.VarChar)
                    .maxLength(NS_MAX_LEN)
                    .build());
                
                schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_CONTENT)
                    .dataType(DataType.VarChar)
                    .maxLength(CONTENT_MAX_LEN)
                    .build());
                
                schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_EMBEDDING)
                    .dataType(DataType.FloatVector)
                    .dimension(dim)
                    .build());
                
                schema.addField(AddFieldReq.builder()
                    .fieldName(FIELD_METADATA)
                    .dataType(DataType.JSON)
                    .build());
                
                CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .build();
                client.createCollection(createReq);
                
                // Create index on embedding field
                IndexParam indexParam = IndexParam.builder()
                    .fieldName(FIELD_EMBEDDING)
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.valueOf(metricType))
                    .extraParams(Map.of("M", "16", "efConstruction", "64"))
                    .build();
                
                CreateIndexReq indexReq = CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(List.of(indexParam))
                    .build();
                client.createIndex(indexReq);
                
                // Load collection into memory
                LoadCollectionReq loadReq = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
                client.loadCollection(loadReq);
                
                log.info("Created new Milvus collection '{}' with HNSW index (metric={})", 
                    collectionName, metricType);
            }
            
            collectionInitialized = true;
        } catch (Exception e) {
            log.error("Failed to initialize collection '{}': {}", collectionName, e.getMessage());
            throw new RuntimeException("Failed to initialize collection", e);
        }
    }
    
    private void ensureCollection() {
        if (!collectionInitialized) {
            throw new IllegalStateException("Collection not initialized. Call initCollection(dim) first or provide dim at construction.");
        }
    }
    
    /**
     * Truncate a string to maxBytes bytes, handling UTF-8 encoding.
     */
    public static String truncate(String text, int maxBytes) {
        if (text == null) {
            return "";
        }
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        // Truncate bytes and decode, handling potential partial characters
        return new String(Arrays.copyOf(bytes, maxBytes), java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * Build a Milvus id in [...] expression from a list of IDs.
     */
    public static String idsExpr(List<String> ids) {
        String quoted = ids.stream()
            .map(id -> "\"" + id + "\"")
            .collect(Collectors.joining(", "));
        return FIELD_ID + " in [" + quoted + "]";
    }
    
    // ------------------------------------------------------------------
    // Public API – mirrors JSONFileConnector
    // ------------------------------------------------------------------
    
    /**
     * Upsert all nodes in data under namespace.
     * 
     * Nodes without an embedding are skipped because Milvus requires non-null float vectors.
     * Existing rows with the same id are replaced (delete-then-insert).
     *
     * @param namespace Logical partition key
     * @param data      Mapping of nodeId to serialized VectorNode dict
     */
    public void saveToDb(String namespace, Map<String, Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            log.info("saveToDb: empty data, nothing to do");
            return;
        }
        
        // Auto-detect embedding dimension from the first embedded node
        Integer detectedDim = null;
        for (Map<String, Object> nodeData : data.values()) {
            Object embObj = nodeData.get("embedding");
            if (embObj instanceof List<?> embList && !embList.isEmpty()) {
                detectedDim = embList.size();
                break;
            }
        }
        
        if (detectedDim == null) {
            throw new IllegalArgumentException("Cannot determine embedding dimension - no embeddings in data");
        }
        
        // Initialize collection if needed
        if (!collectionInitialized) {
            initCollection(detectedDim);
        } else if (dim != null && detectedDim != dim) {
            throw new IllegalArgumentException("Embedding dimension mismatch: expected " + dim + ", got " + detectedDim);
        }
        
        List<String> ids = new ArrayList<>();
        List<String> namespaces = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        int skipped = 0;
        
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            String nodeId = entry.getKey();
            Map<String, Object> nodeData = entry.getValue();
            
            Object embObj = nodeData.get("embedding");
            if (!(embObj instanceof List<?> embList) || embList.isEmpty()) {
                skipped++;
                continue;
            }
            
            // Convert embedding to List<Float>
            List<Float> embedding = new ArrayList<>();
            for (Object obj : embList) {
                if (obj instanceof Number num) {
                    embedding.add(num.floatValue());
                }
            }
            
            ids.add(truncate(nodeId, ID_MAX_LEN));
            namespaces.add(truncate(namespace, NS_MAX_LEN));
            contents.add(truncate((String) nodeData.getOrDefault("content", ""), CONTENT_MAX_LEN));
            embeddings.add(embedding);
            metadatas.add((Map<String, Object>) nodeData.getOrDefault("metadata", new HashMap<>()));
        }
        
        if (skipped > 0) {
            log.warn("saveToDb: skipped {} nodes with null/empty embedding", skipped);
        }
        
        if (ids.isEmpty()) {
            log.info("saveToDb: all nodes had null/empty embeddings, nothing inserted");
            return;
        }
        
        try {
            // Delete existing rows with same IDs first (upsert behavior)
            DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(idsExpr(ids))
                .build();
            client.delete(deleteReq);
            
            // Insert new rows
            List<com.google.gson.JsonObject> rows = new ArrayList<>();
            com.google.gson.Gson gson = new com.google.gson.Gson();
            for (int i = 0; i < ids.size(); i++) {
                com.google.gson.JsonObject row = new com.google.gson.JsonObject();
                row.addProperty(FIELD_ID, ids.get(i));
                row.addProperty(FIELD_NS, namespaces.get(i));
                row.addProperty(FIELD_CONTENT, contents.get(i));
                row.add(FIELD_EMBEDDING, gson.toJsonTree(embeddings.get(i)).getAsJsonArray());
                row.add(FIELD_METADATA, gson.toJsonTree(metadatas.get(i)).getAsJsonObject());
                rows.add(row);
            }
            
            InsertReq insertReq = InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build();
            InsertResp response = client.insert(insertReq);
            
            log.info("Upserted {} nodes into namespace '{}' ({} new, {} skipped)", 
                response.getInsertCnt(), namespace, ids.size(), skipped);
        } catch (Exception e) {
            log.error("saveToDb failed for namespace '{}': {}", namespace, e.getMessage());
            throw new RuntimeException("Failed to save to Milvus", e);
        }
    }
    
    /**
     * Load all nodes from namespace.
     *
     * @param namespace Logical partition key
     * @return Mapping of nodeId to serialized VectorNode dict
     */
    public Map<String, Map<String, Object>> loadFromDb(String namespace) {
        ensureCollection();
        
        try {
            String filter = FIELD_NS + " == \"" + namespace + "\"";
            QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(List.of(FIELD_ID, FIELD_CONTENT, FIELD_EMBEDDING, FIELD_METADATA))
                .build();
            
            QueryResp response = client.query(queryReq);
            List<QueryResp.QueryResult> results = response.getQueryResults();
            
            Map<String, Map<String, Object>> data = new LinkedHashMap<>();
            for (QueryResp.QueryResult result : results) {
                Map<String, Object> entity = result.getEntity();
                String id = (String) entity.get(FIELD_ID);
                
                Map<String, Object> nodeData = new HashMap<>();
                nodeData.put("id", id);
                nodeData.put("content", entity.get(FIELD_CONTENT));
                
                // Convert embedding
                Object embObj = entity.get(FIELD_EMBEDDING);
                if (embObj instanceof List<?> embList) {
                    List<Double> embedding = new ArrayList<>();
                    for (Object obj : embList) {
                        if (obj instanceof Number num) {
                            embedding.add(num.doubleValue());
                        }
                    }
                    nodeData.put("embedding", embedding);
                }
                
                nodeData.put("metadata", entity.getOrDefault(FIELD_METADATA, new HashMap<>()));
                data.put(id, nodeData);
            }
            
            log.info("Loaded {} nodes from namespace '{}'", data.size(), namespace);
            return data;
        } catch (Exception e) {
            log.error("loadFromDb failed for namespace '{}': {}", namespace, e.getMessage());
            throw new RuntimeException("Failed to load from Milvus", e);
        }
    }
    
    /**
     * Search for nodes by embedding similarity.
     *
     * @param namespace Logical partition key
     * @param embedding Query embedding vector
     * @param topK      Number of results to return
     * @return List of result dicts with id, content, metadata, score
     */
    public List<Map<String, Object>> search(String namespace, List<Double> embedding, int topK) {
        ensureCollection();
        
        // Convert Double to Float for Milvus
        List<Float> floatEmbedding = embedding.stream()
            .map(Double::floatValue)
            .collect(Collectors.toList());
        
        try {
            String filter = FIELD_NS + " == \"" + namespace + "\"";
            
            SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(new FloatVec(floatEmbedding)))
                .annsField(FIELD_EMBEDDING)
                .topK(topK)
                .filter(filter)
                .outputFields(List.of(FIELD_ID, FIELD_CONTENT, FIELD_METADATA))
                .build();
            
            SearchResp response = client.search(searchReq);
            List<Map<String, Object>> results = new ArrayList<>();
            
            // getSearchResults() returns List<List<SearchResp.SearchResult>> (nested list)
            // Each inner list corresponds to one query vector
            List<List<SearchResp.SearchResult>> searchResults = response.getSearchResults();
            for (List<SearchResp.SearchResult> resultList : searchResults) {
                for (SearchResp.SearchResult result : resultList) {
                    Map<String, Object> entity = result.getEntity();
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", entity.get(FIELD_ID));
                    item.put("content", entity.get(FIELD_CONTENT));
                    item.put("embedding", null); // omitted from search results
                    item.put("metadata", entity.getOrDefault(FIELD_METADATA, new HashMap<>()));
                    item.put("score", result.getScore());
                    results.add(item);
                }
            }
            
            log.debug("search returned {} hits from namespace '{}'", results.size(), namespace);
            return results;
        } catch (Exception e) {
            log.error("Search failed in namespace '{}': {}", namespace, e.getMessage());
            throw new RuntimeException("Failed to search Milvus", e);
        }
    }
    
    /**
     * Return true if namespace contains at least one node.
     */
    public boolean exists(String namespace) {
        try {
            if (!collectionInitialized) {
                HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
                if (!client.hasCollection(hasReq)) {
                    return false;
                }
            }
            
            ensureCollection();
            String filter = FIELD_NS + " == \"" + namespace + "\"";
            QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(List.of(FIELD_ID))
                .limit(1)
                .build();
            
            QueryResp response = client.query(queryReq);
            return !response.getQueryResults().isEmpty();
        } catch (Exception e) {
            log.error("exists() failed for namespace '{}': {}", namespace, e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete all nodes belonging to namespace.
     *
     * @return true if nodes were deleted; false if namespace was empty
     */
    public boolean delete(String namespace) {
        ensureCollection();
        
        try {
            String filter = FIELD_NS + " == \"" + namespace + "\"";
            
            // Get IDs first
            QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(filter)
                .outputFields(List.of(FIELD_ID))
                .build();
            QueryResp response = client.query(queryReq);
            
            if (response.getQueryResults().isEmpty()) {
                log.info("delete(): namespace '{}' was already empty", namespace);
                return false;
            }
            
            List<String> ids = response.getQueryResults().stream()
                .map(r -> (String) r.getEntity().get(FIELD_ID))
                .collect(Collectors.toList());
            
            DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(idsExpr(ids))
                .build();
            client.delete(deleteReq);
            
            log.info("Deleted {} nodes from namespace '{}'", ids.size(), namespace);
            return true;
        } catch (Exception e) {
            log.error("delete() failed for namespace '{}': {}", namespace, e.getMessage());
            throw new RuntimeException("Failed to delete from Milvus", e);
        }
    }
    
    /**
     * Delete specific nodes by their IDs within namespace.
     *
     * @param namespace Logical partition key (used for logging)
     * @param nodeIds   Node IDs to remove
     * @return true on success
     */
    public boolean deleteNodes(String namespace, List<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return true;
        }
        
        ensureCollection();
        
        try {
            DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName)
                .filter(idsExpr(nodeIds))
                .build();
            client.delete(deleteReq);
            
            log.info("Deleted {} nodes from namespace '{}'", nodeIds.size(), namespace);
            return true;
        } catch (Exception e) {
            log.error("deleteNodes() failed for namespace '{}': {}", namespace, e.getMessage());
            throw new RuntimeException("Failed to delete nodes from Milvus", e);
        }
    }
    
    // ------------------------------------------------------------------
    // Index management
    // ------------------------------------------------------------------
    
    /**
     * (Re)create an ANN index on the embedding field.
     *
     * @param indexType       Index algorithm: "HNSW", "IVF_FLAT", "IVF_SQ8", "FLAT"
     * @param metricType      Distance metric; defaults to connector's metricType
     * @param m               HNSW - number of bi-directional links per node (default 16)
     * @param efConstruction  HNSW - size of dynamic candidate list during index build (default 64)
     * @param nlist           IVF - number of cluster centroids (default 128)
     */
    public void createIndex(
        String indexType,
        String metricType,
        int m,
        int efConstruction,
        int nlist
    ) {
        ensureCollection();
        
        String mt = metricType != null ? metricType : this.metricType;
        
        try {
            DropIndexReq dropReq = DropIndexReq.builder()
                .collectionName(collectionName)
                .fieldName(FIELD_EMBEDDING)
                .build();
            client.dropIndex(dropReq);
            log.info("Dropped existing index on '{}'", collectionName);
        } catch (Exception e) {
            // Index might not exist, ignore
        }
        
        IndexParam.IndexType idxType = IndexParam.IndexType.valueOf(indexType);
        Map<String, Object> extraParams = new HashMap<>();
        
        if (indexType.equals("HNSW")) {
            extraParams.put("M", String.valueOf(m));
            extraParams.put("efConstruction", String.valueOf(efConstruction));
        } else if (indexType.startsWith("IVF")) {
            extraParams.put("nlist", String.valueOf(nlist));
        }
        
        IndexParam indexParam = IndexParam.builder()
            .fieldName(FIELD_EMBEDDING)
            .indexType(idxType)
            .metricType(IndexParam.MetricType.valueOf(mt))
            .extraParams(extraParams)
            .build();
        
        CreateIndexReq indexReq = CreateIndexReq.builder()
            .collectionName(collectionName)
            .indexParams(List.of(indexParam))
            .build();
        client.createIndex(indexReq);
        
        LoadCollectionReq loadReq = LoadCollectionReq.builder()
            .collectionName(collectionName)
            .build();
        client.loadCollection(loadReq);
        
        log.info("Created {} index on '{}' (metric={})", indexType, collectionName, mt);
    }
    
    // ------------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------------
    
    /**
     * Return all distinct namespace values stored in the collection.
     */
    public List<String> listNamespaces() {
        ensureCollection();
        
        try {
            QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .filter(FIELD_ID + " != \"\"")
                .outputFields(List.of(FIELD_NS))
                .build();
            QueryResp response = client.query(queryReq);
            
            Set<String> namespaces = response.getQueryResults().stream()
                .map(r -> (String) r.getEntity().get(FIELD_NS))
                .collect(Collectors.toSet());
            
            List<String> sorted = new ArrayList<>(namespaces);
            Collections.sort(sorted);
            return sorted;
        } catch (Exception e) {
            log.error("listNamespaces() failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Return the number of stored nodes, optionally filtered by namespace.
     *
     * @param namespace If given, count only nodes in that partition
     */
    public int count(String namespace) {
        ensureCollection();
        
        try {
            if (namespace != null) {
                String filter = FIELD_NS + " == \"" + namespace + "\"";
                QueryReq queryReq = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(filter)
                    .outputFields(List.of(FIELD_ID))
                    .build();
                QueryResp response = client.query(queryReq);
                return response.getQueryResults().size();
            }
            
            GetCollectionStatsReq statsReq = GetCollectionStatsReq.builder()
                .collectionName(collectionName)
                .build();
            GetCollectionStatsResp stats = client.getCollectionStats(statsReq);
            return stats.getNumOfEntities().intValue();
        } catch (Exception e) {
            log.error("count() failed: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Flush buffered inserts/deletes to persistent Milvus storage.
     */
    public void flush() {
        if (collectionInitialized) {
            try {
                FlushReq flushReq = FlushReq.builder()
                    .collectionNames(List.of(collectionName))
                    .build();
                client.flush(flushReq);
                log.debug("Flushed Milvus collection '{}'", collectionName);
            } catch (Exception e) {
                log.warn("Flush failed: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Disconnect this client from Milvus.
     */
    public void close() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            log.warn("Error disconnecting from Milvus: {}", e.getMessage());
        }
        
        collectionInitialized = false;
        log.info("MilvusConnector closed (host={}, port={})", host, port);
    }
    
    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public String getCollectionName() {
        return collectionName;
    }
    
    public Integer getDim() {
        return dim;
    }
    
    public String getMetricType() {
        return metricType;
    }
    
    public boolean isCollectionInitialized() {
        return collectionInitialized;
    }
    
    /**
     * Inject a collection client directly, bypassing normal initialization.
     * Intended for unit tests that supply a mock client.
     */
    public void setClient(MilvusClientV2 client) {
        this.client = client;
        this.collectionInitialized = true;
    }
}