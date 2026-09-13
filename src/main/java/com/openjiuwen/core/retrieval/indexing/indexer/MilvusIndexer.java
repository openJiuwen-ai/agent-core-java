/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.indexer;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.BaseCallback;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Milvus-backed index manager for retrieval.
 * 
 * @since 0.1.7
 */
public class MilvusIndexer implements Indexer {
    private final MilvusVectorStore vectorStore;
    private final MilvusClientV2 client;
    private final boolean ownsStore;

    /**
     * MilvusIndexer.
     * 
     * @param vectorStore vectorStore
     * @since 0.1.7
     */
    public MilvusIndexer(MilvusVectorStore vectorStore) {
        this(vectorStore, false);
    }

    /**
     * MilvusIndexer.
     * 
     * @param config config
     * @param milvusUri milvusUri
     * @param indexType indexType
     * @since 0.1.7
     */
    public MilvusIndexer(VectorStoreConfig config, String milvusUri, String indexType) {
        this(new MilvusVectorStore(config, milvusUri, indexType), true);
    }

    /**
     * MilvusIndexer.
     * 
     * @param config config
     * @param milvusUri milvusUri
     * @param milvusToken milvusToken
     * @param indexType indexType
     * @since 0.1.7
     */
    public MilvusIndexer(VectorStoreConfig config, String milvusUri, String milvusToken, String indexType) {
        this(new MilvusVectorStore(config, milvusUri, milvusToken, indexType), true);
    }

    /**
     * MilvusIndexer.
     * 
     * @param vectorStore vectorStore
     * @param ownsStore ownsStore
     * @since 0.1.7
     */
    private MilvusIndexer(MilvusVectorStore vectorStore, boolean ownsStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
        this.client = vectorStore.getClient();
        this.ownsStore = ownsStore;
    }

    /**
     * buildIndex.
     * 
     * @param chunks chunks
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean buildIndex(List<TextChunk> chunks, IndexConfig config, Embedding embedModel,
            Map<String, Object> options) {
        List<TextChunk> safeChunks = chunks == null ? List.of() : chunks;
        if (safeChunks.isEmpty()) {
            return true;
        }
        ensureCollection(config.getIndexName(), config, embedModel);
        VectorStore scopedStore = vectorStore.withCollection(config.getIndexName());
        List<String> docIds =
            safeChunks.stream().map(TextChunk::getDocId).filter(id -> id != null && !id.isBlank()).distinct().toList();
        if (!docIds.isEmpty()) {
            List<SearchResult> existing = scopedStore.queryByFilters(Map.of(getDocIdField(), docIds), docIds.size());
            if (!existing.isEmpty()) {
                LinkedHashSet<String> duplicateDocIds = new LinkedHashSet<>();
                for (SearchResult result : existing) {
                    Object value = result.getMetadata().get("doc_id");
                    if (value != null) {
                        duplicateDocIds.add(String.valueOf(value));
                    }
                }
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_ADD_DOC_RUNTIME_ERROR,
                        "some documents with same doc_id already exist: " + duplicateDocIds);
            }
        }
        scopedStore.add(toDocs(safeChunks, config, embedModel, options), 128, options);
        return true;
    }

    /**
     * updateIndex.
     * 
     * @param chunks chunks
     * @param docId docId
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean updateIndex(List<TextChunk> chunks, String docId, IndexConfig config, Embedding embedModel,
            Map<String, Object> options) {
        deleteIndex(docId, config.getIndexName(), options);
        return buildIndex(chunks, config, embedModel, options);
    }

    /**
     * deleteIndex.
     * 
     * @param docId docId
     * @param indexName indexName
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean deleteIndex(String docId, String indexName, Map<String, Object> options) {
        VectorStore scopedStore = vectorStore.withCollection(indexName);
        return scopedStore.delete(null, Map.of(getDocIdField(), docId), options);
    }

    /**
     * indexExists.
     * 
     * @param indexName indexName
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean indexExists(String indexName) {
        return vectorStore.tableExists(indexName);
    }

    /**
     * getIndexInfo.
     * 
     * @param indexName indexName
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getIndexInfo(String indexName) {
        Map<String, Object> info = new LinkedHashMap<>();
        boolean exists = indexExists(indexName);
        info.put("isExists", exists);
        info.put("index_name", indexName);
        if (!exists) {
            info.put("count", 0L);
            return info;
        }
        DescribeCollectionReq.DescribeCollectionReqBuilder builder =
            DescribeCollectionReq.builder().collectionName(indexName);
        if (getDatabaseName() != null && !getDatabaseName().isBlank()) {
            builder.databaseName(getDatabaseName());
        }
        DescribeCollectionResp collection = client.describeCollection(builder.build());
        info.put("count", vectorStore.count(indexName));
        info.put("field_names", collection.getFieldNames());
        info.put("vector_fields", collection.getVectorFieldNames());
        return info;
    }

    /**
     * close.
     * 
     * @since 0.1.7
     */
    @Override
    public void close() {
        if (ownsStore) {
            vectorStore.close();
        }
    }

