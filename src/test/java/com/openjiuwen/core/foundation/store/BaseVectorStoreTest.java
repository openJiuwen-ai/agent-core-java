/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseVectorStoreTest {

    @Test
    void fieldSchemaRequiresPositiveVectorDim() {
        assertThatThrownBy(() -> new FieldSchema(
                "embedding",
                VectorDataType.FLOAT_VECTOR,
                false,
                false,
                65535,
                null,
                null,
                null,
                null,
                null
        )).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> new FieldSchema(
                "embedding",
                VectorDataType.FLOAT_VECTOR,
                false,
                false,
                65535,
                0,
                null,
                null,
                null,
                null
        )).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    @Test
    void fieldSchemaAndCollectionSchemaRoundTripThroughDictHelpers() {
        FieldSchema idField = new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null, null, null, null, null);
        FieldSchema embeddingField = new FieldSchema(
                "embedding",
                VectorDataType.FLOAT_VECTOR,
                false,
                false,
                65535,
                768,
                null,
                null,
                null,
                null
        );
        CollectionSchema schema = CollectionSchema.fromFields(
                List.of(idField, embeddingField.toDict()),
                "docs",
                true
        );

        Map<String, Object> fieldDict = embeddingField.toDict();
        CollectionSchema restored = CollectionSchema.fromDict(schema.toDict());

        assertThat(fieldDict).containsEntry("type", "FLOAT_VECTOR").containsEntry("dim", 768);
        assertThat(schema.getPrimaryKeyField().getName()).isEqualTo("id");
        assertThat(schema.getVectorFields()).extracting(FieldSchema::getName).containsExactly("embedding");
        assertThat(restored.getFields()).hasSize(2);
        assertThat(restored.isEnableDynamicField()).isTrue();
    }

    @Test
    void collectionSchemaRejectsDuplicatePrimaryOrFieldNames() {
        CollectionSchema schema = new CollectionSchema();
        schema.addField(new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null, null, null, null, null));

        assertThatThrownBy(() -> schema.addField(
                new FieldSchema("id", VectorDataType.VARCHAR, false, false, 256, null, null, null, null, null)
        )).isInstanceOf(BaseError.class);

        assertThatThrownBy(() -> schema.addField(
                new FieldSchema("other_id", VectorDataType.VARCHAR, true, false, 256, null, null, null, null, null)
        )).isInstanceOf(BaseError.class);
    }

    @Test
    void subclassExposesAsyncVectorStoreContract() {
        BaseOperation operation = new BaseOperation(new OperationMetadata(2, "schema")) {
        };
        BaseVectorStore store = new BaseVectorStore() {
            @Override
            public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(new CollectionSchema());
            }

            @Override
            public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docs, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<VectorSearchResult>> search(
                    String collectionName,
                    List<Double> queryVector,
                    String vectorField,
                    int topK,
                    Map<String, Object> filters,
                    Map<String, Object> kwargs
            ) {
                return CompletableFuture.completedFuture(List.of(new VectorSearchResult(0.9d, Map.of("id", "doc-1"))));
            }

            @Override
            public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> deleteDocsByFilters(String collectionName, Map<String, Object> filters, Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<List<String>> listCollectionNames() {
                return CompletableFuture.completedFuture(List.of("docs"));
            }

            @Override
            public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
                return CompletableFuture.completedFuture(Map.of("schema_version", 2));
            }
        };

        assertThat(store.collectionExists("docs", Map.of()).join()).isTrue();
        assertThat(store.listCollectionNames().join()).containsExactly("docs");
        assertThat(store.search("docs", List.of(0.1d), "embedding", 5, Map.of(), Map.of()).join())
                .singleElement()
                .extracting(VectorSearchResult::getScore)
                .isEqualTo(0.9d);
        assertThat(operation.getSchemaVersion()).isEqualTo(2);
    }
}
