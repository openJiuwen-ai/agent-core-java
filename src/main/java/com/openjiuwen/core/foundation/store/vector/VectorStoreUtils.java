/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static com.openjiuwen.core.common.exception.ErrorHelper.buildError;

/**
 * Mirrors Python's {@code openjiuwen.core.foundation.store.vector.utils} module in
 * {@code openjiuwen/core/foundation/store/vector/utils.py}.
 */
public final class VectorStoreUtils {

    private static final String ERROR_MSG = "error_msg";
    private static final Map<String, VectorDataType> TYPE_MAPPING = buildTypeMapping();

    private VectorStoreUtils() {
    }

    public static double convertL2Squared(double rawScore, double maxDist) {
        if (maxDist == 0.0d) {
            throw new ArithmeticException("float division by zero");
        }
        return pythonMax(0.0d, (maxDist - rawScore) / maxDist);
    }

    public static double convertL2Squared(double rawScore) {
        return convertL2Squared(rawScore, 4.0d);
    }

    public static double convertCosineSimilarity(double rawScore) {
        return (rawScore + 1.0d) / 2.0d;
    }

    public static double convertCosineDistance(double rawScore) {
        return (2.0d - rawScore) / 2.0d;
    }

    public static double convertIpSimilarity(double rawScore) {
        return pythonMax(0.0d, pythonMin(1.0d, (rawScore + 1.0d) / 2.0d));
    }

    public static double convertIpDistance(double rawScore) {
        return pythonMax(0.0d, pythonMin(1.0d, (2.0d - rawScore) / 2.0d));
    }

