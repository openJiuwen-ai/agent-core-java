/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Conversion functions for Vector Store distance/similarity scores to normalized similarity [0, 1],
 * and schema migration helpers.
 * <p>
 * Mirrors Python's {@code store.vector.utils} module.
 */
public final class VectorStoreUtils {

    private VectorStoreUtils() {
    }

    // ====== Distance / Similarity Converters ======

    /**
     * Convert squared L2 distance to normalized similarity in [0, 1].
     *
     * @param rawScore raw L2 distance score
     * @param maxDist  maximum distance (defaults to 4 for unit vectors)
     * @return normalized similarity score in [0, 1]
     */
    public static double convertL2Squared(double rawScore, double maxDist) {
        return Math.max(0.0, (maxDist - rawScore) / maxDist);
    }

    public static double convertL2Squared(double rawScore) {
        return convertL2Squared(rawScore, 4.0);
    }

    /**
     * Convert cosine similarity to normalized similarity in [0, 1].
     *
     * @param rawScore raw cosine similarity (range [-1, 1])
     * @return normalized similarity score in [0, 1]
     */
    public static double convertCosineSimilarity(double rawScore) {
        return (rawScore + 1.0) / 2.0;
    }

    /**
     * Convert cosine distance to normalized cosine similarity in [0, 1].
     *
     * @param rawScore raw cosine distance (range [0, 2])
     * @return normalized similarity score in [0, 1]
     */
    public static double convertCosineDistance(double rawScore) {
        return (2.0 - rawScore) / 2.0;
    }

    /**
     * Convert raw inner product to normalized similarity in [0, 1].
     *
     * @param rawScore raw inner product
     * @return normalized similarity score in [0, 1]
     */
    public static double convertIpSimilarity(double rawScore) {
        return Math.max(0.0, Math.min(1.0, (rawScore + 1.0) / 2.0));
    }

    /**
     * Convert inner product distance form to normalized similarity in [0, 1].
     *
     * @param rawScore IP distance (range [0, 2])
     * @return normalized similarity score in [0, 1]
     */
    public static double convertIpDistance(double rawScore) {
        return Math.max(0.0, Math.min(1.0, (2.0 - rawScore) / 2.0));
    }

    // ====== Type Mapping ======

    private static final Map<String, VectorDataType> TYPE_MAPPING = Map.ofEntries(
            Map.entry("string", VectorDataType.VARCHAR),
            Map.entry("str", VectorDataType.VARCHAR),
            Map.entry("varchar", VectorDataType.VARCHAR),
            Map.entry("int", VectorDataType.INT32),
            Map.entry("integer", VectorDataType.INT32),
            Map.entry("int32", VectorDataType.INT32),
            Map.entry("int64", VectorDataType.INT64),
            Map.entry("long", VectorDataType.INT64),
            Map.entry("float", VectorDataType.FLOAT),
            Map.entry("float32", VectorDataType.FLOAT),
            Map.entry("double", VectorDataType.DOUBLE),
            Map.entry("float64", VectorDataType.DOUBLE),
            Map.entry("bool", VectorDataType.BOOL),
            Map.entry("boolean", VectorDataType.BOOL),
            Map.entry("json", VectorDataType.JSON),
            Map.entry("vector", VectorDataType.FLOAT_VECTOR),
            Map.entry("float_vector", VectorDataType.FLOAT_VECTOR)
    );

