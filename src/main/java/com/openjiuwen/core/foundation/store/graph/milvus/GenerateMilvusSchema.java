/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.graph.GraphStoreConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility function for generating milvus graph store's schema and index.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/core/foundation/store/graph/milvus/generate_milvus_schema.py}.
 */
public final class GenerateMilvusSchema {

    public static final String ENTITY_COLLECTION = GraphStoreConstants.ENTITY_COLLECTION;
    public static final String RELATION_COLLECTION = GraphStoreConstants.RELATION_COLLECTION;
    public static final String EPISODE_COLLECTION = GraphStoreConstants.EPISODE_COLLECTION;

    private static final List<Object> ICU_FILTER = List.of(
            "asciifolding",
            "lowercase",
            Map.of("type", "stemmer", "language", "english"),
            "removepunct"
    );
    private static final Map<String, Object> ICU_ANALYZER = Map.of("tokenizer", "icu", "filter", ICU_FILTER);
    private static final Map<String, Object> ICU_ANALYZER_WITH_STOPWORDS = Map.of(
            "tokenizer", "icu",
            "filter", List.of(
                    "asciifolding",
                    "lowercase",
                    Map.of("type", "stemmer", "language", "english"),
                    "removepunct",
                    Map.of("type", "stop", "stop_words", List.of("of", "to", "_english_"))
            )
    );
    private static final Map<String, Object> EXACT_MATCH_ANALYZER = Map.of("tokenizer", "whitespace");

    private GenerateMilvusSchema() {
    }

    public static SchemaResult generateSchemaAndIndex(String collection,
                                                      GraphStoreStorageConfig storageConfig,
                                                      GraphStoreIndexConfig embedConfig,
                                                      int dim,
                                                      boolean dynamicField) {
        String indexType;
        if ("auto".equals(embedConfig.getIndexType().getIndexType())) {
            indexType = "AUTOINDEX";
        } else {
            indexType = embedConfig.getIndexType().getIndexType().toUpperCase();
            String variant = embedConfig.getIndexType().getVariant();
            if (variant != null) {
                indexType = indexType + "_" + variant.toUpperCase();
            }
        }
        String metricType = embedConfig.getDistanceMetric()
                .replace("dot", "ip")
                .replace("euclidean", "l2")
                .toUpperCase();

        SchemaResult result = new SchemaResult(collection, indexType, metricType, dynamicField);
        addCommonFields(result, storageConfig);

        if (ENTITY_COLLECTION.equals(collection)) {
            result.addField("name", field("VARCHAR", storageConfig.getName(), null, true, true, ICU_ANALYZER, null));
            result.addField("name_embedding", field("FLOAT_VECTOR", null, dim, false, false, null, null));
            result.addField("attributes", field("JSON", null, null, false, false, null, null));
            result.addIndex("name_embedding", "semantic_embedding_name", indexType, metricType, embedConfig.getExtraConfigs());
            result.addField("relations", arrayField("VARCHAR", storageConfig.getUuid(), storageConfig.getRelations()));
            result.addField("episodes", arrayField("VARCHAR", storageConfig.getUuid(), storageConfig.getEpisodes()));
        } else if (RELATION_COLLECTION.equals(collection)) {
            result.addField("valid_since", field("INT64", null, null, false, false, null, null));
            result.addField("valid_until", field("INT64", null, null, false, false, null, null));
            result.addField("offset_since", field("INT8", null, null, false, false, null, null));
            result.addField("offset_until", field("INT8", null, null, false, false, null, null));
            result.addField("name", field("VARCHAR", storageConfig.getName(), null, false, false, null, null));
            result.addField("lhs", field("VARCHAR", storageConfig.getUuid(), null, false, false, null, null));
            result.addField("rhs", field("VARCHAR", storageConfig.getUuid(), null, false, false, null, null));
        } else if (EPISODE_COLLECTION.equals(collection)) {
            result.addField("valid_since", field("INT64", null, null, false, false, null, null));
            result.addField("entities", arrayField("VARCHAR", storageConfig.getUuid(), storageConfig.getEntities()));
        } else {
            throw new IllegalArgumentException("Collection not supported, collection=" + collection);
        }

        Map<String, Object> analyzer = embedConfig.getBm25AnalyzerSettings();
        result.addField(
                "content",
                field("VARCHAR", storageConfig.getContent(), null, false, true, analyzer == null ? ICU_ANALYZER_WITH_STOPWORDS : analyzer, null)
        );
        if (dim > 0) {
            result.addField("content_embedding", field("FLOAT_VECTOR", null, dim, false, false, null, null));
            result.addIndex("content_embedding", "semantic_embedding_content", indexType, metricType, embedConfig.getExtraConfigs());
        }
        result.addField("content_bm25", field("SPARSE_FLOAT_VECTOR", null, null, false, false, null, null));
        result.addFunction(Map.of(
                "name", "bm25_func",
                "input_field_names", List.of("content"),
                "output_field_names", List.of("content_bm25"),
                "function_type", "BM25"
        ));
        result.addIndex(
                "content_bm25",
                "sparse_inverted_index",
                "SPARSE_INVERTED_INDEX",
                "BM25",
                Map.of(
                        "params",
                        Map.of(
                                "bm25_b", embedConfig.getBm25Config().getBm25B(),
                                "bm25_k1", embedConfig.getBm25Config().getBm25K1()
                        )
                )
        );
        return result;
    }

