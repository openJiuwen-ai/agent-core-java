/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliStoragePackageTest {

    @Test
    void exposesPythonModuleReExports() {
        assertEquals("openjiuwen/harness/cli/storage/__init__.py", CliStoragePackage.PYTHON_MODULE);
        assertEquals(CliSessionStore.class, CliStoragePackage.SESSION_STORE);
        assertEquals(CliSessionStore.StoredMessage.class, CliStoragePackage.STORED_MESSAGE);
        assertEquals(CliSessionStore.StoredSession.class, CliStoragePackage.STORED_SESSION);
    }
}