    /**
     * Map a string type name to VectorDataType.
     *
     * @param typeStr the string representation of the type
     * @return the corresponding VectorDataType
     * @throws com.openjiuwen.core.common.exception.BaseError if unknown type
     */
    public static VectorDataType mapStringToVectorDataType(String typeStr) {
        String normalized = typeStr.toLowerCase().strip();
        VectorDataType result = TYPE_MAPPING.get(normalized);
        if (result == null) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Unknown type string: '" + typeStr + "'. Supported types: " + TYPE_MAPPING.keySet());
        }
        return result;
    }

    // ====== Schema Migration ======

    /**
     * Compute the final schema after applying all operations.
     * <p>
     * Operations are expected to have the following known types (checked by class name):
     * <ul>
     *   <li>AddScalarFieldOperation - adds a new scalar field</li>
     *   <li>RenameScalarFieldOperation - renames a field</li>
     *   <li>UpdateScalarFieldTypeOperation - updates a field's data type</li>
     *   <li>UpdateEmbeddingDimensionOperation - updates vector field dimension</li>
     * </ul>
     *
     * @param oldSchema  the original schema
     * @param operations list of operations to apply
     * @return the resulting schema after all operations
     */
    @SuppressWarnings("unchecked")
    public static CollectionSchema computeNewSchema(CollectionSchema oldSchema, List<?> operations) {
        Map<String, Object> schemaDict = oldSchema.toDict();
        CollectionSchema newSchema = CollectionSchema.fromDict(schemaDict);

        for (Object operation : operations) {
            newSchema = applySchemaOperation(newSchema, operation);
        }
        return newSchema;
    }

    @SuppressWarnings("unchecked")
    private static CollectionSchema applySchemaOperation(CollectionSchema schema, Object operation) {
        // Use reflection-based approach to check operation type by class name
        String className = operation.getClass().getSimpleName();
        if (operation instanceof Map<?, ?> opMap) {
            return applyMapOperation(schema, (Map<String, Object>) opMap);
        }
        // Try reflection for known operation types
        return applyReflectiveOperation(schema, operation, className);
    }

    @SuppressWarnings("unchecked")
    private static CollectionSchema applyMapOperation(CollectionSchema schema, Map<String, Object> opMap) {
        String type = (String) opMap.get("type");
        if (type == null) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Operation map must contain 'type' key");
        }
        Map<String, Object> schemaDict = schema.toDict();
        List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaDict.get("fields");

        switch (type) {
            case "add_field" -> {
                String fieldName = (String) opMap.get("field_name");
                String fieldType = (String) opMap.get("field_type");
                Object defaultValue = opMap.get("default_value");
                VectorDataType dtype = mapStringToVectorDataType(fieldType);
                FieldSchema.Builder fb = FieldSchema.builder().name(fieldName).dtype(dtype);
                if (defaultValue != null) {
                    fb.defaultValue(defaultValue);
                }
                CollectionSchema result = CollectionSchema.fromDict(schemaDict);
                result.addField(fb.build());
                return result;
            }
            case "rename_field" -> {
                String oldName = (String) opMap.get("old_field_name");
                String newName = (String) opMap.get("new_field_name");
                if (oldName.equals(newName)) return schema;
                renameFieldInList(fields, oldName, newName);
                return CollectionSchema.fromDict(schemaDict);
            }
            case "update_field_type" -> {
                String fieldName = (String) opMap.get("field_name");
                String newFieldType = (String) opMap.get("new_field_type");
                updateFieldTypeInList(fields, fieldName, newFieldType);
                return CollectionSchema.fromDict(schemaDict);
            }
            case "update_embedding_dimension" -> {
                String fieldName = (String) opMap.get("field_name");
                int newDim = ((Number) opMap.get("new_dimension")).intValue();
                updateVectorDimInList(fields, fieldName, newDim);
                return CollectionSchema.fromDict(schemaDict);
            }
            default -> throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Unsupported operation type: " + type);
        }
    }

    private static CollectionSchema applyReflectiveOperation(CollectionSchema schema, Object operation, String className) {
        try {
            Map<String, Object> schemaDict = schema.toDict();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) schemaDict.get("fields");

            if (className.contains("AddScalarField")) {
                String fieldName = (String) operation.getClass().getMethod("getFieldName").invoke(operation);
                String fieldType = (String) operation.getClass().getMethod("getFieldType").invoke(operation);
                Object defaultValue = operation.getClass().getMethod("getDefaultValue").invoke(operation);
                VectorDataType dtype = mapStringToVectorDataType(fieldType);
                FieldSchema.Builder fb = FieldSchema.builder().name(fieldName).dtype(dtype);
                if (defaultValue != null) {
                    fb.defaultValue(defaultValue);
                }
                CollectionSchema result = CollectionSchema.fromDict(schemaDict);
                result.addField(fb.build());
                return result;
            } else if (className.contains("RenameScalarField")) {
                String oldName = (String) operation.getClass().getMethod("getOldFieldName").invoke(operation);
                String newName = (String) operation.getClass().getMethod("getNewFieldName").invoke(operation);
                if (oldName.equals(newName)) return schema;
                renameFieldInList(fields, oldName, newName);
                return CollectionSchema.fromDict(schemaDict);
            } else if (className.contains("UpdateScalarFieldType")) {
                String fieldName = (String) operation.getClass().getMethod("getFieldName").invoke(operation);
                String newFieldType = (String) operation.getClass().getMethod("getNewFieldType").invoke(operation);
                updateFieldTypeInList(fields, fieldName, newFieldType);
                return CollectionSchema.fromDict(schemaDict);
            } else if (className.contains("UpdateEmbeddingDimension")) {
                String fieldName = (String) operation.getClass().getMethod("getFieldName").invoke(operation);
                int newDim = (int) operation.getClass().getMethod("getNewDimension").invoke(operation);
                updateVectorDimInList(fields, fieldName, newDim);
                return CollectionSchema.fromDict(schemaDict);
            } else {
                throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                        "error_msg", "Unsupported operation type: " + className);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to apply schema operation: " + className, e);
        }
    }

    private static void renameFieldInList(List<Map<String, Object>> fields, String oldName, String newName) {
        boolean oldExists = false;
        boolean newExists = false;
        for (Map<String, Object> field : fields) {
            if (oldName.equals(field.get("name"))) oldExists = true;
            if (newName.equals(field.get("name"))) newExists = true;
        }
        if (!oldExists) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Old field '" + oldName + "' does not exist");
        }
        if (newExists) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "New field '" + newName + "' already exists");
        }
        for (Map<String, Object> field : fields) {
            if (oldName.equals(field.get("name"))) {
                field.put("name", newName);
                break;
            }
        }
    }

    private static void updateFieldTypeInList(List<Map<String, Object>> fields, String fieldName, String newFieldType) {
        boolean found = false;
        for (Map<String, Object> field : fields) {
            if (fieldName.equals(field.get("name"))) {
                found = true;
                if (VectorDataType.FLOAT_VECTOR.name().equals(field.get("type"))) {
                    throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                            "error_msg", "Cannot update type of vector field '" + fieldName + "'");
                }
                field.put("type", mapStringToVectorDataType(newFieldType).name());
                break;
            }
        }
        if (!found) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Field '" + fieldName + "' does not exist");
        }
    }

    private static void updateVectorDimInList(List<Map<String, Object>> fields, String fieldName, int newDim) {
        boolean found = false;
        boolean isVector = false;
        for (Map<String, Object> field : fields) {
            if (fieldName.equals(field.get("name"))) {
                found = true;
                if (VectorDataType.FLOAT_VECTOR.name().equals(field.get("type"))) {
                    isVector = true;
                    field.put("dim", newDim);
                }
                break;
            }
        }
        if (!found) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Field '" + fieldName + "' does not exist");
        }
        if (!isVector) {
            throw ErrorHelper.buildError(StatusCode.STORE_VECTOR_SCHEMA_INVALID,
                    "error_msg", "Field '" + fieldName + "' is not a vector field");
        }
    }

    // ====== Document Transform ======

    /**
     * Build a unified transform function that applies all operations to a document.
     *
     * @param operations list of operations to apply
     * @return transform function
     */
    public static Function<Map<String, Object>, Map<String, Object>> buildTransformFuncForOperations(
            List<?> operations) {
        return doc -> {
            Map<String, Object> result = new LinkedHashMap<>(doc);
            for (Object operation : operations) {
                applyOperationToDoc(result, operation);
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    private static void applyOperationToDoc(Map<String, Object> doc, Object operation) {
        String className = operation.getClass().getSimpleName();
        try {
            if (operation instanceof Map<?, ?> opMap) {
                applyMapOperationToDoc(doc, (Map<String, Object>) opMap);
            } else if (className.contains("AddScalarField")) {
                String fieldName = (String) operation.getClass().getMethod("getFieldName").invoke(operation);
                Object defaultValue = operation.getClass().getMethod("getDefaultValue").invoke(operation);
                if (!doc.containsKey(fieldName) && defaultValue != null) {
                    doc.put(fieldName, defaultValue);
                }
            } else if (className.contains("RenameScalarField")) {
                String oldName = (String) operation.getClass().getMethod("getOldFieldName").invoke(operation);
                String newName = (String) operation.getClass().getMethod("getNewFieldName").invoke(operation);
                if (doc.containsKey(oldName)) {
                    doc.put(newName, doc.remove(oldName));
                }
            }
            // UpdateScalarFieldType: keep value as-is, let database handle conversion
            // UpdateEmbeddingDimension: requires re-embedding function, handled externally
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to apply operation to document: " + className, e);
        }
    }

    private static void applyMapOperationToDoc(Map<String, Object> doc, Map<String, Object> opMap) {
        String type = (String) opMap.get("type");
        if ("add_field".equals(type)) {
            String fieldName = (String) opMap.get("field_name");
            Object defaultValue = opMap.get("default_value");
            if (!doc.containsKey(fieldName) && defaultValue != null) {
                doc.put(fieldName, defaultValue);
            }
        } else if ("rename_field".equals(type)) {
            String oldName = (String) opMap.get("old_field_name");
            String newName = (String) opMap.get("new_field_name");
            if (doc.containsKey(oldName)) {
                doc.put(newName, doc.remove(oldName));
            }
        }
    }
}
