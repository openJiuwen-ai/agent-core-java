/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity tests for Chroma vector store behavior.
 *
 * <p>Mirrors Python's {@code ChromaVectorStore} in
 * {@code openjiuwen/core/foundation/store/vector/chroma_vector_store.py}.</p>
 */
class ChromaVectorStoreTest {

    @Test
    void createAddSearchAndDeleteUsePythonFieldMapping() {
        ChromaVectorStore store = new ChromaVectorStore();
        CollectionSchema schema = schema();

        store.createCollection("docs", schema, Map.of("distance_metric", "cosine")).join();
        store.addDocs("docs", List.of(
                Map.of(
                        "doc_id", "a",
                        "embedding", List.of(1.0d, 0.0d),
                        "text", "alpha",
                        "tags", List.of("x", "y"),
                        "score", 3
                ),
                Map.of(
                        "doc_id", "b",
                        "embedding", List.of(0.0d, 1.0d),
                        "text", "beta",
                        "tags", List.of("z"),
                        "score", 4
                )
        ), Map.of("batch_size", 1)).join();

        List<VectorSearchResult> results = store.search(
                "docs",
                List.of(1.0d, 0.0d),
                "embedding",
                5,
                Map.of("score", 3),
                Map.of()
        ).join();

        assertEquals(1, results.size());
        assertEquals(1.0d, results.get(0).getScore());
        assertEquals("a", results.get(0).getFields().get("doc_id"));
        assertEquals("alpha", results.get(0).getFields().get("text"));
        assertIterableEquals(List.of("x", "y"), (List<?>) results.get(0).getFields().get("tags"));

        store.deleteDocsByIds("docs", List.of("a"), Map.of()).join();
        assertTrue(store.search("docs", List.of(1.0d, 0.0d), "embedding", 5, null, Map.of()).join()
                .stream()
                .noneMatch(result -> "a".equals(result.getFields().get("doc_id"))));

        store.deleteDocsByFilters("docs", Map.of("score", 4), Map.of()).join();
        assertTrue(store.search("docs", List.of(1.0d, 0.0d), "embedding", 5, null, Map.of()).join().isEmpty());
    }

    @Test
    void schemaAndMetadataRoundTrip() {
        ChromaVectorStore store = new ChromaVectorStore();

        assertFalse(store.collectionExists("docs", Map.of()).join());
        store.createCollection("docs", schema(), Map.of("distance_metric", "euclidean")).join();

        assertTrue(store.collectionExists("docs", Map.of()).join());
        assertIterableEquals(List.of("docs"), store.listCollectionNames().join());
        assertEquals("docs", store.getSchema("docs", Map.of()).join().getDescription());
        assertEquals("l2", store.getCollectionMetadata("docs").join().get("distance_metric"));
        assertEquals(0, store.getCollectionMetadata("docs").join().get("schema_version"));

        store.updateCollectionMetadata("docs", Map.of("schema_version", 2)).join();
        assertEquals(2, store.getCollectionMetadata("docs").join().get("schema_version"));
    }

    @Test
    void validatesRequiredSchemaAndDocumentFields() {
        ChromaVectorStore store = new ChromaVectorStore();

        BaseError noPrimary = assertBaseError(
                () -> store.createCollection("bad", schemaWithoutPrimary(), Map.of()).join());
        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, noPrimary.getStatus());

        BaseError noVector = assertBaseError(
                () -> store.createCollection("bad", schemaWithoutVector(), Map.of()).join());
        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, noVector.getStatus());

        store.createCollection("docs", schema(), Map.of()).join();
        BaseError missingVector = assertBaseError(
                () -> store.addDocs("docs", List.of(Map.of("doc_id", "a")), Map.of()).join());
        assertEquals(StatusCode.STORE_VECTOR_DOC_INVALID, missingVector.getStatus());
    }

    @Test
    void updateSchemaMigratesDocumentsWithAddFieldDefault() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("docs", schema(), Map.of()).join();
        store.addDocs("docs", List.of(Map.of(
                "doc_id", "a",
                "embedding", List.of(1.0d, 0.0d),
                "text", "alpha"
        )), Map.of()).join();

        store.updateSchema("docs", List.of(new AddScalarFieldOperation("category", "string", "general"))).join();

        List<Map<String, Object>> docs = store.getAllDocuments("docs").join();
        assertEquals(1, docs.size());
        assertEquals("general", docs.get(0).get("category"));
        assertTrue(store.getSchema("docs", Map.of()).join().hasField("category"));
    }

    @Test
    void invalidSchemaVersionRaisesBaseError() {
        ChromaVectorStore store = new ChromaVectorStore();
        store.createCollection("docs", schema(), Map.of()).join();

        BaseError error = assertBaseError(
                () -> store.updateCollectionMetadata("docs", Map.of("schema_version", -1)).join());

        assertEquals(StatusCode.STORE_VECTOR_SCHEMA_INVALID, error.getStatus());
    }

    @Test
    void missingCollectionRaisesVectorCollectionError() {
        ChromaVectorStore store = new ChromaVectorStore();

        BaseError error = assertBaseError(
                () -> store.getSchema("missing", Map.of()).join());

        assertEquals(StatusCode.STORE_VECTOR_COLLECTION_NOT_FOUND, error.getStatus());
        assertInstanceOf(Map.class, error.getParams());
    }

    private CollectionSchema schema() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("doc_id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 2,
                        null, null, null, null),
                new FieldSchema("text", VectorDataType.VARCHAR, false, false, 65535, null,
                        null, null, "docs", null)
        ), "docs", true);
    }

    private CollectionSchema schemaWithoutPrimary() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 2,
                        null, null, null, null)
        ), "bad", true);
    }

    private CollectionSchema schemaWithoutVector() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("doc_id", VectorDataType.VARCHAR, true, false, 256, null,
                        null, null, null, null)
        ), "bad", true);
    }

    private BaseError assertBaseError(Runnable action) {
        CompletionException exception = assertThrows(CompletionException.class, action::run);
        assertInstanceOf(BaseError.class, exception.getCause());
        return (BaseError) exception.getCause();
    }

    private static final class AddScalarFieldOperation extends BaseOperation {
        private final String fieldName;
        private final String fieldType;
        private final Object defaultValue;

        private AddScalarFieldOperation(String fieldName, String fieldType, Object defaultValue) {
            super(new OperationMetadata(2, "add scalar"));
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.defaultValue = defaultValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getFieldType() {
            return fieldType;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }
    }
}