    static VectorDataType mapStringToVectorDataType(String typeStr) {
        String normalizedType = typeStr.toLowerCase(Locale.ROOT).strip();
        VectorDataType dtype = TYPE_MAPPING.get(normalizedType);
        if (dtype == null) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Unknown type string: '" + typeStr + "'. Supported types: " + TYPE_MAPPING.keySet()
            );
        }
        return dtype;
    }

    public static CollectionSchema computeNewSchema(
            CollectionSchema oldSchema,
            List<? extends BaseOperation> operations
    ) {
        CollectionSchema newSchema = CollectionSchema.fromDict(oldSchema.toDict());

        for (BaseOperation operation : operations) {
            String kind = operationKind(operation);
            if ("AddScalarFieldOperation".equals(kind)) {
                newSchema = computeSchemaAddField(newSchema, operation);
            } else if ("RenameScalarFieldOperation".equals(kind)) {
                newSchema = computeSchemaRenameField(newSchema, operation);
            } else if ("UpdateScalarFieldTypeOperation".equals(kind)) {
                newSchema = computeSchemaUpdateFieldType(newSchema, operation);
            } else if ("UpdateEmbeddingDimensionOperation".equals(kind)) {
                newSchema = computeSchemaUpdateVectorDim(newSchema, operation);
            } else {
                throw buildError(
                        StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        ERROR_MSG,
                        "Unsupported operation type: " + kind
                );
            }
        }

        return newSchema;
    }

    public static Function<Map<String, Object>, Map<String, Object>> buildTransformFuncForOperations(
            List<? extends BaseOperation> operations
    ) {
        return doc -> {
            Map<String, Object> currentDoc = doc;
            for (BaseOperation operation : operations) {
                currentDoc = applyOperationToDoc(currentDoc, operation);
            }
            return currentDoc;
        };
    }

    private static CollectionSchema computeSchemaAddField(CollectionSchema schema, BaseOperation operation) {
        CollectionSchema newSchema = CollectionSchema.fromDict(schema.toDict());
        FieldSchema fieldSchema = new FieldSchema(
                stringProperty(operation, "fieldName", "field_name"),
                mapStringToVectorDataType(stringProperty(operation, "fieldType", "field_type")),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                property(operation, "defaultValue", "default_value")
        );
        newSchema.addField(fieldSchema);
        return newSchema;
    }

    private static CollectionSchema computeSchemaRenameField(CollectionSchema schema, BaseOperation operation) {
        String oldFieldName = stringProperty(operation, "oldFieldName", "old_field_name");
        String newFieldName = stringProperty(operation, "newFieldName", "new_field_name");
        if (oldFieldName.equals(newFieldName)) {
            return schema;
        }

        CollectionSchema newSchema = CollectionSchema.fromDict(schema.toDict());
        if (!newSchema.hasField(oldFieldName)) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Old field '" + oldFieldName + "' does not exist"
            );
        }
        if (newSchema.hasField(newFieldName)) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "New field '" + newFieldName + "' already exists"
            );
        }
        newSchema.getField(oldFieldName).setName(newFieldName);
        return newSchema;
    }

    private static CollectionSchema computeSchemaUpdateFieldType(CollectionSchema schema, BaseOperation operation) {
        String fieldName = stringProperty(operation, "fieldName", "field_name");
        CollectionSchema newSchema = CollectionSchema.fromDict(schema.toDict());
        FieldSchema field = newSchema.getField(fieldName);
        if (field == null) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Field '" + fieldName + "' does not exist"
            );
        }
        if (field.getDtype() == VectorDataType.FLOAT_VECTOR) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Cannot update type of vector field '" + fieldName + "'"
            );
        }
        field.setDtype(mapStringToVectorDataType(stringProperty(operation, "newFieldType", "new_field_type")));
        return CollectionSchema.fromDict(newSchema.toDict());
    }

    private static CollectionSchema computeSchemaUpdateVectorDim(CollectionSchema schema, BaseOperation operation) {
        String fieldName = stringProperty(operation, "fieldName", "field_name");
        CollectionSchema newSchema = CollectionSchema.fromDict(schema.toDict());
        FieldSchema field = newSchema.getField(fieldName);
        if (field == null) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Field '" + fieldName + "' does not exist"
            );
        }
        if (field.getDtype() != VectorDataType.FLOAT_VECTOR) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Field '" + fieldName + "' is not a vector field"
            );
        }
        field.setDim(intProperty(operation, "newDimension", "new_dimension"));
        return CollectionSchema.fromDict(newSchema.toDict());
    }

    private static Map<String, Object> applyOperationToDoc(Map<String, Object> doc, BaseOperation operation) {
        String kind = operationKind(operation);
        if ("AddScalarFieldOperation".equals(kind)) {
            String fieldName = stringProperty(operation, "fieldName", "field_name");
            Object defaultValue = property(operation, "defaultValue", "default_value");
            if (!doc.containsKey(fieldName) && defaultValue != null) {
                doc.put(fieldName, defaultValue);
            }
        } else if ("RenameScalarFieldOperation".equals(kind)) {
            String oldFieldName = stringProperty(operation, "oldFieldName", "old_field_name");
            String newFieldName = stringProperty(operation, "newFieldName", "new_field_name");
            if (doc.containsKey(oldFieldName)) {
                doc.put(newFieldName, doc.remove(oldFieldName));
            }
        } else if ("UpdateEmbeddingDimensionOperation".equals(kind)) {
            applyEmbeddingDimensionUpdate(doc, operation);
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static void applyEmbeddingDimensionUpdate(Map<String, Object> doc, BaseOperation operation) {
        int newDimension = intProperty(operation, "newDimension", "new_dimension");
        Object recomputeFunc = property(operation, "recomputeEmbeddingFunc", "recompute_embedding_func");
        Object newVector;
        if (recomputeFunc == null) {
            newVector = zeroVector(newDimension);
        } else if (recomputeFunc instanceof Function<?, ?> function) {
            newVector = ((Function<Map<String, Object>, ?>) function).apply(doc);
        } else {
            throw new IllegalArgumentException("recompute_embedding_func must be a java.util.function.Function");
        }

        if (sequenceLength(newVector) != newDimension) {
            throw buildError(
                    StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    ERROR_MSG,
                    "Generated vector length " + sequenceLength(newVector) + " does not match new_dim " + newDimension
            );
        }
        doc.put(stringProperty(operation, "fieldName", "field_name"), newVector);
    }

    private static Object property(Object target, String camelName, String snakeName) {
        for (String name : List.of("get" + capitalize(camelName), camelName, snakeName)) {
            Object value = invokeNoArgMethod(target, name);
            if (value != MissingValue.INSTANCE) {
                return value;
            }
        }
        for (String name : List.of(camelName, snakeName)) {
            Object value = readField(target, name);
            if (value != MissingValue.INSTANCE) {
                return value;
            }
        }
        return null;
    }

    private static Object invokeNoArgMethod(Object target, String methodName) {
        if (target == null) {
            return MissingValue.INSTANCE;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return MissingValue.INSTANCE;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return MissingValue.INSTANCE;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return MissingValue.INSTANCE;
    }

    private static String stringProperty(Object target, String camelName, String snakeName) {
        Object value = property(target, camelName, snakeName);
        return value == null ? "" : String.valueOf(value);
    }

    private static int intProperty(Object target, String camelName, String snakeName) {
        Object value = property(target, camelName, snakeName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static int sequenceLength(Object sequence) {
        if (sequence instanceof List<?> list) {
            return list.size();
        }
        if (sequence != null && sequence.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(sequence);
        }
        throw new IllegalArgumentException("Generated vector must be a list or array");
    }

    private static List<Double> zeroVector(int dimension) {
        List<Double> vector = new ArrayList<>();
        for (int index = 0; index < dimension; index++) {
            vector.add(0.0d);
        }
        return vector;
    }

    private static String operationKind(BaseOperation operation) {
        return operation == null ? "NoneType" : operation.getClass().getSimpleName();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static double pythonMax(double left, double right) {
        return right > left ? right : left;
    }

    private static double pythonMin(double left, double right) {
        return right < left ? right : left;
    }

    private static Map<String, VectorDataType> buildTypeMapping() {
        Map<String, VectorDataType> mapping = new LinkedHashMap<>();
        mapping.put("string", VectorDataType.VARCHAR);
        mapping.put("str", VectorDataType.VARCHAR);
        mapping.put("varchar", VectorDataType.VARCHAR);
        mapping.put("int", VectorDataType.INT32);
        mapping.put("integer", VectorDataType.INT32);
        mapping.put("int32", VectorDataType.INT32);
        mapping.put("int64", VectorDataType.INT64);
        mapping.put("long", VectorDataType.INT64);
        mapping.put("float", VectorDataType.FLOAT);
        mapping.put("float32", VectorDataType.FLOAT);
        mapping.put("double", VectorDataType.DOUBLE);
        mapping.put("float64", VectorDataType.DOUBLE);
        mapping.put("bool", VectorDataType.BOOL);
        mapping.put("boolean", VectorDataType.BOOL);
        mapping.put("json", VectorDataType.JSON);
        mapping.put("vector", VectorDataType.FLOAT_VECTOR);
        mapping.put("float_vector", VectorDataType.FLOAT_VECTOR);
        return Collections.unmodifiableMap(mapping);
    }

    private enum MissingValue {
        INSTANCE
    }
}
