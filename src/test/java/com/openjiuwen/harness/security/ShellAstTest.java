/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShellAstTest {

    @Test
    void parsesSimpleCommand() {
        ShellAstParseResult result = ShellAst.parseShellForPermission("echo hello");

        assertEquals("simple", result.getKind());
        assertEquals(1, result.getSubcommands().size());
        assertEquals("echo hello", result.getSubcommands().get(0).getText());
        assertEquals(2, result.getSubcommands().get(0).getArgv().size());
    }

    @Test
    void parsesPipelineIntoMultipleSubcommands() {
        ShellAstParseResult result = ShellAst.parseShellForPermission("cat foo.txt | grep bar");

        assertEquals("simple", result.getKind());
        assertEquals(2, result.getSubcommands().size());
        assertTrue(result.getFlags().hasPipeline());
        assertTrue(result.getFlags().getOperators().contains("|"));
    }

    @Test
    void marksCommandSubstitutionAsTooComplex() {
        ShellAstParseResult result = ShellAst.parseShellForPermission("echo $(whoami)");

        assertEquals("too_complex", result.getKind());
        assertTrue(result.getFlags().hasCommandSubstitution());
    }

    @Test
    void keepsRedirectionFlagsForSimpleCommand() {
        ShellAstParseResult result = ShellAst.parseShellForPermission("cat < input.txt > output.txt");

        assertEquals("simple", result.getKind());
        assertTrue(result.getFlags().hasInputRedirection());
        assertTrue(result.getFlags().hasOutputRedirection());
    }

    @Test
    void unmatchedQuotesBecomeParseUnavailable() {
        ShellAstParseResult result = ShellAst.parseShellForPermission("echo \"unterminated");

        assertEquals("parse_unavailable", result.getKind());
    }
}
