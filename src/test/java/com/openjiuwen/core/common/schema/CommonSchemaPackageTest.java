/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.schema;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused tests for {@link CommonSchemaPackage}.
 */
class CommonSchemaPackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/core/common/schema/__init__.py", CommonSchemaPackage.PYTHON_MODULE);
        assertEquals(Param.class, CommonSchemaPackage.PARAM);
        assertEquals(ParamType.class, CommonSchemaPackage.PARAM_TYPE);
        assertEquals(BaseCard.class, CommonSchemaPackage.BASE_CARD);
    }
}
