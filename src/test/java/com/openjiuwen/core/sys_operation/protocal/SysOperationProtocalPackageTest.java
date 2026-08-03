/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.protocal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SysOperationProtocalPackageTest {

    @Test
    void exposesPythonPackageBridge() {
        assertEquals(
                "openjiuwen/core/sys_operation/protocal/__init__.py",
                SysOperationProtocalPackage.PYTHON_MODULE
        );
        assertEquals(
                List.of("BaseFsProtocal", "BaseShellProtocal", "BaseCodeProtocal"),
                SysOperationProtocalPackage.EXPORTED_SYMBOLS
        );
        assertSame(BaseFsProtocal.class, SysOperationProtocalPackage.BASE_FS_PROTOCAL);
        assertSame(BaseShellProtocal.class, SysOperationProtocalPackage.BASE_SHELL_PROTOCAL);
        assertSame(BaseCodeProtocal.class, SysOperationProtocalPackage.BASE_CODE_PROTOCAL);
    }
}
