/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.shellast;

import java.util.ArrayList;
import java.util.List;

/**
 * POSIX-style shell word splitter used by the conservative scanner.
 *
 * <p>Handles single/double quotes and backslash escaping well enough to recover argv
 * for simple commands. Unterminated quotes cause an {@link IllegalArgumentException}
 * so the scanner can degrade to {@code parse_unavailable}. Compound structures
 * (pipes, redirections, substitutions) are detected earlier by the scanner and never
 * reach this lexer.
 *
 * <p><b>Platform-aware backslash handling</b>: On Windows (cmd.exe), backslash is a
 * path separator, not a POSIX escape character. Treating it as an escape would strip
 * all backslashes from Windows paths (e.g. {@code C:\srv\workspace\secrets\*.txt}
 * becomes {@code C:srvworkspacesecrets*.txt}), breaking file_guard prefix matching.
 * On Windows, backslash is preserved as a literal character; on Linux/macOS, it retains
 * its POSIX escape semantics.
 *
 * @since 0.1.15
 */
final class ShellLexer {
    private ShellLexer() {
    }

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * Split a command into argv tokens.
     *
     * @param command command text
     * @return argv list
     * @throws IllegalArgumentException when a quote is unterminated
     * @since 0.1.15
     */
    static List<String> split(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean hasContent = false;
        int i = 0;
        int n = command.length();
        while (i < n) {
            char c = command.charAt(i);
            if (Character.isWhitespace(c)) {
                if (hasContent) {
                    tokens.add(word.toString());
                    word.setLength(0);
                    hasContent = false;
                }
                i++;
                continue;
            }
            if (c == '\'') {
                i = parseSingleQuote(command, i, n, word);
                hasContent = true;
                continue;
            }
            if (c == '"') {
                i = parseDoubleQuote(command, i, n, word);
                hasContent = true;
                continue;
            }
            if (c == '\\' && !IS_WINDOWS) {
                // POSIX escape: consume backslash, keep next char literally.
                if (i + 1 < n) {
                    word.append(command.charAt(i + 1));
                    hasContent = true;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            // On Windows (or for any literal backslash), keep it as-is.
            word.append(c);
            hasContent = true;
            i++;
        }
        if (hasContent) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    private static int parseSingleQuote(String command, int start, int n, StringBuilder word) {
        int pos = start + 1;
        while (pos < n && command.charAt(pos) != '\'') {
            word.append(command.charAt(pos));
            pos++;
        }
        if (pos >= n) {
            throw new IllegalArgumentException("unterminated single quote");
        }
        return pos + 1;
    }

    private static int parseDoubleQuote(String command, int start, int n, StringBuilder word) {
        int pos = start + 1;
        while (pos < n && command.charAt(pos) != '"') {
            char d = command.charAt(pos);
            if (d == '\\' && pos + 1 < n && !IS_WINDOWS) {
                // POSIX double-quote escape: only ", \, $, `, \n are escapable.
                char next = command.charAt(pos + 1);
                if (next == '"' || next == '\\' || next == '$' || next == '`' || next == '\n') {
                    word.append(next);
                    pos += 2;
                    continue;
                }
            }
            // On Windows or for non-escapable chars, keep the backslash literally.
            word.append(d);
            pos++;
        }
        if (pos >= n) {
            throw new IllegalArgumentException("unterminated double quote");
        }
        return pos + 1;
    }
}
