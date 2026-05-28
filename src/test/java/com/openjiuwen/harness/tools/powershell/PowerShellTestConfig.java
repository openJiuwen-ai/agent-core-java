/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.powershell;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Enable strict PowerShell security checks for unit tests.
 *
 * <p>Mirrors Python's {@code conftest.py} in
 * {@code tests.unit_tests.harness.tools.test_powershell}.
 *
 * <p>Sets OPENJIUWEN_BASH_STRICT=1 environment variable for test execution.
 */
public class PowerShellTestConfig {

    private static final String STRICT_ENV_VAR = "OPENJIUWEN_BASH_STRICT";

    @BeforeAll
    static void enableStrictMode() {
        System.setProperty(STRICT_ENV_VAR, "1");
    }

    @BeforeEach
    void verifyStrictModeEnabled() {
        // Placeholder: verify strict mode is enabled for each test
        assert "1".equals(System.getProperty(STRICT_ENV_VAR));
    }
}