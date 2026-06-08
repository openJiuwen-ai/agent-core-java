/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

/**
 * Semantic meaning of a process exit code.
 *
 * <p>Mirrors Python's {@code ExitCodeMeaning} in
 * {@code openjiuwen/harness/tools/shell/powershell/_semantics.py}.
 */
public record ExitCodeMeaning(boolean isError, String message) {

    public ExitCodeMeaning(boolean isError) {
        this(isError, null);
    }

    public String getMessage() {
        return message;
    }
}
