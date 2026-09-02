/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.milvus;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.provider.VectorStoreProvider;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import io.milvus.v2.client.MilvusClientV2;

import java.util.List;
import java.util.Map;

/**
 * Creates retrieval vector stores backed by Milvus.
 *
 * @since 0.1.15
 */
public final class MilvusRetrievalVectorStoreProvider implements VectorStoreProvider {
    /**
     * Returns the provider's store type.
     *
     * @return Milvus store type
     * @since 0.1.15
     */
    @Override
    public String storeType() {
        return "milvus";
    }

    /**
     * Creates a Milvus retrieval vector store.
     *
     * @param config vector store configuration
     * @param indexType retrieval index type
     * @param options creation options
     * @return Milvus vector store
     * @since 0.1.15
     */
    @Override
    public VectorStore create(VectorStoreConfig config, String indexType, Map<String, Object> options) {
        Object clientOption = firstOption(options, List.of("milvus_client", "milvusClient", "client"));
        if (clientOption instanceof MilvusClientV2 client) {
            return new MilvusVectorStore(client, config, indexType, options);
        }
        String uri = stringOption(options, List.of("milvus_uri", "milvusUri", "uri"));
        if (uri == null || uri.isBlank()) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "milvus_uri or milvusClient is required for MilvusVectorStore");
        }
        String token = stringOption(options, List.of("milvus_token", "milvusToken", "token"));
        return new MilvusVectorStore(config, uri, token, indexType);
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
