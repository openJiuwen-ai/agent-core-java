/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Backward-compatible alias for the moved system operation base type.
 *
 * <p>Mirrors Python's {@code BaseOperation} in
 * {@code openjiuwen/core/sys_operation/base.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.BaseOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public abstract class BaseOperation extends com.openjiuwen.core.sys_operation.BaseOperation {

    protected BaseOperation(String name,
                            com.openjiuwen.core.sys_operation.OperationMode mode,
                            String description,
                            Object runConfig) {
        super(name, mode, description, runConfig);
    }
}