    /**
     * getDatabaseName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDatabaseName() {
        return vectorStore.getDatabaseName();
    }

    /**
     * getDistanceMetric.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDistanceMetric() {
        return vectorStore.getDistanceMetric();
    }

    /**
     * getIndexType.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getIndexType() {
        return vectorStore.getIndexType();
    }

    /**
     * getTextField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getTextField() {
        return vectorStore.getTextField();
    }

    /**
     * getVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getVectorField() {
        return vectorStore.getVectorField();
    }

    /**
     * getSparseVectorField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getSparseVectorField() {
        return vectorStore.getSparseVectorField();
    }

    /**
     * getMetadataField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getMetadataField() {
        return vectorStore.getMetadataField();
    }

    /**
     * getDocIdField.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDocIdField() {
        return vectorStore.getDocIdField();
    }

    /**
     * ensureCollection.
     * 
     * @param collectionName collectionName
     * @param config config
     * @param embedModel embedModel
     * @since 0.1.7
     */
    private void ensureCollection(String collectionName, IndexConfig config, Embedding embedModel) {
        Integer dimension = "bm25".equals(config.getIndexType()) ? null : resolveDimension(embedModel);
        vectorStore.ensureCollection(collectionName, config.getIndexType(), dimension, Map.of());
    }

    /**
     * resolveDimension.
     * 
     * @param embedModel embedModel
     * @return the result
     * @since 0.1.7
     */
    private int resolveDimension(Embedding embedModel) {
        if (embedModel == null) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND,
                    "embed_model is required for vector or hybrid index");
        }
        int dimension = embedModel.getDimension();
        if (dimension > 0) {
            return dimension;
        }
        List<Float> probe = embedModel.embedQuery("dimension-probe");
        if (probe != null && !probe.isEmpty()) {
            return probe.size();
        }
        throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_DIMENSION_NOT_FOUND,
                "dimension is required for vector or hybrid index");
    }

    /**
     * toDocs.
     * 
     * @param chunks chunks
     * @param config config
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private List<Map<String, Object>> toDocs(List<TextChunk> chunks, IndexConfig config, Embedding embedModel,
            Map<String, Object> options) {
        List<List<Float>> embeddings = null;
        if (!"bm25".equals(config.getIndexType())) {
            if (embedModel == null) {
                throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_EMBED_MODEL_NOT_FOUND,
                        "embed_model is required to build vector or hybrid index");
            }
            embeddings = embedBatches(chunks, embedModel, options);
        }

        List<Map<String, Object>> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
            metadata.putIfAbsent("doc_id", chunk.getDocId());
            metadata.putIfAbsent("chunk_id", chunk.getId());
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("chunk_id", chunk.getId());
            doc.put(getDocIdField(), chunk.getDocId());
            doc.put(getTextField(), chunk.getText());
            doc.put(getMetadataField(), metadata);
            if (embeddings != null && i < embeddings.size()) {
                doc.put(getVectorField(), embeddings.get(i));
            }
            docs.add(doc);
        }
        return docs;
    }

    /**
     * embedBatches.
     * 
     * @param chunks chunks
     * @param embedModel embedModel
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private List<List<Float>> embedBatches(List<TextChunk> chunks, Embedding embedModel, Map<String, Object> options) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        int batchSize = Math.max(1, embedModel.getMaxBatchSize());
        BaseCallback callback =
            options != null && options.get("callback") instanceof BaseCallback baseCallback ? baseCallback : null;
        List<List<Float>> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<String> texts = chunks.subList(start, end).stream().map(TextChunk::getText).toList();
            embeddings.addAll(embedModel.embedDocuments(texts, batchSize));
            if (callback != null) {
                callback.onBatch(start, end, texts);
            }
        }
        return embeddings;
    }
}