    private static void addCommonFields(SchemaResult result, GraphStoreStorageConfig storageConfig) {
        result.addField("uuid", primaryVarcharField(storageConfig.getUuid()));
        result.addField("created_at", field("INT64", null, null, false, false, null, null));
        result.addField("user_id", field("VARCHAR", storageConfig.getUserId(), null, false, false, null, null));
        result.addField("obj_type", field("VARCHAR", storageConfig.getObjType(), null, false, true, EXACT_MATCH_ANALYZER, true));
        result.addField("language", field("VARCHAR", storageConfig.getLanguage(), null, false, false, null, null));
        result.addField("metadata", field("JSON", null, null, false, false, null, null));
    }

    private static Map<String, Object> primaryVarcharField(int maxLength) {
        Map<String, Object> field = field("VARCHAR", maxLength, null, true, false, null, null);
        field.put("auto_id", false);
        return field;
    }

    private static Map<String, Object> arrayField(String elementType, int maxLength, int maxCapacity) {
        Map<String, Object> field = field("ARRAY", null, null, false, false, null, null);
        field.put("element_type", elementType);
        field.put("max_length", maxLength);
        field.put("max_capacity", maxCapacity);
        return field;
    }

    private static Map<String, Object> field(String type,
                                             Integer maxLength,
                                             Integer dim,
                                             boolean primary,
                                             boolean analyzerEnabled,
                                             Map<String, Object> analyzerParams,
                                             Boolean matchEnabled) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", type);
        if (primary) {
            field.put("is_primary", true);
        }
        if (maxLength != null) {
            field.put("max_length", maxLength);
        }
        if (dim != null) {
            field.put("dim", dim);
        }
        if (analyzerEnabled) {
            field.put("enable_analyzer", true);
        }
        if (matchEnabled != null) {
            field.put("enable_match", matchEnabled);
        }
        if (analyzerParams != null) {
            field.put("analyzer_params", analyzerParams);
        }
        return field;
    }

    public static final class SchemaResult {
        private final String collection;
        private final String indexType;
        private final String metricType;
        private final boolean dynamicField;
        private final Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
        private final List<Map<String, Object>> indexes = new ArrayList<>();
        private final List<Map<String, Object>> functions = new ArrayList<>();

        private SchemaResult(String collection, String indexType, String metricType, boolean dynamicField) {
            this.collection = collection;
            this.indexType = indexType;
            this.metricType = metricType;
            this.dynamicField = dynamicField;
        }

        public void addField(String name, Map<String, Object> spec) {
            Map<String, Object> field = new LinkedHashMap<>(spec);
            field.put("name", name);
            fields.put(name, field);
        }

        public void addIndex(String fieldName,
                             String indexName,
                             String indexType,
                             String metricType,
                             Map<String, Object> extraConfigs) {
            Map<String, Object> index = new LinkedHashMap<>();
            index.put("field_name", fieldName);
            index.put("index_name", indexName);
            index.put("index_type", indexType);
            index.put("metric_type", metricType);
            if (extraConfigs != null && !extraConfigs.isEmpty()) {
                index.putAll(extraConfigs);
            }
            indexes.add(index);
        }

        public void addFunction(Map<String, Object> functionSpec) {
            functions.add(new LinkedHashMap<>(functionSpec));
        }

        public String getCollection() {
            return collection;
        }

        public String getIndexType() {
            return indexType;
        }

        public String getMetricType() {
            return metricType;
        }

        public boolean isDynamicField() {
            return dynamicField;
        }

        public Map<String, Map<String, Object>> getFields() {
            return fields;
        }

        public List<Map<String, Object>> getIndexes() {
            return indexes;
        }

        public List<Map<String, Object>> getFunctions() {
            return functions;
        }
    }
}
