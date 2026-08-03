/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import com.openjiuwen.core.sys_operation.OperationDef;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.OperationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code test_sandbox_discovery} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_sandbox_discovery.py}.</p>
 */
class SandboxDiscoveryMissingTest {

    @Test
    void sandboxOperationsAreDiscoveredThroughRegistry() {
        OperationDef fsOperation = OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX);
        assertThat(fsOperation).isNotNull();
        assertThat(fsOperation.name()).isEqualTo("fs");
        assertThat(fsOperation.mode()).isEqualTo(OperationMode.SANDBOX);

        OperationDef shellOperation = OperationRegistry.getOperationInfo("shell", OperationMode.SANDBOX);
        assertThat(shellOperation).isNotNull();
        assertThat(shellOperation.name()).isEqualTo("shell");

        OperationDef codeOperation = OperationRegistry.getOperationInfo("code", OperationMode.SANDBOX);
        assertThat(codeOperation).isNotNull();
        assertThat(codeOperation.name()).isEqualTo("code");
    }
}
