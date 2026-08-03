/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import java.lang.reflect.Method;

/**
 * Storage codec contract for memory persistence.
 *
 * <p>Mirrors Python's {@code StorageCodec} in
 * {@code openjiuwen/core/foundation/store/base_memory_index.py}.</p>
 */
public interface StorageCodec {

    String encode(String text);

    String decode(String data);

    static boolean isCodec(Object candidate) {
        if (candidate == null) {
            return false;
        }
        Class<?> type = candidate.getClass();
        return matches(type, "encode") && matches(type, "decode");
    }

    private static boolean matches(Class<?> type, String methodName) {
        try {
            Method method = type.getMethod(methodName, String.class);
            return method.getReturnType() == String.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
