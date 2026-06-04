/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.vector_fields;

import com.openjiuwen.spi.store.vector.CollectionSchema;
import com.openjiuwen.spi.store.vector.FieldSchema;
import com.openjiuwen.spi.store.vector.VectorDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus-compatible field helpers.
 * <p>
 * Mirrors Python's {@code MilvusFields} in
 * {@code openjiuwen.core.foundation.store.vector_fields.milvus_fields}.
 *
 * <p>Python features:
 * <ul>
 *   <li>MilvusFieldDefine - field definition helper</li>
 *   <li>CollectionSchema creation</li>
 *   <li>FieldSchema creation</li>
 *   <li>create_collection_schema - creates complete schema</li>
 * </ul>
 */
public final class MilvusFields {

    private MilvusFields() {
    }

    /**
     * Create default schema with embedding field.
     * <p>
     * Mirrors Python's default_schema functionality.
     *
     * @param dimension vector dimension
     * @return CollectionSchema with default fields
     */
    public static CollectionSchema defaultSchema(int dimension) {
        return BaseVectorFields.defaultSchema("embedding", dimension);
    }

    /**
     * Create collection schema with custom fields.
     * <p>
     * Mirrors Python's create_collection_schema.
     *
     * @param collectionName collection name
     * @param description collection description
     * @param fields list of field schemas
     * @return CollectionSchema
     */
    public static CollectionSchema createCollectionSchema(
            String collectionName,
            String description,
            List<FieldSchema> fields) {
        return CollectionSchema.fromFields(fields, description, false);
    }

    /**
     * Create a field schema for primary key.
     * <p>
     * Mirrors Python's primary key field creation.
     *
     * @param name field name
     * @return FieldSchema for primary key
     */
    public static FieldSchema createPrimaryKeyField(String name) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.VARCHAR)
                .isPrimary(true)
                .autoId(false)
                .maxLength(36)
                .build();
    }

    /**
     * Create a field schema for vector embedding.
     * <p>
     * Mirrors Python's vector field creation.
     *
     * @param name field name
     * @param dimension vector dimension
     * @return FieldSchema for vector
     */
    public static FieldSchema createVectorField(String name, int dimension) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.FLOAT_VECTOR)
                .dim(dimension)
                .build();
    }

    /**
     * Create a field schema for varchar text.
     *
     * @param name field name
     * @param maxLength max length
     * @return FieldSchema for varchar
     */
    public static FieldSchema createVarcharField(String name, int maxLength) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.VARCHAR)
                .maxLength(maxLength)
                .build();
    }

    /**
     * Create a field schema for INT64.
     *
     * @param name field name
     * @return FieldSchema for INT64
     */
    public static FieldSchema createInt64Field(String name) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.INT64)
                .build();
    }

    /**
     * Create a field schema for INT32.
     *
     * @param name field name
     * @return FieldSchema for INT32
     */
    public static FieldSchema createInt32Field(String name) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.INT32)
                .build();
    }

    /**
     * Create a field schema for FLOAT.
     *
     * @param name field name
     * @return FieldSchema for FLOAT
     */
    public static FieldSchema createFloatField(String name) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.FLOAT)
                .build();
    }

    /**
     * Create a field schema for BOOL.
     *
     * @param name field name
     * @return FieldSchema for BOOL
     */
    public static FieldSchema createBoolField(String name) {
        return FieldSchema.builder()
                .name(name)
                .dtype(VectorDataType.BOOL)
                .build();
    }

    /**
     * Build a standard Milvus collection schema with common fields.
     * <p>
     * Creates schema with: id (primary), embedding (vector), text (varchar), metadata (json).
     *
     * @param collectionName collection name
     * @param dimension vector dimension
     * @return CollectionSchema
     */
    public static CollectionSchema buildStandardSchema(String collectionName, int dimension) {
        List<FieldSchema> fields = new ArrayList<>();
        fields.add(createPrimaryKeyField("id"));
        fields.add(createVectorField("embedding", dimension));
        fields.add(createVarcharField("text", 256));
        fields.add(createVarcharField("metadata", 1024));

        return createCollectionSchema(collectionName, "Standard Milvus collection schema", fields);
    }

    /**
     * Build a Milvus collection schema for memory storage.
     * <p>
     * Creates schema with: memory_id, embedding, content, memory_type, created_at.
     *
     * @param collectionName collection name
     * @param dimension vector dimension
     * @return CollectionSchema
     */
    public static CollectionSchema buildMemorySchema(String collectionName, int dimension) {
        List<FieldSchema> fields = new ArrayList<>();
        fields.add(createPrimaryKeyField("memory_id"));
        fields.add(createVectorField("embedding", dimension));
        fields.add(createVarcharField("content", 2048));
        fields.add(createVarcharField("memory_type", 64));
        fields.add(createInt64Field("created_at"));

        return createCollectionSchema(collectionName, "Memory collection schema", fields);
    }
}
