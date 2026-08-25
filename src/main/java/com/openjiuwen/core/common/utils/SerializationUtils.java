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
     * Require a value that can participate in Java native serialization.
     *
     * @param value candidate value; must not be {@code null}
     * @param fieldName field name used to describe an invalid value
     * @return the serializable value
     * @throws IllegalArgumentException when the value is null or not serializable
     * @since 0.1.14
     */
    public static Serializable requireSerializable(Object value, String fieldName) {
        if (value instanceof Serializable serializable) {
            return serializable;
        }
        throw new IllegalArgumentException(fieldName + " must be non-null and implement Serializable");
    }
}
