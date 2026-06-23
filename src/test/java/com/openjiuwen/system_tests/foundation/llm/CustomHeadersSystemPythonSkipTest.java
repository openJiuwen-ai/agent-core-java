/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.foundation.llm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code TestCustomHeadersSystem} in
 * {@code tests/system_tests/foundation/llm/test_custom_headers_system.py}.</p>
 */
class CustomHeadersSystemPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: temporarily skipped while upstream integration stabilizes";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void commonAsyncOpenaiClientForwardsSanitizedDefaultHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void commonOpenaiClientWithoutCustomHeadersOmitsDefaultHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void commonSyncOpenaiClientForwardsSanitizedDefaultHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void initModelInvokeInjectsHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void initModelStreamRequestHeadersOverride() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void modelInvokeInjectsSanitizedConfigHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void modelInvokeRequestHeadersOverrideCaseInsensitive() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void modelInvokeWithoutHeadersHasNoExtraHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void modelStreamInjectsSanitizedConfigHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void modelStreamRequestHeadersOverride() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void reactConfigInvokeInjectsHeaders() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void reactConfigStreamRequestHeadersOverride() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void reactConfigWithoutHeadersHasNoExtraHeaders() {
    }
}
