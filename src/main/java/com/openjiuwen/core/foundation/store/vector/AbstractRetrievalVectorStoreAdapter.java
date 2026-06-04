/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.spi.store.vector.BaseVectorStore;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;
import com.openjiuwen.spi.store.vector.VectorSearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that exposes retrieval vector stores through foundation.store's BaseVectorStore API.
 */
abstract class AbstractRetrievalVectorStoreAdapter extends BaseVectorStore {

    private final VectorStore delegate;
    private final Map<String, CollectionSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, FieldMapping> fieldMappings = new ConcurrentHashMap<>();

    protected AbstractRetrievalVectorStoreAdapter(VectorStore delegate) {
        this.delegate = delegate;
    }

    protected VectorStore delegate() {
        return delegate;
    }

    @Override
    public void createCollection(String collectionName, Object schema, Map<String, Object> kwargs) throws Exception {
        CollectionSchema resolvedSchema = normalizeSchema(schema);
        FieldMapping mapping = resolveMapping(resolvedSchema);
        schemas.put(collectionName, resolvedSchema);
        fieldMappings.put(collectionName, mapping);

        Integer dimension = resolvedSchema.getVectorFields().stream()
                .findFirst()
                .map(FieldSchema::getDim)
                .orElse(null);

        VectorStore scoped = delegate.withCollection(collectionName);
        String requestedIndexType = scoped.getIndexType();
        if (kwargs != null) {
            Object rawIndexType = kwargs.containsKey("indexType") ? kwargs.get("indexType") : kwargs.get("index_type");
            if (rawIndexType != null) {
                String normalized = String.valueOf(rawIndexType).toLowerCase();
                if (RetrievalValidation.INDEX_TYPES.contains(normalized)) {
                    requestedIndexType = normalized;
                }
            }
        }
        scoped.ensureCollection(collectionName, requestedIndexType, dimension, kwargs == null ? Map.of() : kwargs);
        updateCollectionMetadata(collectionName, collectionMetadata(resolvedSchema, mapping, kwargs));
    }

    @Override
    public void deleteCollection(String collectionName, Map<String, Object> kwargs) throws Exception {
        delegate.deleteTable(collectionName);
        schemas.remove(collectionName);
    }

    @Override
    public boolean collectionExists(String collectionName, Map<String, Object> kwargs) throws Exception {
        return delegate.tableExists(collectionName) || schemas.containsKey(collectionName);
    }

    @Override
    public CollectionSchema getSchema(String collectionName, Map<String, Object> kwargs) throws Exception {
        if (!collectionExists(collectionName, kwargs)) {
            throw new IllegalArgumentException("collection doesn't exist: " + collectionName);
        }
        return schemas.computeIfAbsent(collectionName, key -> defaultSchema());
    }

