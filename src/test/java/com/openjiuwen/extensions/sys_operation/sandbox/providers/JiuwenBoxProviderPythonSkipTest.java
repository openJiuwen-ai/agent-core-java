/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's Jiuwenbox provider end-to-end tests in
 * {@code tests/unit_tests/extensions/sys_operation/sandbox/test_jiuwenbox.py}.</p>
 */
class JiuwenBoxProviderPythonSkipTest {

    private static final String PYTHON_SKIP_REASON =
            "Skipped in Python source: Requires running Jiuwenbox sandbox";

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void fsCodeShellShareAutoCreatedSandbox() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void extraParamsSharesSandboxIdAfterMemoryCacheCleared() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void extraParamsPolicyAndPolicyModeAreUsedToCreateSandbox() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void forceRecreateCreatesNewSandboxOnRemoteAndReplacesStaleCache() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void forceRecreateWithPolicyAppliesPolicyToNewSandbox() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void forceRecreateUploadsPreserveFilesIntoNewSandbox() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void preserveFileUploadedWhenProviderCreatesNewSandbox() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void preserveDirectoryRecursesIntoSandbox() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void preserveFileNotReUploadedWhenSandboxIdAlreadyCached() {
    }
}
