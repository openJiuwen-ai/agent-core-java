/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */

package com.openjiuwen.spi.store.vector;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class defining a unified interface for vector storage.
 * <p>
 * Mirrors Python's {@code BaseVectorStore} ABC.
 * Synchronous methods (run on virtual threads in practice).
 */
public abstract class BaseVectorStore {

    /**
     * Create a new collection with the specified schema.
     *
     * @param collectionName name of the collection
     * @param schema         collection schema (CollectionSchema or Map)
     * @param kwargs         additional parameters
     */
    public abstract void createCollection(String collectionName, Object schema,
                                          Map<String, Object> kwargs) throws Exception;

    /**
     * Delete a collection by name.
     */
    public abstract void deleteCollection(String collectionName,
                                          Map<String, Object> kwargs) throws Exception;

    /**
     * Check if a collection exists.
     */
    public abstract boolean collectionExists(String collectionName,
                                             Map<String, Object> kwargs) throws Exception;

    /**
     * Get the schema of a collection.
     */
    public abstract CollectionSchema getSchema(String collectionName,
                                               Map<String, Object> kwargs) throws Exception;

    /**
     * Add documents to a collection.
     *
     * @param collectionName target collection
     * @param docs           list of documents (maps with field values + embeddings)
     * @param kwargs         additional parameters
     */
    public abstract void addDocs(String collectionName,
                                 List<Map<String, Object>> docs,
                                 Map<String, Object> kwargs) throws Exception;

    /**
     * Search for most relevant documents by vector similarity.
     *
     * @param collectionName collection to search
     * @param queryVector    query vector
     * @param vectorField    name of the vector field
     * @param topK           number of results
     * @param filters        optional scalar field filters
     * @param kwargs         additional parameters
     * @return list of search results
     */
    public abstract List<VectorSearchResult> search(String collectionName,
                                                    List<Float> queryVector,
                                                    String vectorField,
                                                    int topK,
                                                    Map<String, Object> filters,
                                                    Map<String, Object> kwargs) throws Exception;

    /**
     * Delete documents by their IDs.
     */
    public abstract void deleteDocsByIds(String collectionName,
                                         List<String> ids,
                                         Map<String, Object> kwargs) throws Exception;

    /**
     * Delete documents by scalar field filters.
     */
    public abstract void deleteDocsByFilters(String collectionName,
                                             Map<String, Object> filters,
                                             Map<String, Object> kwargs) throws Exception;

    /**
     * List all collection names in the vector store.
     *
     * @return list of collection names
     */
    public abstract List<String> listCollectionNames() throws Exception;

    /**
     * Upgrade the schema field for vector data migration.
     *
     * @param collectionName name of the collection
     * @param operations     list of migration operations
     */
    public abstract void updateSchema(String collectionName, List<?> operations) throws Exception;

    /**
     * Update the metadata of a collection.
     *
     * @param collectionName name of the collection
     * @param metadata       metadata key-value pairs to update
     */
    public abstract void updateCollectionMetadata(String collectionName,
                                                  Map<String, Object> metadata) throws Exception;

    /**
     * Get the metadata of a collection.
     *
     * @param collectionName name of the collection
     * @return metadata key-value pairs
     */
    public abstract Map<String, Object> getCollectionMetadata(String collectionName) throws Exception;
}
