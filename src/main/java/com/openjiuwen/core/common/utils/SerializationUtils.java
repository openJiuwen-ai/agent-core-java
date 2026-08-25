/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import java.io.Serializable;

/**
 * Utilities for enforcing Java native serialization contracts at object boundaries.
 *
 * @since 0.1.14
 */
public final class SerializationUtils {
    private SerializationUtils() {
    }

    /**
     * Return a value that can participate in Java native serialization.
     *
     * @param value candidate value, or {@code null}
     * @param fieldName field name used to describe an invalid value
     * @return the serializable value, or {@code null}
     * @throws IllegalArgumentException when a non-null value is not serializable
     * @since 0.1.14
     */
    public static Serializable requireSerializable(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Serializable serializable) {
            return serializable;
        }
        throw new IllegalArgumentException(fieldName + " must implement Serializable");
    }
}
