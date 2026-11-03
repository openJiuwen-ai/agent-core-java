/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.List;

/**
 * Mirrors Python's {@code ShellSubcommand} in
 * {@code openjiuwen/harness/security/shell_ast.py}.
 */
public final class ShellSubcommand {

    private final String text;
    private final List<String> argv;
    private final List<String> redirects;
    private final SourceSpan sourceSpan;
    private final List<String> parentOperators;

    public ShellSubcommand(
            String text,
            List<String> argv,
            List<String> redirects,
            SourceSpan sourceSpan,
            List<String> parentOperators
    ) {
        this.text = text == null ? "" : text;
        this.argv = argv == null ? List.of() : List.copyOf(argv);
        this.redirects = redirects == null ? List.of() : List.copyOf(redirects);
        this.sourceSpan = sourceSpan;
        this.parentOperators = parentOperators == null ? List.of() : List.copyOf(parentOperators);
    }

    public String getText() {
        return text;
    }

    public List<String> getArgv() {
        return argv;
    }

    public List<String> getRedirects() {
        return redirects;
    }

    public SourceSpan getSourceSpan() {
        return sourceSpan;
    }

    public List<String> getParentOperators() {
        return parentOperators;
    }

    /**
     * Mirrors Python's source span tuple in
     * {@code openjiuwen/harness/security/shell_ast.py}.
     */
    public record SourceSpan(int start, int end) {
    }
}
