/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import java.util.Locale;
import java.util.Optional;

/**
 * File-access axis for the file-guard pipeline (Pipeline B).
 *
 * <p>Mirrors Python {@code file_guard.FileGuardAction}. Each path rule carries an
 * independent {@code read}/{@code write}/{@code exec} level; this enum selects which
 * axis applies to a given tool invocation so that, for example, {@code write_file}
 * evaluates the {@code WRITE} axis while {@code read_file} evaluates {@code READ}.
 *
 * @since 0.1.15
 */
public enum FileGuardAction {
    /** Read access (file inspection, listing, grep). */
    READ,
    /** Write access (create, edit, delete, rename). */
    WRITE,
    /** Execute access (run a script as an interpreter target). */
    EXEC;

    /**
     * Parse an axis from a config value.
     *
     * @param value raw value, typically {@code "read"}/{@code "write"}/{@code "exec"}
     * @return the matching axis, or empty when unrecognized
     * @since 0.1.15
     */
    public static Optional<FileGuardAction> fromString(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "read" -> Optional.of(READ);
            case "write" -> Optional.of(WRITE);
            case "exec" -> Optional.of(EXEC);
            default -> Optional.empty();
        };
    }
}
