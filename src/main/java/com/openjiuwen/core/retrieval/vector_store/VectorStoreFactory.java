/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import io.milvus.v2.client.MilvusClientV2;

import javax.sql.DataSource;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Factory for creating vector stores from configuration.
 */
public final class VectorStoreFactory {

    private VectorStoreFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static VectorStore createVectorStore(VectorStoreConfig config) {
        return createVectorStore(config, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
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

    private static VectorStore createMilvusStore(VectorStoreConfig config,
                                                 String indexType,
                                                 Map<String, Object> options) {
        Object providedClient = firstOption(options, List.of("milvus_client", "milvusClient", "client"));
        if (providedClient instanceof MilvusClientV2 client) {
            return new MilvusVectorStore(client, config, indexType);
        }
        String uri = stringOption(options, List.of("milvus_uri", "milvusUri", "uri"));
        if (uri == null || uri.isBlank()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "milvus_uri or milvusClient is required for MilvusVectorStore");
        }
        String token = stringOption(options, List.of("milvus_token", "milvusToken", "token"));
        return new MilvusVectorStore(config, uri, token, indexType);
    }

    private static VectorStore createPgVectorStore(VectorStoreConfig config,
                                                   String indexType,
                                                   Map<String, Object> options) {
        Object providedDataSource = firstOption(options, List.of("dataSource", "data_source"));
        if (providedDataSource instanceof DataSource dataSource) {
            return new PGVectorStore(config, dataSource, indexType, options);
        }

        String jdbcUrl = stringOption(options, List.of("jdbcUrl", "jdbc_url", "pgUri", "pg_uri", "url"));
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "jdbcUrl or dataSource is required for PGVectorStore");
        }
        String username = stringOption(options, List.of("username", "user"));
        String password = stringOption(options, List.of("password"));
        return new PGVectorStore(config, jdbcUrl, username, password, indexType, options);
    }

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

    private static String stringOption(Map<String, Object> options, List<String> keys) {
        Object value = firstOption(options, keys);
        return value == null ? null : String.valueOf(value);
    }

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
