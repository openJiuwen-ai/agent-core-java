/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import io.milvus.v2.client.MilvusClientV2;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

/**
 * Factory for creating vector stores from configuration.
 * 
 * @since 0.1.7
 */
public final class VectorStoreFactory {
    /**
     * VectorStoreFactory.
     * 
     * @since 0.1.7
     */
    private VectorStoreFactory() {
    }

    /**
     * createVectorStore.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static VectorStore createVectorStore(VectorStoreConfig config) {
        return createVectorStore(config, Map.of());
    }

    /**
     * createVectorStore.
     * 
     * @param config config
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    public static VectorStore createVectorStore(VectorStoreConfig config, Map<String, Object> options) {
        if (config == null) {
            throw RetrievalExceptions.validation("VectorStoreConfig is required");
        }
        config.validate();
        String indexType = resolveRetrievalIndexType(options);
        return switch (config.getStoreType()) {
            case MILVUS -> createMilvusStore(config, indexType, options);
            case CHROMA -> new ChromaVectorStore(config, indexType);
            case PGVECTOR -> createPgVectorStore(config, indexType, options);
            case ELASTICSEARCH -> new ElasticsearchVectorStore(config, indexType);
        };
    }

    /**
     * createMilvusStore.
     * 
     * @param config config
     * @param indexType indexType
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private static VectorStore createMilvusStore(VectorStoreConfig config, String indexType,
            Map<String, Object> options) {
        Object providedClient = firstOption(options, List.of("milvus_client", "milvusClient", "client"));
        if (providedClient instanceof MilvusClientV2 client) {
            return new MilvusVectorStore(client, config, indexType);
        }
        String uri = stringOption(options, List.of("milvus_uri", "milvusUri", "uri"));
        if (uri == null || uri.isBlank()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "milvus_uri or milvusClient is required for MilvusVectorStore");
        }
        String token = stringOption(options, List.of("milvus_token", "milvusToken", "token"));
        return new MilvusVectorStore(config, uri, token, indexType);
    }

    /**
     * createPgVectorStore.
     * 
     * @param config config
     * @param indexType indexType
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private static VectorStore createPgVectorStore(VectorStoreConfig config, String indexType,
            Map<String, Object> options) {
        Object providedDataSource = firstOption(options, List.of("dataSource", "data_source"));
        if (providedDataSource instanceof DataSource dataSource) {
            return new PGVectorStore(config, dataSource, indexType, options);
        }

        String jdbcUrl = stringOption(options, List.of("jdbcUrl", "jdbc_url", "pgUri", "pg_uri", "url"));
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "jdbcUrl or dataSource is required for PGVectorStore");
        }
        String username = stringOption(options, List.of("username", "user"));
        String password = stringOption(options, List.of("password"));
        return new PGVectorStore(config, jdbcUrl, username, password, indexType, options);
    }

    /**
     * firstOption.
     * 
     * @param options options
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object firstOption(Map<String, Object> options, List<String> keys) {
        if (options == null) {
            return null;
        }
        for (String key : keys) {
            if (options.containsKey(key)) {
                return options.get(key);
            }
        }
        return null;
    }

    /**
     * stringOption.
     * 
     * @param options options
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static String stringOption(Map<String, Object> options, List<String> keys) {
        Object value = firstOption(options, keys);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * resolveRetrievalIndexType.
     * 
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private static String resolveRetrievalIndexType(Map<String, Object> options) {
        String requested = stringOption(options, List.of("indexType", "index_type"));
        if (requested == null || requested.isBlank()) {
            return "hybrid";
        }
        String normalized = requested.toLowerCase(Locale.ROOT);
        if (RetrievalValidation.INDEX_TYPES.contains(normalized)) {
            return normalized;
        }
        return "hybrid";
    }
}
