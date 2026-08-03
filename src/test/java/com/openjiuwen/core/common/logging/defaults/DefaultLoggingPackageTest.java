/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultLoggingPackageTest {

    @Test
    void exposesPythonModuleReExportsAndSessionHelpers() {
        assertEquals("openjiuwen/core/common/logging/default/__init__.py", DefaultLoggingPackage.PYTHON_MODULE);
        assertEquals(DefaultLogger.class, DefaultLoggingPackage.DEFAULT_LOGGER);

        DefaultLoggingPackage.setSessionId();
        assertEquals("default_trace_id", DefaultLoggingPackage.getSessionId());

        DefaultLoggingPackage.setSessionId("trace-default-package");
        assertEquals("trace-default-package", DefaultLoggingPackage.getSessionId());
    }
}
