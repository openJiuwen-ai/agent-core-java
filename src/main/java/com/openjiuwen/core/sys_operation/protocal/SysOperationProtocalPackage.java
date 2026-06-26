/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.protocal;

import java.util.List;

/**
 * Package bridge for sys-operation protocal exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/sys_operation/protocal/__init__.py}.
 * </p>
 */
public final class SysOperationProtocalPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/sys_operation/protocal/__init__.py";
    public static final Class<BaseFsProtocal> BASE_FS_PROTOCAL = BaseFsProtocal.class;
    public static final Class<BaseShellProtocal> BASE_SHELL_PROTOCAL = BaseShellProtocal.class;
    public static final Class<BaseCodeProtocal> BASE_CODE_PROTOCAL = BaseCodeProtocal.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseFsProtocal",
            "BaseShellProtocal",
            "BaseCodeProtocal"
    );

    private SysOperationProtocalPackage() {
    }
}
