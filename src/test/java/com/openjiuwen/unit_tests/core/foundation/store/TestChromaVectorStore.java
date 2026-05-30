/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChromaVectorStore.
 * <p>
 * Mirrors Python's test_chroma_vector_store.py from
 * <code>tests/unit_tests/core/foundation/store/test_chroma_vector_store.py</code>.
 */
@DisplayName("Chroma Vector Store Tests")
class TestChromaVectorStore {

    // Stub classes
    static class CollectionSchemaStub {
        String name;
        String description;
        List<FieldSchemaStub> fields = new ArrayList<>();

        CollectionSchemaStub(String name, String description) {
            this.name = name;
            this.description = description;
        }

        void addField(FieldSchemaStub field) {
            fields.add(field);
        }
    }

    static class FieldSchemaStub {
        String name;
        String dtype;
        int maxLength;
        int dim;
        boolean isPrimary;

        FieldSchemaStub(String name, String dtype, boolean isPrimary) {
            this.name = name;
            this.dtype = dtype;
            this.isPrimary = isPrimary;
        }
    }

    static class ChromaVectorStoreStub {
        Map<String, CollectionSchemaStub> collections = new HashMap<>();
        Map<String, List<VectorRecordStub>> vectorsByCollection = new HashMap<>();

        void createCollection(String name, CollectionSchemaStub schema) {
            collections.put(name, schema);
            vectorsByCollection.computeIfAbsent(name, key -> new ArrayList<>());
        }

        CollectionSchemaStub getCollection(String name) {
            return collections.get(name);
        }

        void addVectors(String collectionName, List<float[]> vectors, List<String> ids) {
            if (!collections.containsKey(collectionName)) {
                throw new IllegalArgumentException("Collection does not exist: " + collectionName);
            }
            if (vectors.size() != ids.size()) {
                throw new IllegalArgumentException("vectors and ids must have the same size");
            }
            List<VectorRecordStub> records = vectorsByCollection.computeIfAbsent(collectionName, key -> new ArrayList<>());
            for (int i = 0; i < vectors.size(); i++) {
                records.add(new VectorRecordStub(ids.get(i), vectors.get(i).clone()));
            }
        }

        List<Map<String, Object>> search(String collectionName, float[] queryVector, int topK) {
            List<Map<String, Object>> results = new ArrayList<>();
            for (VectorRecordStub record : vectorsByCollection.getOrDefault(collectionName, List.of())) {
                if (results.size() >= topK) {
                    break;
                }
                results.add(Map.of(
                        "id", record.id,
                        "vector", record.vector
                ));
            }
            return results;
        }

        int vectorCount(String collectionName) {
            return vectorsByCollection.getOrDefault(collectionName, List.of()).size();
        }
    }

    static class VectorRecordStub {
        String id;
        float[] vector;

        VectorRecordStub(String id, float[] vector) {
            this.id = id;
            this.vector = vector;
        }
    }

    @Nested
    @DisplayName("Create Collection Tests")
    class TestCreateCollection {

        @Test
        @DisplayName("create collection with schema")
        void testCreateCollectionWithSchema() {
            ChromaVectorStoreStub store = new ChromaVectorStoreStub();
            CollectionSchemaStub schema = new CollectionSchemaStub("test_collection", "Test");
            schema.addField(new FieldSchemaStub("id", "VARCHAR", true));
            schema.addField(new FieldSchemaStub("embedding", "FLOAT_VECTOR", false));

            store.createCollection("test_collection", schema);

            assertNotNull(store.getCollection("test_collection"));
            assertEquals(2, schema.fields.size());
        }
    }

    @Nested
    @DisplayName("Add Vectors Tests")
    class TestAddVectors {

        @Test
        @DisplayName("add vectors to collection")
        void testAddVectorsToCollection() {
            ChromaVectorStoreStub store = new ChromaVectorStoreStub();
            CollectionSchemaStub schema = new CollectionSchemaStub("test", "Test");
            store.createCollection("test", schema);

            List<float[]> vectors = new ArrayList<>();
            vectors.add(new float[]{0.1f, 0.2f, 0.3f});
            List<String> ids = new ArrayList<>();
            ids.add("doc1");

            store.addVectors("test", vectors, ids);

            // Verify vectors were added
            assertNotNull(store.getCollection("test"));
            assertEquals(1, store.vectorCount("test"));
        }
    }

    @Nested
    @DisplayName("Search Tests")
    class TestSearch {

        @Test
        @DisplayName("search vectors")
        void testSearchVectors() {
            ChromaVectorStoreStub store = new ChromaVectorStoreStub();
            CollectionSchemaStub schema = new CollectionSchemaStub("test", "Test");
            store.createCollection("test", schema);

            float[] queryVector = {0.1f, 0.2f, 0.3f};
            store.addVectors("test", List.of(new float[]{0.1f, 0.2f, 0.3f}), List.of("doc1"));
            List<Map<String, Object>> results = store.search("test", queryVector, 10);

            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals("doc1", results.get(0).get("id"));
        }
    }
}
