/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * Registry that manages chained upgrade operations by entity_key.
 * Operations for the same entity_key must have monotonically increasing schema_versions.
 * 
 * @since 0.1.7
 */
public class OperationRegistry {
    private Map<String, List<BaseOperation>> operations = new LinkedHashMap<>();

    /**
     * register.
     * 
     * @param entityKey entityKey
     * @param op op
     * @since 0.1.7
     */
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
            throw ErrorHelper.buildError(StatusCode.MEMORY_REGISTER_OPERATION_VALIDATION_INVALID, "entity_key",
                    entityKey, "schema_version", String.valueOf(op.getSchemaVersion()), "error_msg",
                    "schema number must be greater than current maximum");
        }
        ops.add(op);
    }

    /**
     * getOperations.
     * 
     * @param entityKey entityKey
     * @param fromVersion fromVersion
     * @param toVersion toVersion
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * getOperations.
     * 
     * @param entityKey entityKey
     * @return the result
     * @since 0.1.7
     */
    public List<BaseOperation> getOperations(String entityKey) {
        return getOperations(entityKey, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * getCurrentVersion.
     * 
     * @param entityKey entityKey
     * @return the result
     * @since 0.1.7
     */
    public int getCurrentVersion(String entityKey) {
        List<BaseOperation> ops = operations.getOrDefault(entityKey, Collections.emptyList());
        return ops.isEmpty() ? 0 : ops.get(ops.size() - 1).getSchemaVersion();
    }

    /**
     * getAllEntities.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getAllEntities() {
        return new ArrayList<>(operations.keySet());
    }

    /**
     * getAllOperations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<BaseOperation>> getAllOperations() {
        return new LinkedHashMap<>(operations);
    }

    /**
     * clear.
     * 
     * @since 0.1.7
     */
    public void clear() {
        operations.clear();
    }

    /**
     * setOperations.
     * 
     * @param ops ops
     * @since 0.1.7
     */
    public void setOperations(Map<String, List<BaseOperation>> ops) {
        this.operations = ops;
    }
}
