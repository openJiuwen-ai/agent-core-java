/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

/**
 * Semantic meaning of a process exit code.
 *
 * <p>Mirrors Python's ExitCodeMeaning in
 * {@code openjiuwen.harness.tools.shell.powershell._semantics}.
 */
public class ExitCodeMeaning {

    private final boolean isError;
    private final String message;

    public ExitCodeMeaning(boolean isError, String message) {
        this.isError = isError;
        this.message = message;
    }

    public ExitCodeMeaning(boolean isError) {
        this.isError = isError;
        this.message = null;
    }

    public boolean isError() {
        return isError;
    }

    public String getMessage() {
        return message;
    }
}