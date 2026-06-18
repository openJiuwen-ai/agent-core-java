/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.memory.migration.operation.OperationMetadata;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for vector store utility functions.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.store.vector.utils} in
 * {@code openjiuwen/core/foundation/store/vector/utils.py}.</p>
 */
class VectorStoreUtilsTest {

    @Test
    void normalizesDistanceAndSimilarityScores() {
        assertThat(VectorStoreUtils.convertL2Squared(1.0d)).isEqualTo(0.75d);
        assertThat(VectorStoreUtils.convertL2Squared(5.0d, 4.0d)).isEqualTo(0.0d);
        assertThatThrownBy(() -> VectorStoreUtils.convertL2Squared(1.0d, 0.0d))
                .isInstanceOf(ArithmeticException.class);

        assertThat(VectorStoreUtils.convertCosineSimilarity(-1.0d)).isEqualTo(0.0d);
        assertThat(VectorStoreUtils.convertCosineSimilarity(1.0d)).isEqualTo(1.0d);
        assertThat(VectorStoreUtils.convertCosineSimilarity(2.0d)).isEqualTo(1.5d);

        assertThat(VectorStoreUtils.convertCosineDistance(0.0d)).isEqualTo(1.0d);
        assertThat(VectorStoreUtils.convertCosineDistance(2.0d)).isEqualTo(0.0d);
        assertThat(VectorStoreUtils.convertCosineDistance(-1.0d)).isEqualTo(1.5d);

        assertThat(VectorStoreUtils.convertIpSimilarity(3.0d)).isEqualTo(1.0d);
        assertThat(VectorStoreUtils.convertIpSimilarity(-3.0d)).isEqualTo(0.0d);
        assertThat(VectorStoreUtils.convertIpSimilarity(0.0d)).isEqualTo(0.5d);

        assertThat(VectorStoreUtils.convertIpDistance(-1.0d)).isEqualTo(1.0d);
        assertThat(VectorStoreUtils.convertIpDistance(3.0d)).isEqualTo(0.0d);
        assertThat(VectorStoreUtils.convertIpDistance(1.0d)).isEqualTo(0.5d);
    }

