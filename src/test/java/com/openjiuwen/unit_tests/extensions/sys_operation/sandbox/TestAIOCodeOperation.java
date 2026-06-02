/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.sysop.SysOperation;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/sys_operation/sandbox/test_aio_code_operation.py}.
 */
class TestAIOCodeOperation extends AbstractSandboxCodeOperationTest {

    @Override
    protected SysOperation createSysOp() {
        return newAioSysOp();
    }
}
