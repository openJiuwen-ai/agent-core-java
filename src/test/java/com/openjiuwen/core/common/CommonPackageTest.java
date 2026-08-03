/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.schema.Param;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Mirrors Python's package surface in
 * {@code openjiuwen/core/common/__init__.py}.
 */
class CommonPackageTest {

    @Test
    void exposesPythonPackageBridge() {
        assertEquals("openjiuwen/core/common/__init__.py", CommonPackage.PYTHON_MODULE);
        assertEquals(BaseCard.class, CommonPackage.BASE_CARD);
        assertEquals(Param.class, CommonPackage.PARAM);
        assertIterableEquals(
                java.util.List.of("BaseCard", "Param"),
                CommonPackage.EXPORTED_SYMBOLS
        );
    }
}
