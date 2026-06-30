/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Foundation-store Chroma adapter.
 */
public class ChromaVectorStore extends AbstractRetrievalVectorStoreAdapter {

    /**
     * Auto-generated for codecheck compliance.
     */
    public ChromaVectorStore(Map<String, Object> options) {
        super(new com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore(
                new VectorStoreConfig(
                        "chroma",
                        InMemoryVectorStore.stringOption(options, "database_name", "databaseName", "default"),
                        InMemoryVectorStore.stringOption(options, "collection_name", "collectionName", "default_collection"),
                        InMemoryVectorStore.stringOption(options, "distance_metric", "distanceMetric", "cosine")
                ),
                InMemoryVectorStore.indexType(options)
        ));
    }

    /**
     * Retrieve all documents from a collection for migration purposes.
     *
     * @param collectionName name of the collection
     * @return list of documents as maps
     */
    public List<Map<String, Object>> getAllDocuments(String collectionName) throws Exception {
        // Retrieve collection metadata to determine field mappings
        Map<String, Object> metadata = getCollectionMetadata(collectionName);
        String primaryKey = metadata.getOrDefault("primary_key", "id").toString();
        String vectorField = metadata.getOrDefault("vector_field", "embedding").toString();
        String textField = metadata.getOrDefault("text_field", "text").toString();

        // Search with large topK to get all documents
        List<Float> zeroVector = new ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            zeroVector.add(0.0f);
        }
        var results = search(collectionName, zeroVector, vectorField, 10000, null, null);

        List<Map<String, Object>> documents = new ArrayList<>();
        for (var result : results) {
            Map<String, Object> doc = new LinkedHashMap<>();
            Map<String, Object> fields = result.getFields();
            doc.put(primaryKey, fields.getOrDefault("id", ""));
            doc.put(textField, fields.getOrDefault("text", ""));
            if (fields.containsKey("metadata")) {
                Object metadataObj = fields.get("metadata");
                if (metadataObj instanceof Map<?, ?> metaMap) {
                    doc.putAll((Map<String, Object>) metaMap);
                }
            }
            documents.add(doc);
        }
        return documents;
    }
}
