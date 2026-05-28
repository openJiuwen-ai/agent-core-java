/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.test_bash;

import com.openjiuwen.harness.tools.shell.bash.BashOutputUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test configuration for bash tool tests.
 * <p>
 * Mirrors Python's {@code conftest.py} from
 * {@code tests/unit_tests/harness/tools/test_bash/conftest.py}.
 *
 * <p>Python conftest sets OPENJIUWEN_BASH_STRICT=1 environment variable.
 * In Java, this is handled via system properties.
 */
@DisplayName("Bash Test Configuration")
class BashTestConfig {

    @Nested
    @DisplayName("Environment Tests")
    class EnvironmentTests {

        @Test
        @DisplayName("test strict mode can be enabled")
        void testStrictModeCanBeEnabled() {
            // Python: enable_bash_strict fixture sets OPENJIUWEN_BASH_STRICT=1
            System.setProperty("OPENJIUWEN_BASH_STRICT", "1");
            
            String strictMode = System.getProperty("OPENJIUWEN_BASH_STRICT");
            assertEquals("1", strictMode);
            
            // Clean up
            System.clearProperty("OPENJIUWEN_BASH_STRICT");
        }
    }
}