    @Override
    public void addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) throws Exception {
        VectorStore scoped = delegate.withCollection(collectionName);
        scoped.add(normalizeDocs(collectionName, docs),
                kwargs != null && kwargs.get("batch_size") instanceof Number number ? number.intValue() : null,
                kwargs);
    }

    @Override
    public List<VectorSearchResult> search(String collectionName,
                                           List<Float> queryVector,
                                           String vectorField,
                                           int topK,
                                           Map<String, Object> filters,
                                           Map<String, Object> kwargs) throws Exception {
        VectorStore scoped = delegate.withCollection(collectionName);
        // Pass vectorField through via options so the backend can use it
        Map<String, Object> options = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();
        if (vectorField != null && !vectorField.isBlank()) {
            options.put("vector_field", vectorField);
        }
        List<SearchResult> results = scoped.search(queryVector, topK, filters, options);
        FieldMapping mapping = mappingFor(collectionName);
        List<VectorSearchResult> mapped = new ArrayList<>();
        for (SearchResult result : results) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put(mapping.primaryKey(), result.getId());
            Map<String, Object> metadata = result.getMetadata();
            if (metadata != null) {
                fields.putAll(metadata);
            }
            if (mapping.textField() != null) {
                fields.put(mapping.textField(), result.getText());
            }
            mapped.add(VectorSearchResult.builder()
                    .score(result.getScore())
                    .fields(fields)
                    .build());
        }
        return mapped;
    }

    @Override
    public void deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) throws Exception {
        delegate.withCollection(collectionName).delete(ids, null, kwargs);
    }

    @Override
    public void deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) throws Exception {
        delegate.withCollection(collectionName).delete(null, filters, kwargs);
    }

    @Override
    public List<String> listCollectionNames() throws Exception {
        // Delegate to real backend when possible (e.g. Milvus can list collections)
        if (delegate instanceof com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore milvusStore) {
            List<String> names = new ArrayList<>(milvusStore.getClient()
                    .listCollections().getCollectionNames());
            // Merge with locally cached names
            for (String cached : schemas.keySet()) {
                if (!names.contains(cached)) {
                    names.add(cached);
                }
            }
            return names;
        }
        // Fallback: return known collection names from cache
        return new ArrayList<>(schemas.keySet());
    }

    @Override
    public void updateSchema(String collectionName, List<?> operations) throws Exception {
        // Default: update in-memory schema cache if possible
        CollectionSchema current = schemas.get(collectionName);
        if (current != null) {
            CollectionSchema newSchema = VectorStoreUtils.computeNewSchema(current, operations);
            schemas.put(collectionName, newSchema);
        }
    }

    /** Per-collection metadata cache. */
    private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();

    @Override
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) throws Exception {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        // Update local metadata cache
        collectionMetadata.merge(collectionName, new LinkedHashMap<>(metadata), (existing, incoming) -> {
            existing.putAll(incoming);
            return existing;
        });
        // Attempt to propagate to real backend when possible
        if (delegate instanceof com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore milvusStore) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (var entry : metadata.entrySet()) {
                properties.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            try {
                milvusStore.getClient().alterCollectionProperties(
                        io.milvus.v2.service.collection.request.AlterCollectionPropertiesReq.builder()
                                .collectionName(collectionName)
                                .properties(properties)
                                .build());
            } catch (Exception e) {
                // Log but don't fail - metadata update is best-effort on some backends
                com.openjiuwen.core.common.logging.Loggers.STORE.warn(
                        "Failed to update collection metadata on backend: %s", e.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) throws Exception {
        // Return cached metadata if available
        Map<String, Object> cached = collectionMetadata.get(collectionName);
        if (cached != null) {
            Map<String, Object> result = new LinkedHashMap<>(defaultsFor(collectionName));
            result.putAll(cached);
            return result;
        }
        // Attempt to fetch from real backend
        if (delegate instanceof com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore milvusStore) {
            try {
                var info = milvusStore.getClient().describeCollection(
                        io.milvus.v2.service.collection.request.DescribeCollectionReq.builder()
                                .collectionName(collectionName).build());
                Map<String, Object> result = new LinkedHashMap<>();
                if (info.getProperties() != null) {
                    result.putAll(info.getProperties());
                }
                collectionMetadata.put(collectionName, result);
                return result;
            } catch (Exception e) {
                // Fall through to empty map
            }
        }
        return new LinkedHashMap<>(defaultsFor(collectionName));
    }

    /**
     * Close the underlying vector store connection.
     * Delegates to the retrieval-layer store's close method.
     */
    public void close() {
        delegate.close();
    }

    private CollectionSchema normalizeSchema(Object schema) {
        if (schema instanceof CollectionSchema collectionSchema) {
            return collectionSchema;
        }
        if (schema instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return CollectionSchema.fromDict(typedMap);
        }
        return defaultSchema();
    }

    private FieldMapping resolveMapping(CollectionSchema schema) {
        FieldSchema primaryField = schema.getPrimaryKeyField()
                .orElseThrow(() -> new IllegalArgumentException(
                        "schema must contain a primary key field (is_primary=true)"));
        List<FieldSchema> vectorFields = schema.getVectorFields();
        if (vectorFields.isEmpty()) {
            throw new IllegalArgumentException("schema must contain at least one FLOAT_VECTOR field");
        }
        String textField = null;
        for (FieldSchema field : schema.getFields()) {
            if (field.getDtype() == VectorDataType.VARCHAR && !field.isPrimary()) {
                textField = field.getName();
                break;
            }
        }
        return new FieldMapping(primaryField.getName(), vectorFields.get(0).getName(), textField);
    }

    private FieldMapping mappingFor(String collectionName) {
        return fieldMappings.computeIfAbsent(collectionName, key -> resolveMapping(schemas.getOrDefault(key, defaultSchema())));
    }

    private List<Map<String, Object>> normalizeDocs(String collectionName, List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        FieldMapping mapping = mappingFor(collectionName);
        List<Map<String, Object>> normalized = new ArrayList<>(docs.size());
        for (Map<String, Object> doc : docs) {
            Object id = doc.get(mapping.primaryKey());
            if (id == null) {
                throw new IllegalArgumentException("document must have primary field '" + mapping.primaryKey() + "'");
            }
            Object vector = doc.get(mapping.vectorField());
            if (vector == null) {
                throw new IllegalArgumentException("document must have vector field '" + mapping.vectorField() + "'");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(id));
            item.put("chunk_id", String.valueOf(id));
            item.put("vector", vector);
            if (mapping.textField() != null) {
                item.put("text", String.valueOf(doc.getOrDefault(mapping.textField(), "")));
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            Object existingMetadata = doc.get("metadata");
            if (existingMetadata instanceof Map<?, ?> existingMap) {
                for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                    metadata.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String key = entry.getKey();
                if (!key.equals(mapping.primaryKey())
                        && !key.equals(mapping.vectorField())
                        && !key.equals(mapping.textField())
                        && !"metadata".equals(key)) {
                    metadata.put(key, entry.getValue());
                }
            }
            item.put("metadata", metadata);
            normalized.add(item);
        }
        return normalized;
    }

    private Map<String, Object> collectionMetadata(CollectionSchema schema,
                                                   FieldMapping mapping,
                                                   Map<String, Object> kwargs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schema", schema.toDict());
        metadata.put("fields", schema.toDict());
        metadata.put("field_mapping", Map.of(
                "primary_key", mapping.primaryKey(),
                "vector_field", mapping.vectorField(),
                "text_field", mapping.textField() == null ? "" : mapping.textField()));
        metadata.put("primary_key", mapping.primaryKey());
        metadata.put("vector_field", mapping.vectorField());
        metadata.put("text_field", mapping.textField());
        metadata.put("distance_metric", kwargs != null && kwargs.get("distance_metric") != null
                ? String.valueOf(kwargs.get("distance_metric")).toLowerCase()
                : delegate.getDistanceMetric());
        metadata.put("schema_version", 0);
        return metadata;
    }

    private Map<String, Object> defaultsFor(String collectionName) {
        FieldMapping mapping = mappingFor(collectionName);
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("distance_metric", delegate.getDistanceMetric());
        defaults.put("schema_version", 0);
        defaults.put("primary_key", mapping.primaryKey());
        defaults.put("vector_field", mapping.vectorField());
        defaults.put("text_field", mapping.textField());
        return defaults;
    }

    private CollectionSchema defaultSchema() {
        return CollectionSchema.fromFields(List.of(
                FieldSchema.builder().name("id").dtype(VectorDataType.VARCHAR).isPrimary(true).maxLength(256).build(),
                FieldSchema.builder().name("embedding").dtype(VectorDataType.FLOAT_VECTOR).dim(1536).build(),
                FieldSchema.builder().name("text").dtype(VectorDataType.VARCHAR).maxLength(65535).build(),
                FieldSchema.builder().name("metadata").dtype(VectorDataType.JSON).build()
        ), "Default adapter schema", false);
    }

    private record FieldMapping(String primaryKey, String vectorField, String textField) {
    }
}
