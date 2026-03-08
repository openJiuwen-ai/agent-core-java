/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.spi.store.BaseKVStore;

import java.util.function.Consumer;

// ==================== SQL Operations ====================

/**
 * Add a new column to a table.
 */
class AddColumnOperation extends BaseOperation {
    private final String table;
    private final String columnName;
    private final String columnType;
    private final boolean nullable;
    private final Object defaultValue;

    public AddColumnOperation(OperationMetadata metadata, String table, String columnName,
                               String columnType, boolean nullable, Object defaultValue) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.columnType = columnType;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
    }

    public String getTable() { return table; }
    public String getColumnName() { return columnName; }
    public String getColumnType() { return columnType; }
    public boolean isNullable() { return nullable; }
    public Object getDefaultValue() { return defaultValue; }
}

/**
 * Rename a column in a table.
 */
class RenameColumnOperation extends BaseOperation {
    private final String table;
    private final String oldColumnName;
    private final String newColumnName;

    public RenameColumnOperation(OperationMetadata metadata, String table,
                                  String oldColumnName, String newColumnName) {
        super(metadata);
        this.table = table;
        this.oldColumnName = oldColumnName;
        this.newColumnName = newColumnName;
    }

    public String getTable() { return table; }
    public String getOldColumnName() { return oldColumnName; }
    public String getNewColumnName() { return newColumnName; }
}

/**
 * Update the data type of an existing column.
 */
class UpdateColumnTypeOperation extends BaseOperation {
    private final String table;
    private final String columnName;
    private final String newColumnType;

    public UpdateColumnTypeOperation(OperationMetadata metadata, String table,
                                      String columnName, String newColumnType) {
        super(metadata);
        this.table = table;
        this.columnName = columnName;
        this.newColumnType = newColumnType;
    }

    public String getTable() { return table; }
    public String getColumnName() { return columnName; }
    public String getNewColumnType() { return newColumnType; }
}

// ==================== Vector Operations ====================

/**
 * Add a scalar field to a vector data type.
 */
class AddScalarFieldOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final String fieldType;
    private final Object defaultValue;

    public AddScalarFieldOperation(OperationMetadata metadata, String dataType,
                                    String fieldName, String fieldType, Object defaultValue) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.defaultValue = defaultValue;
    }

    public String getDataType() { return dataType; }
    public String getFieldName() { return fieldName; }
    public String getFieldType() { return fieldType; }
    public Object getDefaultValue() { return defaultValue; }
}

/**
 * Rename a scalar field in a vector data type.
 */
class RenameScalarFieldOperation extends BaseOperation {
    private final String dataType;
    private final String oldFieldName;
    private final String newFieldName;

    public RenameScalarFieldOperation(OperationMetadata metadata, String dataType,
                                       String oldFieldName, String newFieldName) {
        super(metadata);
        this.dataType = dataType;
        this.oldFieldName = oldFieldName;
        this.newFieldName = newFieldName;
    }

    public String getDataType() { return dataType; }
    public String getOldFieldName() { return oldFieldName; }
    public String getNewFieldName() { return newFieldName; }
}

/**
 * Update the data type of a scalar field in a vector data type.
 */
class UpdateScalarFieldTypeOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final String newFieldType;

    public UpdateScalarFieldTypeOperation(OperationMetadata metadata, String dataType,
                                           String fieldName, String newFieldType) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newFieldType = newFieldType;
    }

    public String getDataType() { return dataType; }
    public String getFieldName() { return fieldName; }
    public String getNewFieldType() { return newFieldType; }
}

/**
 * Update the embedding dimension of a vector data type.
 */
class UpdateEmbeddingDimensionOperation extends BaseOperation {
    private final String dataType;
    private final String fieldName;
    private final int newDimension;
    private final int batchSize;

    public UpdateEmbeddingDimensionOperation(OperationMetadata metadata, String dataType,
                                              String fieldName, int newDimension, int batchSize) {
        super(metadata);
        this.dataType = dataType;
        this.fieldName = fieldName;
        this.newDimension = newDimension;
        this.batchSize = batchSize;
    }

    public String getDataType() { return dataType; }
    public String getFieldName() { return fieldName; }
    public int getNewDimension() { return newDimension; }
    public int getBatchSize() { return batchSize; }
}

// ==================== KV Operations ====================

/**
 * Update a key-value pair via a provided callable.
 */
class UpdateKVOperation extends BaseOperation {
    private final Consumer<BaseKVStore> updateFunc;

    public UpdateKVOperation(OperationMetadata metadata, Consumer<BaseKVStore> updateFunc) {
        super(metadata);
        this.updateFunc = updateFunc;
    }

    public Consumer<BaseKVStore> getUpdateFunc() { return updateFunc; }
}
