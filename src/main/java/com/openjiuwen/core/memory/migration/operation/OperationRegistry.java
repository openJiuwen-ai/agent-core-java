/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that manages chained upgrade operations by entity key.
 *
 * <p>Mirrors Python's {@code OperationRegistry} in
 * {@code openjiuwen/core/memory/migration/operation/operation_registry.py}.
 */
public class OperationRegistry {

    private Map<String, List<BaseOperation>> operations = new LinkedHashMap<>();

    public void register(String entityKey, BaseOperation operation) {
        List<BaseOperation> entityOperations = operations.get(entityKey);
        if (entityOperations == null) {
            operations.put(entityKey, new ArrayList<>(List.of(operation)));
            return;
        }

        int lastVersion = entityOperations.get(entityOperations.size() - 1).getSchemaVersion();
        if (operation.getSchemaVersion() <= lastVersion) {
            throw ErrorHelper.buildError(
                    StatusCode.MEMORY_REGISTER_OPERATION_VALIDATION_INVALID,
                    "entity_key", entityKey,
                    "schema_version", String.valueOf(operation.getSchemaVersion()),
                    "error_msg", "the schema number of the new operation must be greater than the current maximum"
            );
        }
        entityOperations.add(operation);
    }

    public List<BaseOperation> getOperations(String entityKey, int fromVersion, int toVersion) {
        if (fromVersion > toVersion) {
            return Collections.emptyList();
        }
        List<BaseOperation> entityOperations = operations.getOrDefault(entityKey, Collections.emptyList());
        if (entityOperations.isEmpty()) {
            return Collections.emptyList();
        }

        List<BaseOperation> result = new ArrayList<>();
        for (BaseOperation operation : entityOperations) {
            int schemaVersion = operation.getSchemaVersion();
            if (fromVersion <= schemaVersion && schemaVersion <= toVersion) {
                result.add(operation);
            }
        }
        return result;
    }

    public int getCurrentVersion(String entityKey) {
        List<BaseOperation> entityOperations = operations.getOrDefault(entityKey, Collections.emptyList());
        return entityOperations.isEmpty() ? 0 : entityOperations.get(entityOperations.size() - 1).getSchemaVersion();
    }

    public List<String> getAllEntities() {
        return new ArrayList<>(operations.keySet());
    }

    public Map<String, List<BaseOperation>> getAllOperations() {
        return new LinkedHashMap<>(operations);
    }

    public void clear() {
        operations.clear();
    }

    public void setOperations(Map<String, List<BaseOperation>> operations) {
        Map<String, List<BaseOperation>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<BaseOperation>> entry : operations.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        this.operations = copied;
    }
}
