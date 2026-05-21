/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Command classification and exit-code semantic interpretation.
 *
 * <p>Mirrors Python's functions in
 * {@code openjiuwen.harness.tools.shell.bash._semantics}.
 */
public final class BashSemanticsUtils {

    private static final Pattern OPERATOR_RE = Pattern.compile("\\s*(?:\\|\\||&&|[;|])\\s*");

    private static final Set<String> SEARCH_COMMANDS = new HashSet<>(Arrays.asList(
            "find", "grep", "egrep", "fgrep", "rg", "ag", "ack",
            "locate", "which", "whereis", "type", "command"
    ));

    private static final Set<String> READ_COMMANDS = new HashSet<>(Arrays.asList(
            "cat", "head", "tail", "less", "more", "wc", "stat",
            "file", "strings", "jq", "yq", "awk", "gawk", "cut",
            "sort", "uniq", "tr", "tee", "od", "xxd", "hexdump",
            "sha256sum", "sha1sum", "md5sum", "md5", "shasum"
    ));

    private static final Set<String> LIST_COMMANDS = new HashSet<>(Arrays.asList(
            "ls", "tree", "du", "df", "lsof", "lsblk"
    ));

    private static final Set<String> NEUTRAL_COMMANDS = new HashSet<>(Arrays.asList(
            "echo", "printf", "true", "false", ":", "test", "["
    ));

    private static final Set<String> SILENT_COMMANDS = new HashSet<>(Arrays.asList(
            "mv", "cp", "rm", "mkdir", "rmdir", "chmod", "chown",
            "chgrp", "touch", "ln", "cd", "export", "unset",
            "source", ".", "wait", "pushd", "popd"
    ));

    private static final Map<String, CommandKind> KIND_LOOKUP = new HashMap<>();

    private static final Set<CommandKind> READ_KINDS = new HashSet<>(Arrays.asList(
            CommandKind.SEARCH, CommandKind.READ, CommandKind.LIST
    ));

    private static final Map<String, Function<ExitCodeContext, ExitCodeMeaning>> SEMANTICS_TABLE = new HashMap<>();

    static {
        for (String cmd : SEARCH_COMMANDS) {
            KIND_LOOKUP.put(cmd, CommandKind.SEARCH);
        }
        for (String cmd : READ_COMMANDS) {
            KIND_LOOKUP.put(cmd, CommandKind.READ);
        }
        for (String cmd : LIST_COMMANDS) {
            KIND_LOOKUP.put(cmd, CommandKind.LIST);
        }
        for (String cmd : NEUTRAL_COMMANDS) {
            KIND_LOOKUP.put(cmd, CommandKind.NEUTRAL);
        }
        for (String cmd : SILENT_COMMANDS) {
            KIND_LOOKUP.put(cmd, CommandKind.SILENT);
        }

        Function<ExitCodeContext, ExitCodeMeaning> grepSemantics = ctx -> {
            if (ctx.code == 0) {
                return new ExitCodeMeaning(false);
            }
            if (ctx.code == 1) {
                return new ExitCodeMeaning(false, "No matches found");
            }
            return new ExitCodeMeaning(true, "grep error (exit " + ctx.code + ")");
        };

        Function<ExitCodeContext, ExitCodeMeaning> findSemantics = ctx -> {
            if (ctx.code == 0) {
                return new ExitCodeMeaning(false);
            }
            if (ctx.code == 1) {
                return new ExitCodeMeaning(false, "Some directories inaccessible");
            }
            return new ExitCodeMeaning(true, "find error (exit " + ctx.code + ")");
        };

        Function<ExitCodeContext, ExitCodeMeaning> diffSemantics = ctx -> {
            if (ctx.code == 0) {
                return new ExitCodeMeaning(false, "Files are identical");
            }
            if (ctx.code == 1) {
                return new ExitCodeMeaning(false, "Files differ");
            }
            return new ExitCodeMeaning(true, "diff error (exit " + ctx.code + ")");
        };

        Function<ExitCodeContext, ExitCodeMeaning> testSemantics = ctx -> {
            if (ctx.code == 0) {
                return new ExitCodeMeaning(false, "Condition is true");
            }
            if (ctx.code == 1) {
                return new ExitCodeMeaning(false, "Condition is false");
            }
            return new ExitCodeMeaning(true, "test error (exit " + ctx.code + ")");
        };

        SEMANTICS_TABLE.put("grep", grepSemantics);
        SEMANTICS_TABLE.put("egrep", grepSemantics);
        SEMANTICS_TABLE.put("fgrep", grepSemantics);
        SEMANTICS_TABLE.put("rg", grepSemantics);
        SEMANTICS_TABLE.put("ag", grepSemantics);
        SEMANTICS_TABLE.put("ack", grepSemantics);
        SEMANTICS_TABLE.put("find", findSemantics);
        SEMANTICS_TABLE.put("diff", diffSemantics);
        SEMANTICS_TABLE.put("test", testSemantics);
        SEMANTICS_TABLE.put("[", testSemantics);
    }

