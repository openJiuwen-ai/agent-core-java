/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.vector_fields.VectorField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Generator for Milvus schema and index definitions.
 * <p>
 * Mirrors Python's {@code generate_milvus_schema.py} module from
 * <code>openjiuwen/core/foundation/store/graph/milvus/generate_milvus_schema.py</code>.
 */
public class GenerateMilvusSchema {

    private static final Logger LOGGER = Logger.getLogger(GenerateMilvusSchema.class.getName());

    // Collection names
    public static final String ENTITY_COLLECTION = "entity";
    public static final String RELATION_COLLECTION = "relation";
    public static final String EPISODE_COLLECTION = "episode";

    // Analyzer configurations (mirrors Python constants)
    private static final Map<String, Object> ICU_ANALYZER = new HashMap<>();
    private static final Map<String, Object> EXACT_MATCH_ANALYZER = new HashMap<>();

    static {
        // ICU analyzer configuration
        ICU_ANALYZER.put("tokenizer", "icu");

        // Exact match analyzer configuration
        EXACT_MATCH_ANALYZER.put("tokenizer", "whitespace");
    }

    /**
     * Generate schema and index for a Milvus collection.
     *
     * @param collection collection name (entity, relation, or episode)
     * @param storageConfig storage configuration
     * @param embedConfig embedding configuration
     * @param dim embedding dimension
     * @param dynamicField enable dynamic field
     * @return schema result object
     */
    public static SchemaResult generateSchemaAndIndex(
            String collection,
            GraphStoreStorageConfig storageConfig,
            GraphStoreIndexConfig embedConfig,
            int dim,
            boolean dynamicField) {
        if (!ENTITY_COLLECTION.equals(collection)
                && !RELATION_COLLECTION.equals(collection)
                && !EPISODE_COLLECTION.equals(collection)) {
            throw new IllegalArgumentException("Collection not supported, collection=" + collection);
        }

        // Determine index type
        String indexType = "AUTOINDEX";
        if (embedConfig.getIndexType() != null) {
            String rawIndexType = embedConfig.getIndexType().getIndexType();
            if ("auto".equals(rawIndexType)) {
                indexType = "AUTOINDEX";
            } else {
                indexType = rawIndexType.toUpperCase();
                String variant = embedConfig.getIndexType().getVariant();
                if (variant != null) {
                    indexType = indexType + "_" + variant.toUpperCase();
                }
            }
        }

        // Determine metric type
        String metricType = embedConfig.getDistanceMetric()
                .replace("dot", "ip")
                .replace("euclidean", "l2")
                .toUpperCase();

        LOGGER.info("Generating schema for collection: " + collection);

        SchemaResult result = new SchemaResult(collection, indexType, metricType, dynamicField);
        addCommonFields(result, storageConfig);
        switch (collection) {
            case ENTITY_COLLECTION -> {
                result.addField("name", storageConfig.getName());
                result.addField("name_embedding", dim);
                result.addField("attributes", "json");
                result.addField("relations", storageConfig.getRelations());
                result.addField("episodes", storageConfig.getEpisodes());
                result.addIndex("name_embedding");
            }
            case RELATION_COLLECTION -> {
                result.addField("valid_since", "int64");
                result.addField("valid_until", "int64");
                result.addField("offset_since", "int8");
                result.addField("offset_until", "int8");
                result.addField("name", storageConfig.getName());
                result.addField("lhs", storageConfig.getUuid());
                result.addField("rhs", storageConfig.getUuid());
            }
            case EPISODE_COLLECTION -> {
                result.addField("valid_since", "int64");
                result.addField("entities", storageConfig.getEntities());
            }
            default -> throw new IllegalStateException("Unexpected value: " + collection);
        }
        result.addField("content", storageConfig.getContent());
        if (dim > 0) {
            result.addField("content_embedding", dim);
            result.addIndex("content_embedding");
        }
        result.addField("content_bm25", "sparse_float_vector");
        result.addIndex("content_bm25");
        return result;
    }

    /**
     * Result class for schema generation.
     */
    public static class SchemaResult {
        private final String collection;
        private final String indexType;
        private final String metricType;
        private final Map<String, Object> fields;
        private final List<String> indexedFields;
        private final boolean dynamicField;

        public SchemaResult(String collection, String indexType, String metricType, boolean dynamicField) {
            this.collection = collection;
            this.indexType = indexType;
            this.metricType = metricType;
            this.fields = new HashMap<>();
            this.indexedFields = new ArrayList<>();
            this.dynamicField = dynamicField;
        }

        public String getCollection() { return collection; }
        public String getIndexType() { return indexType; }
        public String getMetricType() { return metricType; }
        public Map<String, Object> getFields() { return fields; }
        public List<String> getIndexedFields() { return indexedFields; }
        public boolean isDynamicField() { return dynamicField; }

        public void addField(String name, Object value) {
            fields.put(name, value);
        }

        public void addIndex(String fieldName) {
            indexedFields.add(fieldName);
        }
    }

    private static void addCommonFields(SchemaResult result, GraphStoreStorageConfig storageConfig) {
        result.addField("uuid", storageConfig.getUuid());
        result.addField("created_at", "int64");
        result.addField("user_id", storageConfig.getUserId());
        result.addField("obj_type", storageConfig.getObjType());
        result.addField("language", storageConfig.getLanguage());
        result.addField("metadata", "json");
    }
}
