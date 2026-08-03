/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.loguru;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoguruLoggingPackageTest {

    @Test
    void exposesPythonModuleReExport() {
        assertEquals("openjiuwen/core/common/logging/loguru/__init__.py", LoguruLoggingPackage.PYTHON_MODULE);
        assertEquals(LoguruLogger.class, LoguruLoggingPackage.LOGURU_LOGGER);
    }
}
