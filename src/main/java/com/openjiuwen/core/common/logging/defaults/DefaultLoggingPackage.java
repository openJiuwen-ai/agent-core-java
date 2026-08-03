/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.openjiuwen.core.common.logging.LoggingUtils;

/**
 * Package bridge for default logging exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/common/logging/default/__init__.py}.</p>
 */
public final class DefaultLoggingPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/common/logging/default/__init__.py";
    public static final Class<DefaultLogger> DEFAULT_LOGGER = DefaultLogger.class;

    private DefaultLoggingPackage() {
    }

    public static void setSessionId() {
        LoggingUtils.setSessionId();
    }

    public static void setSessionId(String traceId) {
        LoggingUtils.setSessionId(traceId);
    }

    public static String getSessionId() {
        return LoggingUtils.getSessionId();
    }
}
