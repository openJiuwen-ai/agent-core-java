/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class BashSemanticsTest {

    @Test
    void splitPipelineSplitsOnShellOperators() {
        assertIterableEquals(
                List.of("FOO=bar /usr/bin/grep needle file.txt", "cat file.txt", "printf done"),
                BashSemantics.splitPipeline("FOO=bar /usr/bin/grep needle file.txt || cat file.txt; printf done")
        );
    }

    @Test
    void extractBaseCommandSkipsAssignmentsAndNormalizesExecutableName() {
        assertEquals("grep", BashSemantics.extractBaseCommand("FOO=1 C:\\Tools\\grep.exe needle file.txt"));
    }

    @Test
    void isReadOnlyAcceptsReadLikeCommandsAndNeutralCommands() {
        assertTrue(BashSemantics.isReadOnly("rg needle file.txt | cat | echo"));
    }

    @Test
    void isReadOnlyRejectsMutatingCommands() {
        assertFalse(BashSemantics.isReadOnly("cat file.txt | rm other.txt"));
    }

    @Test
    void isSilentRequiresOnlySilentCommands() {
        assertTrue(BashSemantics.isSilent("cd tmp; mkdir work; touch work/file.txt"));
        assertFalse(BashSemantics.isSilent("cd tmp; cat work/file.txt"));
    }

    @Test
    void interpretExitCodeTreatsSearchExitOneAsNoMatch() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("rg needle file.txt", 1, "", "");

        assertFalse(meaning.isError());
        assertEquals("No matches found", meaning.message());
    }

    @Test
    void interpretExitCodeTreatsPowershellReadExitOneWithoutStderrAsNoOutput() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("Get-Content README.md", 1, "", "");

        assertFalse(meaning.isError());
        assertEquals("No output returned", meaning.message());
    }

    @Test
    void interpretExitCodeUsesDiffSemantics() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("diff a b", 1, "", "");

        assertFalse(meaning.isError());
        assertEquals("Files differ", meaning.message());
    }
}
