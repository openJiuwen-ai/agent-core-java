/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mirrors Python's {@code ExceptionUtils} in
 * {@code openjiuwen/core/common/security/exception_utils.py}.
 */
public final class ExceptionUtils {

    private ExceptionUtils() {
    }

    public static String formatValidationError(List<? extends Map<String, ?>> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(ExceptionUtils::formatOneError)
                .collect(Collectors.joining("\n"));
    }

    private static String formatOneError(Map<String, ?> error) {
        Object locObject = error.get("loc");
        List<String> locSegments = new ArrayList<>();
        if (locObject instanceof Iterable<?> iterable) {
            for (Object segment : iterable) {
                locSegments.add(String.valueOf(segment));
            }
        }
        Object message = error.get("msg");
        return String.join(".", locSegments) + ": " + (message == null ? "Unknown error" : String.valueOf(message));
    }
}
