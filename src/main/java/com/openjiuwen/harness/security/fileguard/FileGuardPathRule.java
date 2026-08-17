/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import com.openjiuwen.harness.security.PermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single compiled path rule for the file-guard pipeline.
 *
 * <p>Mirrors Python {@code file_guard.FileGuardPathRule}. The {@code path} is stored
 * posix-normalized for prefix rules and verbatim for glob rules. {@code match} is
 * {@code "prefix"} (default) or {@code "glob"}. Axis levels may be {@code null} when
 * a caller builds a rule by hand; the normalizer always fills them so the checker
 * can apply the Write/Exec&#x21d2;Read implication deterministically.
 *
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileGuardPathRule {
    /** Normalized path (prefix rules) or glob pattern (glob rules). */
    private String path;
    /** Level for the read axis; {@code null} means unspecified. */
    private PermissionLevel read;
    /** Level for the write axis; {@code null} means unspecified. */
    private PermissionLevel write;
    /** Level for the exec axis; {@code null} means unspecified. */
    private PermissionLevel exec;
    /** Match strategy: {@code "prefix"} (default) or {@code "glob"}. */
    @Builder.Default
    private String match = "prefix";

    /**
     * Resolve the axis level for the given action.
     *
     * @param action file-access axis
     * @return the configured level, or {@code null} when unspecified
     * @since 0.1.15
     */
    public PermissionLevel levelFor(FileGuardAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case WRITE -> write;
            case EXEC -> exec;
            case READ -> read;
        };
    }
}
