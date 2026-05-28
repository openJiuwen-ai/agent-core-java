/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

/**
 * CLI entry point constants and version info.
 * <p>
 * Mirrors Python's {@code cli} in
 * {@code openjiuwen.harness.cli.cli}.
 */
public final class CliConstants {

    private CliConstants() {
    }

    public static final String VERSION = "0.1.12";
    public static final String APP_NAME = "openjiuwen";

    /** CLI commands. */
    public static final String CMD_CHAT = "chat";
    public static final String CMD_RUN = "run";
}
