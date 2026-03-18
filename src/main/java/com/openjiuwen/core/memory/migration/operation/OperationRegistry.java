/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.migration.operation;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.*;

/**
 * Registry that manages chained upgrade operations by entity_key.
 * Operations for the same entity_key must have monotonically increasing schema_versions.
 */
public class OperationRegistry {

    private Map<String, List<BaseOperation>> operations = new LinkedHashMap<>();

    public void register(String entityKey, BaseOperation op) {
        List<BaseOperation> ops = operations.get(entityKey);
        if (ops == null) {
            ops = new ArrayList<>();
            ops.add(op);
            operations.put(entityKey, ops);
            return;
        }
        int lastVersion = ops.get(ops.size() - 1).getSchemaVersion();
        if (op.getSchemaVersion() <= lastVersion) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_REGISTER_OPERATION_VALIDATION_INVALID,
                    "entity_key", entityKey,
                    "schema_version", String.valueOf(op.getSchemaVersion()),
                    "error_msg", "schema number must be greater than current maximum");
        }
        ops.add(op);
    }

    public List<BaseOperation> getOperations(String entityKey, int fromVersion, int toVersion) {
        if (fromVersion > toVersion) {
            return Collections.emptyList();
        }
        List<BaseOperation> ops = operations.getOrDefault(entityKey, Collections.emptyList());
        List<BaseOperation> result = new ArrayList<>();
        for (BaseOperation op : ops) {
            if (op.getSchemaVersion() >= fromVersion && op.getSchemaVersion() <= toVersion) {
                result.add(op);
            }
        }
        return result;
    }

    public List<BaseOperation> getOperations(String entityKey) {
        return getOperations(entityKey, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public int getCurrentVersion(String entityKey) {
        List<BaseOperation> ops = operations.getOrDefault(entityKey, Collections.emptyList());
        return ops.isEmpty() ? 0 : ops.get(ops.size() - 1).getSchemaVersion();
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

    public void setOperations(Map<String, List<BaseOperation>> ops) {
        this.operations = ops;
    }
}