    private BashSemanticsUtils() {
    }

    /**
     * Split a command on shell operators (heuristic, not a full parser).
     *
     * @param command Shell command string
     * @return List of command segments
     */
    public static List<String> splitPipeline(String command) {
        String[] parts = OPERATOR_RE.split(command);
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Extract the executable name from a command segment.
     *
     * <p>Strips leading variable assignments (FOO=bar) and env prefixes,
     * then returns the basename of the first real token.
     *
     * @param segment Command segment
     * @return Base command name
     */
    public static String extractBaseCommand(String segment) {
        String[] tokens = segment.split("\\s+");
        for (String token : tokens) {
            if (token.contains("=") && !token.startsWith("-")) {
                continue;
            }
            int lastSlash = token.lastIndexOf('/');
            return lastSlash >= 0 ? token.substring(lastSlash + 1) : token;
        }
        return "";
    }

    /**
     * Classify the overall command by its last pipeline segment.
     *
     * <p>The last segment determines the final exit code, so that is the
     * one whose semantics matter most.
     *
     * @param command Shell command string
     * @return CommandKind classification
     */
    public static CommandKind classifyCommand(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return CommandKind.OTHER;
        }
        String base = extractBaseCommand(parts.get(parts.size() - 1));
        return KIND_LOOKUP.getOrDefault(base, CommandKind.OTHER);
    }

    /**
     * Return true when every non-neutral segment is a read-like command.
     *
     * @param command Shell command string
     * @return true if command is read-only
     */
    public static boolean isReadOnly(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return false;
        }
        for (String part : parts) {
            String base = extractBaseCommand(part);
            CommandKind kind = KIND_LOOKUP.getOrDefault(base, CommandKind.OTHER);
            if (kind == CommandKind.NEUTRAL) {
                continue;
            }
            if (!READ_KINDS.contains(kind)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return true when the command is expected to produce no stdout.
     *
     * @param command Shell command string
     * @return true if command is silent
     */
    public static boolean isSilent(String command) {
        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return false;
        }
        for (String part : parts) {
            String base = extractBaseCommand(part);
            CommandKind kind = KIND_LOOKUP.getOrDefault(base, CommandKind.OTHER);
            if (kind == CommandKind.NEUTRAL) {
                continue;
            }
            if (kind != CommandKind.SILENT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Interpret an exit code with command-specific semantics.
     *
     * <p>Falls back to the simple rule: exit_code != 0 means error.
     *
     * @param command  Shell command string
     * @param exitCode Process exit code
     * @param stdout   Standard output
     * @param stderr   Standard error
     * @return ExitCodeMeaning with semantic interpretation
     */
    public static ExitCodeMeaning interpretExitCode(String command, int exitCode, String stdout, String stderr) {
        if (exitCode == 0) {
            return new ExitCodeMeaning(false);
        }

        List<String> parts = splitPipeline(command);
        if (parts.isEmpty()) {
            return new ExitCodeMeaning(true);
        }
        String base = extractBaseCommand(parts.get(parts.size() - 1));
        Function<ExitCodeContext, ExitCodeMeaning> handler = SEMANTICS_TABLE.get(base);
        if (handler != null) {
            return handler.apply(new ExitCodeContext(exitCode, stdout, stderr));
        }
        return new ExitCodeMeaning(true);
    }

    public static ExitCodeMeaning interpretExitCode(String command, int exitCode) {
        return interpretExitCode(command, exitCode, "", "");
    }

    public static Set<String> getSearchCommands() {
        return SEARCH_COMMANDS;
    }

    public static Set<String> getReadCommands() {
        return READ_COMMANDS;
    }

    public static Set<String> getListCommands() {
        return LIST_COMMANDS;
    }

    public static Set<String> getNeutralCommands() {
        return NEUTRAL_COMMANDS;
    }

    public static Set<String> getSilentCommands() {
        return SILENT_COMMANDS;
    }

    /**
     * Context for exit code semantic interpretation.
     */
    public static final class ExitCodeContext {
        final int code;
        final String stdout;
        final String stderr;

        public ExitCodeContext(int code, String stdout, String stderr) {
            this.code = code;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}