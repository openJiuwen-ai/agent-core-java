/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * Foundation-store Milvus adapter.
 *
 * <p>Mirrors Python's {@code MilvusVectorStore} in
 * {@code openjiuwen.core.foundation.store.vector.milvus_vector_store}.</p>
 */
public class MilvusVectorStore extends AbstractRetrievalVectorStoreAdapter {

    public MilvusVectorStore(Map<String, Object> options) {
        super(new LazyMilvusRetrievalStore(options));
    }

    private static final class LazyMilvusRetrievalStore implements VectorStore {
        private final String milvusUri;
        private final String milvusToken;
        private final String indexType;
        private final String textField = "text";
        private final String vectorField = "vector";
        private final String sparseVectorField = "sparse_vector";
        private final String metadataField = "metadata";
        private final String docIdField = "doc_id";

        private VectorStoreConfig config;
        private com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore delegate;

        private LazyMilvusRetrievalStore(Map<String, Object> options) {
            this(
                    new VectorStoreConfig(
                            "milvus",
                            InMemoryVectorStore.stringOption(options, "database_name", "databaseName", "default"),
                            InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                            InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")),
                    InMemoryVectorStore.stringOption(options, "milvus_uri", "milvusUri", ""),
                    options != null && (options.containsKey("milvus_token") || options.containsKey("milvusToken"))
                            ? InMemoryVectorStore.stringOption(options, "milvus_token", "milvusToken", null)
                            : null,
                    InMemoryVectorStore.indexType(options));
        }

        private LazyMilvusRetrievalStore(VectorStoreConfig config,
                                         String milvusUri,
                                         String milvusToken,
                                         String indexType) {
            this.config = config;
            this.milvusUri = milvusUri == null ? "" : milvusUri;
            this.milvusToken = milvusToken;
            this.indexType = indexType;
        }

        private com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore delegate() {
            if (delegate == null) {
                delegate = new com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore(
                        config,
                        milvusUri,
                        milvusToken,
                        indexType);
            }
            return delegate;
        }

        @Override
        public String getCollectionName() {
            return delegate != null ? delegate.getCollectionName() : config.getCollectionName();
        }

        @Override
        public void setCollectionName(String collectionName) {
            config.setCollectionName(collectionName);
            if (delegate != null) {
                delegate.setCollectionName(collectionName);
            }
        }

        @Override
        public VectorStore withCollection(String collectionName) {
            if (delegate != null) {
                return delegate.withCollection(collectionName);
            }
            VectorStoreConfig scopedConfig = new VectorStoreConfig(
                    "milvus",
                    config.getDatabaseName(),
                    collectionName,
                    config.getDistanceMetric());
            return new LazyMilvusRetrievalStore(scopedConfig, milvusUri, milvusToken, indexType);
        }

        @Override
        public void checkVectorField() {
            delegate().checkVectorField();
        }

        @Override
        public void ensureCollection(String collectionName,
                                     String indexType,
                                     Integer dimension,
                                     Map<String, Object> options) {
            delegate().ensureCollection(collectionName, indexType, dimension, options);
        }

        @Override
        public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
            delegate().add(data, batchSize, options);
        }

        @Override
        public List<SearchResult> search(List<Float> queryVector,
                                         int topK,
                                         Map<String, Object> filters,
                                         Map<String, Object> options) {
            return delegate().search(queryVector, topK, filters, options);
        }

        @Override
        public List<SearchResult> sparseSearch(String queryText,
                                               int topK,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            return delegate().sparseSearch(queryText, topK, filters, options);
        }

        @Override
        public List<SearchResult> hybridSearch(String queryText,
                                               List<Float> queryVector,
                                               int topK,
                                               double alpha,
                                               Map<String, Object> filters,
                                               Map<String, Object> options) {
            return delegate().hybridSearch(queryText, queryVector, topK, alpha, filters, options);
        }

        @Override
        public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
            return delegate().delete(ids, filterExpr, options);
        }

        @Override
        public boolean tableExists(String tableName) {
            return delegate().tableExists(tableName);
        }

        @Override
        public void deleteTable(String tableName) {
            delegate().deleteTable(tableName);
        }

        @Override
        public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
            return delegate().queryByFilters(filters, limit);
        }

        @Override
        public long count(String tableName) {
            return delegate().count(tableName);
        }

        @Override
        public String getDatabaseName() {
            return config.getDatabaseName();
        }

        @Override
        public String getDistanceMetric() {
            return config.getDistanceMetric();
        }

        @Override
        public String getIndexType() {
            return indexType;
        }

        @Override
        public String getTextField() {
            return textField;
        }

        @Override
        public String getVectorField() {
            return vectorField;
        }

        @Override
        public String getSparseVectorField() {
            return sparseVectorField;
        }

        @Override
        public String getMetadataField() {
            return metadataField;
        }

        @Override
        public String getDocIdField() {
            return docIdField;
        }

        @Override
        public void close() {
            if (delegate != null) {
                delegate.close();
                delegate = null;
            }
        }
    }
}
