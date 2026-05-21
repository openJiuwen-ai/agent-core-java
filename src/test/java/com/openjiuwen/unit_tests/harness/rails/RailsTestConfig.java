/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

/**
 * Test bootstrap configuration for harness/rails unit tests.
 * <p>
 * Provides stub handling for optional dependencies that may not be installed in CI.
 * In Python, this is implemented as a conftest.py that stubs jsonschema_path module.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.rails.conftest}.
 */
public class RailsTestConfig {

    /**
     * SchemaPath stub class for optional jsonschema_path dependency.
     * <p>
     * In Python, this is {@code types.ModuleType("jsonschema_path")} with
     * {@code SchemaPath = object}.
     */
    public static class SchemaPathStub {
        // Empty stub class - provides baseline object type
    }

    /**
     * Ensures the SchemaPath stub is available for tests.
     * <p>
     * In Python, this checks {@code sys.modules} and creates a stub module
     * if jsonschema_path is not present.
     */
    public static SchemaPathStub ensureSchemaPathStub() {
        return new SchemaPathStub();
    }

    // Static initialization block - runs before any tests in this package
    static {
        // Pre-initialize stub for tests
        ensureSchemaPathStub();
    }
}