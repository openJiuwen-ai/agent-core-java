/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.Optional;

/**
 * A single compiled path rule for the file-guard pipeline.
 *
 * <p>Mirrors Python {@code file_guard.FileGuardPathRule}. The {@code path} is stored
 * posix-normalized for prefix rules and verbatim for glob rules. {@code match} is
 * {@code "prefix"} (default) or {@code "glob"}. Axis levels may be {@code null} when
 * a caller builds a rule by hand; the normalizer always fills them so the checker
 * can apply the Write/Exec&#x21d2;Read implication deterministically.
 *
 * @since 0.1.14
 */
public final class FileGuardPathRule {

    /** Match strategy for prefix rules. */
    public static final String MATCH_PREFIX = "prefix";

    /** Match strategy for glob rules. */
    public static final String MATCH_GLOB = "glob";

    private final String path;
    private final PermissionLevel read;
    private final PermissionLevel write;
    private final PermissionLevel exec;
    private final String match;

    public FileGuardPathRule(String path, PermissionLevel read, PermissionLevel write, PermissionLevel exec) {
        this(path, read, write, exec, MATCH_PREFIX);
    }

    public FileGuardPathRule(String path, PermissionLevel read, PermissionLevel write, PermissionLevel exec,
            String match) {
        this.path = path;
        this.read = read;
        this.write = write;
        this.exec = exec;
        this.match = match == null ? MATCH_PREFIX : match;
    }

    /**
     * Normalized path (prefix rules) or glob pattern (glob rules).
     *
     * @return rule path or glob
     */
    public String getPath() {
        return path;
    }

    /**
     * Level for the read axis; {@code null} means unspecified.
     *
     * @return read permission level
     */
    public PermissionLevel getRead() {
        return read;
    }

    /**
     * Level for the write axis; {@code null} means unspecified.
     *
     * @return write permission level
     */
    public PermissionLevel getWrite() {
        return write;
    }

    /**
     * Level for the exec axis; {@code null} means unspecified.
     *
     * @return exec permission level
     */
    public PermissionLevel getExec() {
        return exec;
    }

    /**
     * Match strategy: {@code "prefix"} (default) or {@code "glob"}.
     *
     * @return match strategy
     */
    public String getMatch() {
        return match;
    }

    /**
     * Resolve the axis level for the given action.
     *
     * @param action file-access axis
     * @return the configured level, or empty when unspecified
     */
    public Optional<PermissionLevel> levelFor(FileGuardAction action) {
        if (action == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (action) {
            case WRITE -> write;
            case EXEC -> exec;
            case READ -> read;
        });
    }
}
