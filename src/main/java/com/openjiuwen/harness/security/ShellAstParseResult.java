/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.List;

/**
 * Mirrors Python's {@code ShellAstParseResult} in
 * {@code openjiuwen/harness/security/shell_ast.py}.
 */
public final class ShellAstParseResult {

    private final String kind;
    private final List<ShellSubcommand> subcommands;
    private final ShellStructureFlags flags;
    private final String reason;
    private final String backend;

    public ShellAstParseResult(String kind) {
        this(kind, List.of(), new ShellStructureFlags(), null, "fallback");
    }

    public ShellAstParseResult(
            String kind,
            List<ShellSubcommand> subcommands,
            ShellStructureFlags flags,
            String reason,
            String backend
    ) {
        this.kind = kind;
        this.subcommands = subcommands == null ? List.of() : List.copyOf(subcommands);
        this.flags = flags == null ? new ShellStructureFlags() : flags;
        this.reason = reason;
        this.backend = backend == null ? "fallback" : backend;
    }

    public String getKind() {
        return kind;
    }

    public List<ShellSubcommand> getSubcommands() {
        return subcommands;
    }

    public ShellStructureFlags getFlags() {
        return flags;
    }

    public String getReason() {
        return reason;
    }

    public String getBackend() {
        return backend;
    }
}
