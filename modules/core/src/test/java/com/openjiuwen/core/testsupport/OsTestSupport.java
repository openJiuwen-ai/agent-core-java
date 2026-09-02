/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.testsupport;

import org.junit.jupiter.api.Assumptions;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Shared OS helpers for unit/compatibility tests that must run on both Windows and Linux.
 *  *
 * @since 0.1.7
 */
public final class OsTestSupport {
    /**
     * OsTestSupport
     *
     * @since 0.1.7
     */
    private OsTestSupport() {
    }

    /**
     * isWindows
     *
     * @return whether is windows
     *
     * @since 0.1.7
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Print working directory (cmd {@code cd} on Windows, {@code pwd} on Unix).
     *
     * @return cwd command string
     *
     * @since 0.1.7
     */
    public static String cwdCommand() {
        return isWindows() ? "cd" : "pwd";
    }

    /** Short background wait suitable for process-alive checks.
     *
     * @return short background wait command
     *
     * @since 0.1.7
     */
    public static String shortBackgroundWaitCommand() {
        return isWindows() ? "ping -n 4 127.0.0.1" : "sleep 2";
    }

    /** echoCommand
     *
     * @param text String
     * @return echo command
     *
     * @since 0.1.7
     */
    public static String echoCommand(String text) {
        return isWindows() ? "echo " + text : "echo " + text;
    }

    /**
     * Preferred python launcher for the current OS, preferring an executable that exists on PATH.
     * Linux VMs often have only {@code python3}; Windows typically has {@code python.exe}.
     */
    public static String pythonExecutable() {
        for (String candidate : pythonCandidates()) {
            if (isExecutableOnPath(candidate)) {
                return candidate;
            }
        }
        return isWindows() ? "python.exe" : "python3";
    }

    /**
     * True if any common python launcher ({@code python3}/{@code python}/Windows variants) is on PATH.
     */
    public static boolean isPythonAvailable() {
        for (String candidate : pythonCandidates()) {
            if (isExecutableOnPath(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Skip the current test when no python interpreter is installed. */
    public static void assumePythonAvailable() {
        Assumptions.assumeTrue(isPythonAvailable(), "Python/python3 not found, skipping");
    }

    private static String[] pythonCandidates() {
        return isWindows()
                ? new String[] {"python.exe", "python", "python3.exe", "python3"}
                : new String[] {"python3", "python"};
    }

    /** True if {@code bash} is available (needed by BashTool / CIGateRunner)
     *
     * @return is bash available
     *
     * @since 0.1.7
     */
    public static boolean isBashAvailable() {
        return isExecutableOnPath(isWindows() ? "bash.exe" : "bash");
    }

    /** True if {@code git} is available on PATH (needed by worktree / GitOperations tests)
     *
     * @return is git available
     *
     * @since 0.1.7
     */
    public static boolean isGitAvailable() {
        return isExecutableOnPath(isWindows() ? "git.exe" : "git");
    }

    /** Skip the current test when git is not installed
     *
     * @since 0.1.7
     */
    public static void assumeGitAvailable() {
        Assumptions.assumeTrue(isGitAvailable(), "git not found, skipping");
    }

    /** isExecutableOnPath
     *
     * @param exe exePath String
     * @return is executable on path
     *
     * @since 0.1.7
     */
    private static boolean isExecutableOnPath(String exe) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return false;
        }
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            java.io.File f = new java.io.File(dir, exe);
            if (f.isFile() && (isWindows() || f.canExecute())) {
                return true;
            }
        }
        return false;
    }

    /** Normalize path separators for assertion comparisons
     *
     * @param path raw path
     * @return normalized path
     *
     * @since 0.1.7
     */
    public static String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }

    /** pathContains
     *
     * @param haystack haystack
     * @param needle needlePath
     * @return whether path contains
     *
     * @since 0.1.7
     */
    public static boolean pathContains(String haystack, Path needle) {
        if (haystack == null || needle == null) {
            return false;
        }
        String h = normalizePath(haystack);
        String n = normalizePath(needle.toAbsolutePath().normalize().toString());
        return h.contains(n) || h.replace('/', '\\').contains(n.replace('/', '\\'));
    }

    /** destroyProcessTree
     *
     * @param pid pid
     *
     * @since 0.1.7
     */
    public static void destroyProcessTree(long pid) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            if (isWindows()) {
                try {
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start().waitFor();
                } catch (Exception ignored) {
                    // best effort
                }
            }
            handle.destroy();
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
            try {
                handle.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // ignore
            }
        });
    }
}
