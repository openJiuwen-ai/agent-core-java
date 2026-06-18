/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.Map;

/**
 * Base wrapper for harness memory operations.
 *
 * <p>Mirrors Python's memory tool wrappers in
 * {@code openjiuwen/harness/tools/memory.py} and
 * {@code openjiuwen/harness/tools/coding_memory.py}.</p>
 */
abstract class MemoryOperationTool extends AbstractHarnessTool {

    private final MemoryOperation operation;

    protected MemoryOperationTool(String id, String name, String description, MemoryOperation operation) {
        super(toolCard(id, name, description));
        this.operation = operation;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String validationError = validate(inputs == null ? Map.of() : inputs);
        if (validationError != null) {
            return ToolOutput.failure(validationError);
        }
        if (operation == null) {
            return ToolOutput.failure(getCard().getName() + " operation is not configured");
        }
        try {
            Map<String, Object> result = operation.call(inputs == null ? Map.of() : inputs);
            boolean disabled = Boolean.TRUE.equals(result.get("disabled"));
            boolean success = result.containsKey("success") ? Boolean.TRUE.equals(result.get("success")) : !disabled;
            Object error = result.get("error");
            return ToolOutput.of(success, result, error == null ? null : String.valueOf(error));
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    protected abstract String validate(Map<String, Object> inputs);

    protected static String require(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        return value == null || String.valueOf(value).isBlank() ? key + " is required" : null;
    }

    @FunctionalInterface
    public interface MemoryOperation {
        Map<String, Object> call(Map<String, Object> inputs) throws Exception;
    }
}
