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
 * @since 0.1.15
 */
final class ShellLexer {

    private ShellLexer() {
    }

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
                i++;
                hasContent = true;
                while (i < n && command.charAt(i) != '\'') {
                    word.append(command.charAt(i));
                    i++;
                }
                if (i >= n) {
                    throw new IllegalArgumentException("unterminated single quote");
                }
                i++;
                continue;
            }
            if (c == '"') {
                i++;
                hasContent = true;
                while (i < n && command.charAt(i) != '"') {
                    char d = command.charAt(i);
                    if (d == '\\' && i + 1 < n) {
                        char next = command.charAt(i + 1);
                        if (next == '"' || next == '\\' || next == '$' || next == '`' || next == '\n') {
                            word.append(next);
                            i += 2;
                            continue;
                        }
                    }
                    word.append(d);
                    i++;
                }
                if (i >= n) {
                    throw new IllegalArgumentException("unterminated double quote");
                }
                i++;
                continue;
            }
            if (c == '\\') {
                if (i + 1 < n) {
                    word.append(command.charAt(i + 1));
                    hasContent = true;
                    i += 2;
                    continue;
                }
                i++;
                continue;
            }
            word.append(c);
            hasContent = true;
            i++;
        }
        if (hasContent) {
            tokens.add(word.toString());
        }
        return tokens;
    }
}
