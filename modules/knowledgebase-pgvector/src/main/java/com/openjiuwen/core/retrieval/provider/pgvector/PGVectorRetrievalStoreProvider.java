/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.pgvector;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.provider.VectorStoreProvider;
import com.openjiuwen.core.retrieval.vector_store.PGVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

/**
 * Creates retrieval vector stores backed by PostgreSQL with pgvector.
 *
 * @since 0.1.15
 */
public final class PGVectorRetrievalStoreProvider implements VectorStoreProvider {
    /**
     * Returns the provider's store type.
     *
     * @return PGVector store type
     * @since 0.1.15
     */
    @Override
    public String storeType() {
        return "pgvector";
    }

    /**
     * Creates a PGVector retrieval store.
     *
     * @param config vector store configuration
     * @param indexType retrieval index type
     * @param options creation options
     * @return PGVector store
     * @since 0.1.15
     */
    @Override
    public VectorStore create(VectorStoreConfig config, String indexType, Map<String, Object> options) {
        Object dataSourceOption = firstOption(options, List.of("dataSource", "data_source"));
        if (dataSourceOption instanceof DataSource dataSource) {
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
}
