/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.sysop.result.ReadFileResult;

import java.util.Map;

/**
 * Backward-compatible base class for moved local file-system operations.
 *
 * <p>Mirrors Python's {@code BaseFsOperation} in
 * {@code openjiuwen/core/sys_operation/fs.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.BaseFsOperation}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public abstract class BaseFsOperation extends BaseOperation {

    protected BaseFsOperation(String name,
                              com.openjiuwen.core.sys_operation.OperationMode mode,
                              String description,
                              Object runConfig) {
        super(name, mode, description, runConfig);
    }

    public abstract ReadFileResult readFile(String path,
                                            String mode,
                                            Integer head,
                                            Integer tail,
                                            int[] lineRange,
                                            String encoding,
                                            int chunkSize,
                                            Map<String, Object> options);
}
