/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.loguru;

/**
 * Package bridge for Loguru logging exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/common/logging/loguru/__init__.py}.</p>
 */
public final class LoguruLoggingPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/common/logging/loguru/__init__.py";
    public static final Class<LoguruLogger> LOGURU_LOGGER = LoguruLogger.class;

    private LoguruLoggingPackage() {
    }
}
