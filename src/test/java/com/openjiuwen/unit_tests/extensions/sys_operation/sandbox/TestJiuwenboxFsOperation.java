/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/sys_operation/sandbox/test_jiuwenbox_fs_operation.py}.
 */
class TestJiuwenboxFsOperation extends AbstractSandboxFsOperationTest {

    @Override
    protected SysOperation createSysOp() {
        return newJiuwenboxSysOp();
    }

    @Test
    void testFsUploadReadOnlyPathReturnsServerErrorDetail() throws Exception {
        SysOperation sysOp = createSysOp();
        var localSource = tempDir.resolve("readonly_upload.txt");
        Files.writeString(localSource, "should be rejected");

        var result = sysOp.fs().uploadFile(
                localSource.toString(),
                "/etc/jiuwenbox-denied.txt",
                true,
                true,
                true,
                0,
                null
        );

        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), result.getCode());
        assertTrue(result.getMessage().contains("Access denied") || result.getMessage().contains("/etc/jiuwenbox-denied.txt"));
    }
}
