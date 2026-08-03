/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.vector_fields.PGVectorField;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Factory for vector store implementations.
 *
 * <p>Mirrors Python's module-level {@code create_vector_store} in
 * {@code openjiuwen/core/retrieval/vector_store/store.py}.</p>
 */
public final class VectorStoreFactory {

    private VectorStoreFactory() {
    }

    public static VectorStore createVectorStore(VectorStoreConfig config) {
        return createVectorStore(config, Map.of());
    }

    public static VectorStore createVectorStore(VectorStoreConfig config, Map<String, Object> kwargs) {
        Objects.requireNonNull(config, "config");
        Map<String, Object> options = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
        StoreType storeProvider = config.getStoreProvider();
        if (storeProvider == StoreType.MILVUS) {
            return createMilvusVectorStore(config, options);
        }
        if (storeProvider == StoreType.CHROMA) {
            return new ChromaVectorStore(
                    config,
                    stringOption(options, "chroma_path"),
                    stringOption(options, "text_field", "content"),
                    option(options, "vector_field", "embedding"),
                    stringOption(options, "sparse_vector_field", "sparse_vector"),
                    stringOption(options, "metadata_field", "metadata"),
                    stringOption(options, "doc_id_field", "document_id")
            );
        }
        if (storeProvider == StoreType.PGVECTOR) {
            return createPgVectorStore(config, options);
        }
        throw ErrorHelper.buildError(
                StatusCode.RETRIEVAL_VECTOR_STORE_PROVIDER_INVALID,
                "error_msg",
                "unavailable vector store provider: " + storeProvider
                        + ",and available providers are: " + availableProviders()
        );
    }

    private static VectorStore createMilvusVectorStore(VectorStoreConfig config, Map<String, Object> options) {
        Object vectorField = option(options, "vector_field", "embedding");
        Object client = option(options, "milvus_client", null);
        if (client instanceof MilvusVectorStore.MilvusClientFacade facade) {
            return new MilvusVectorStore(
                    config,
                    stringOption(options, "milvus_uri"),
                    stringOption(options, "milvus_token"),
                    stringOption(options, "text_field", "content"),
                    vectorField,
                    stringOption(options, "sparse_vector_field", "sparse_vector"),
                    stringOption(options, "metadata_field", "metadata"),
                    stringOption(options, "doc_id_field", "document_id"),
                    stringOption(options, "milvus_alias"),
                    facade
            );
        }
        return new MilvusVectorStore(
                config,
                stringOption(options, "milvus_uri"),
                stringOption(options, "milvus_token"),
                stringOption(options, "text_field", "content"),
                stringValue(vectorField),
                stringOption(options, "sparse_vector_field", "sparse_vector"),
                stringOption(options, "metadata_field", "metadata"),
                stringOption(options, "doc_id_field", "document_id"),
                stringOption(options, "milvus_alias"),
                options
        );
    }

    private static VectorStore createPgVectorStore(VectorStoreConfig config, Map<String, Object> options) {
        Object vectorField = option(options, "vector_field", "embedding");
        if (vectorField instanceof PGVectorField pgVectorField) {
            return new PGVectorStore(
                    config,
                    stringOption(options, "pg_uri"),
                    stringOption(options, "text_field", "content"),
                    pgVectorField,
                    stringOption(options, "sparse_vector_field", "sparse_vector"),
                    stringOption(options, "metadata_field", "metadata"),
                    stringOption(options, "doc_id_field", "document_id")
            );
        }
        return new PGVectorStore(
                config,
                stringOption(options, "pg_uri"),
                stringOption(options, "text_field", "content"),
                stringValue(vectorField),
                stringOption(options, "sparse_vector_field", "sparse_vector"),
                stringOption(options, "metadata_field", "metadata"),
                stringOption(options, "doc_id_field", "document_id")
        );
    }

    private static Object option(Map<String, Object> options, String key, Object defaultValue) {
        if (options.containsKey(key)) {
            return options.get(key);
        }
        return defaultValue;
    }

    private static String stringOption(Map<String, Object> options, String key) {
        return stringValue(options.get(key));
    }

    private static String stringOption(Map<String, Object> options, String key, String defaultValue) {
        if (!options.containsKey(key)) {
            return defaultValue;
        }
        return stringValue(options.get(key));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String availableProviders() {
        StringJoiner joiner = new StringJoiner(", ");
        for (StoreType type : StoreType.values()) {
            joiner.add(type.getValue());
        }
        return joiner.toString();
    }
}
