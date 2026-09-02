/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.provider.VectorStoreProvider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating vector stores from configuration.
 * 
 * @since 0.1.7
 */
public final class VectorStoreFactory {
    private static final Map<String, VectorStoreProvider> PROVIDERS = new ConcurrentHashMap<>();

    static {
        for (VectorStoreProvider provider : ServiceLoader.load(VectorStoreProvider.class)) {
            PROVIDERS.putIfAbsent(provider.storeType().toLowerCase(Locale.ROOT), provider);
        }
    }

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
        VectorStoreProvider provider = PROVIDERS.get(config.getStoreType().name().toLowerCase(Locale.ROOT));
        if (provider != null) {
            return provider.create(config, indexType, options == null ? Map.of() : options);
        }
        return switch (config.getStoreType()) {
            case CHROMA -> new ChromaVectorStore(config, indexType);
            case ELASTICSEARCH -> new ElasticsearchVectorStore(config, indexType);
            case MILVUS, PGVECTOR -> throw RetrievalExceptions.error(StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "No vector store provider registered for type: " + config.getStoreType());
        };
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
