/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.context_evolver.core.file_connector;

import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Method;

/**
 * Utility class for safe model serialization.
 */
public class SafeModelDump {
    
    /**
     * Safely serialize an object to dictionary.
     * Handles objects with toDict(), toMap(), or model_dump() methods.
     *
     * @param obj object to serialize
     * @return dictionary representation
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> safeModelDump(Object obj) {
        if (obj == null) {
            return new HashMap<>();
        }

        // Try Pydantic v2 style method first.
        Map<String, Object> dumped = invokeMapMethod(obj, "model_dump");
        if (dumped != null) {
            return dumped;
        }

        // Try Python-style custom method names.
        dumped = invokeMapMethod(obj, "to_dict");
        if (dumped != null) {
            return dumped;
        }

        dumped = invokeMapMethod(obj, "dict");
        if (dumped != null) {
            return dumped;
        }

        // Keep Java-friendly fallback names for existing callers.
        dumped = invokeMapMethod(obj, "toDict");
        if (dumped != null) {
            return dumped;
        }

        dumped = invokeMapMethod(obj, "toMap");
        if (dumped != null) {
            return dumped;
        }

        
        throw new IllegalArgumentException(
            "Object of type " + obj.getClass().getName() + 
            " has no serialization method (model_dump, to_dict, dict, toDict, or toMap)"
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeMapMethod(Object obj, String methodName) {
        Method method = findMethod(obj.getClass(), methodName);
        if (method == null) {
            return null;
        }

        try {
            method.setAccessible(true);
            Object result = method.invoke(obj);
            if (result instanceof Map<?, ?> resultMap) {
                return (Map<String, Object>) resultMap;
            }
        } catch (Exception e) {
            // Ignore and try the next method.
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String methodName) {
        try {
            return type.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            try {
                return type.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
}