    @Test
    void mapsStringTypesWithPythonNormalization() {
        assertThat(VectorStoreUtils.mapStringToVectorDataType(" string ")).isEqualTo(VectorDataType.VARCHAR);
        assertThat(VectorStoreUtils.mapStringToVectorDataType("INT64")).isEqualTo(VectorDataType.INT64);
        assertThat(VectorStoreUtils.mapStringToVectorDataType("float64")).isEqualTo(VectorDataType.DOUBLE);
        assertThat(VectorStoreUtils.mapStringToVectorDataType("float_vector")).isEqualTo(VectorDataType.FLOAT_VECTOR);

        assertThatThrownBy(() -> VectorStoreUtils.mapStringToVectorDataType("unknown"))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    @Test
    void computeNewSchemaAppliesOperationsInOrderWithoutMutatingOriginal() {
        CollectionSchema oldSchema = baseSchema();
        CollectionSchema newSchema = VectorStoreUtils.computeNewSchema(oldSchema, List.of(
                new AddScalarFieldOperation("category", "int", 1),
                new RenameScalarFieldOperation("title", "headline"),
                new UpdateScalarFieldTypeOperation("headline", "json"),
                new UpdateEmbeddingDimensionOperation("embedding", 5, null)
        ));

        assertThat(oldSchema.hasField("category")).isFalse();
        assertThat(oldSchema.hasField("title")).isTrue();
        assertThat(oldSchema.getField("embedding").getDim()).isEqualTo(3);

        assertThat(newSchema.getField("category").getDtype()).isEqualTo(VectorDataType.INT32);
        assertThat(newSchema.getField("category").getDefaultValue()).isEqualTo(1);
        assertThat(newSchema.hasField("title")).isFalse();
        assertThat(newSchema.getField("headline").getDtype()).isEqualTo(VectorDataType.JSON);
        assertThat(newSchema.getField("embedding").getDim()).isEqualTo(5);
    }

    @Test
    void computeNewSchemaRaisesPythonVectorSchemaErrors() {
        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(
                new RenameScalarFieldOperation("missing", "headline")
        ))).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(
                new RenameScalarFieldOperation("title", "id")
        ))).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(
                new UpdateScalarFieldTypeOperation("embedding", "string")
        ))).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(
                new UpdateEmbeddingDimensionOperation("title", 5, null)
        ))).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(
                new UpdateEmbeddingDimensionOperation("embedding", 0, null)
        ))).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(
                new UpdateScalarFieldTypeOperation("title", "vector")
        ))).isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);

        assertThatThrownBy(() -> VectorStoreUtils.computeNewSchema(baseSchema(), List.of(new UnsupportedOperation())))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    @Test
    void transformFunctionMutatesDocumentInOperationOrder() {
        Function<Map<String, Object>, Map<String, Object>> transform =
                VectorStoreUtils.buildTransformFuncForOperations(List.of(
                        new AddScalarFieldOperation("category", "string", "general"),
                        new RenameScalarFieldOperation("title", "headline"),
                        new UpdateScalarFieldTypeOperation("category", "json"),
                        new UpdateEmbeddingDimensionOperation("embedding", 2,
                                doc -> List.of(((String) doc.get("headline")).length() * 1.0d, 0.0d))
                ));
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("title", "abc");
        doc.put("embedding", List.of(1.0d, 2.0d, 3.0d));

        Map<String, Object> transformed = transform.apply(doc);

        assertThat(transformed).isSameAs(doc);
        assertThat(doc).doesNotContainKey("title");
        assertThat(doc).containsEntry("headline", "abc");
        assertThat(doc).containsEntry("category", "general");
        assertThat(doc.get("embedding")).isEqualTo(List.of(3.0d, 0.0d));
    }

    @Test
    void transformFunctionUsesDefaultZeroVectorAndChecksLength() {
        Function<Map<String, Object>, Map<String, Object>> defaultTransform =
                VectorStoreUtils.buildTransformFuncForOperations(List.of(
                        new UpdateEmbeddingDimensionOperation("embedding", 3, null)
                ));
        Map<String, Object> doc = new LinkedHashMap<>(Map.of("embedding", List.of(9.0d)));

        assertThat(defaultTransform.apply(doc).get("embedding")).isEqualTo(List.of(0.0d, 0.0d, 0.0d));

        Function<Map<String, Object>, Map<String, Object>> badTransform =
                VectorStoreUtils.buildTransformFuncForOperations(List.of(
                        new UpdateEmbeddingDimensionOperation("embedding", 3, ignored -> List.of(1.0d, 2.0d))
                ));

        assertThatThrownBy(() -> badTransform.apply(new LinkedHashMap<>(Map.of("embedding", List.of(9.0d)))))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.STORE_VECTOR_SCHEMA_INVALID);
    }

    private CollectionSchema baseSchema() {
        return CollectionSchema.fromFields(List.of(
                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null, null, null, null, null),
                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, 65535, 3,
                        null, null, null, null),
                new FieldSchema("title", VectorDataType.VARCHAR, false, false, 65535, null, null, null, null, null)
        ), "docs", true);
    }

    private abstract static class TestOperation extends BaseOperation {
        private TestOperation() {
            super(new OperationMetadata(2, "test"));
        }
    }

    private static final class AddScalarFieldOperation extends TestOperation {
        private final String fieldName;
        private final String fieldType;
        private final Object defaultValue;

        private AddScalarFieldOperation(String fieldName, String fieldType, Object defaultValue) {
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

    private static final class RenameScalarFieldOperation extends TestOperation {
        private final String oldFieldName;
        private final String newFieldName;

        private RenameScalarFieldOperation(String oldFieldName, String newFieldName) {
            this.oldFieldName = oldFieldName;
            this.newFieldName = newFieldName;
        }

        public String getOldFieldName() {
            return oldFieldName;
        }

        public String getNewFieldName() {
            return newFieldName;
        }
    }

    private static final class UpdateScalarFieldTypeOperation extends TestOperation {
        private final String fieldName;
        private final String newFieldType;

        private UpdateScalarFieldTypeOperation(String fieldName, String newFieldType) {
            this.fieldName = fieldName;
            this.newFieldType = newFieldType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getNewFieldType() {
            return newFieldType;
        }
    }

    private static final class UpdateEmbeddingDimensionOperation extends TestOperation {
        private final String fieldName;
        private final int newDimension;
        private final Function<Map<String, Object>, List<Double>> recomputeEmbeddingFunc;

        private UpdateEmbeddingDimensionOperation(
                String fieldName,
                int newDimension,
                Function<Map<String, Object>, List<Double>> recomputeEmbeddingFunc
        ) {
            this.fieldName = fieldName;
            this.newDimension = newDimension;
            this.recomputeEmbeddingFunc = recomputeEmbeddingFunc;
        }

        public String getFieldName() {
            return fieldName;
        }

        public int getNewDimension() {
            return newDimension;
        }

        public Function<Map<String, Object>, List<Double>> getRecomputeEmbeddingFunc() {
            return recomputeEmbeddingFunc;
        }
    }

    private static final class UnsupportedOperation extends TestOperation {
    }
}
