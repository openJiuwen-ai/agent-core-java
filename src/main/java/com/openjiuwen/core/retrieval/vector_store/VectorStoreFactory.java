/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import io.milvus.v2.client.MilvusClientV2;

import java.util.Map;

/**
 * Factory for creating vector stores from configuration.
 */
public final class VectorStoreFactory {

    private VectorStoreFactory() {
    }

    public static VectorStore createVectorStore(VectorStoreConfig config) {
        return createVectorStore(config, Map.of());
    }

    public static VectorStore createVectorStore(VectorStoreConfig config, Map<String, Object> options) {
        if (config == null) {
            throw RetrievalExceptions.validation("VectorStoreConfig is required");
        }
        config.validate();
        String indexType = stringOption(options, "index_type", "indexType");
        if (indexType == null || indexType.isBlank()) {
            indexType = "hybrid";
        }
        return switch (config.getStoreType()) {
            case MILVUS -> createMilvusStore(config, indexType, options);
            case CHROMA -> new ChromaVectorStore(config, indexType);
            case PGVECTOR -> new PGVectorStore(config, indexType);
        };
    }

    private static VectorStore createMilvusStore(VectorStoreConfig config,
                                                 String indexType,
                                                 Map<String, Object> options) {
        Object providedClient = firstOption(options, "milvus_client", "milvusClient", "client");
        if (providedClient instanceof MilvusClientV2 client) {
            return new MilvusVectorStore(client, config, indexType);
        }
        String uri = stringOption(options, "milvus_uri", "milvusUri", "uri");
        if (uri == null || uri.isBlank()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "milvus_uri or milvusClient is required for MilvusVectorStore");
        }
        String token = stringOption(options, "milvus_token", "milvusToken", "token");
        return new MilvusVectorStore(config, uri, token, indexType);
    }

    private static Object firstOption(Map<String, Object> options, String... keys) {
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

    private static String stringOption(Map<String, Object> options, String... keys) {
        Object value = firstOption(options, keys);
        return value == null ? null : String.valueOf(value);
    }
}
