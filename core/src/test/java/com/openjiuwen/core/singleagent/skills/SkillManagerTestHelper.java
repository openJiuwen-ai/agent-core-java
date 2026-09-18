/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.sysop.BaseFsOperation;

import java.util.function.Function;

/**
 * Test helper that exposes the package-private SkillManager constructor
 * for cross-package tests.
 */
public class SkillManagerTestHelper {

    private SkillManagerTestHelper() {
    }

    /**
     * Creates a SkillManager with a custom fsResolver, bypassing the
     * default Runner-based resolution.
     *
     * @param sysOperationId the sys operation id
     * @param fsResolver custom resolver function
     * @return a new SkillManager instance
     */
    public static SkillManager createWithResolver(String sysOperationId,
                                                   Function<String, BaseFsOperation> fsResolver) {
        return new SkillManager(sysOperationId, fsResolver);
    }
}
