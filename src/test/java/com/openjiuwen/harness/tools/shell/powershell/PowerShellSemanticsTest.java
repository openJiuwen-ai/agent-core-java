/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PowerShellSemanticsTest {

    @Test
    void splitPipelineIgnoresNestedOperatorsInsideScriptBlocksAndQuotes() {
        String command = "Get-ChildItem | Select-Object @{Name='x';Expression={ $_ -replace ';', '|' }} ; "
                + "Get-Content file.txt";

        assertIterableEquals(
                List.of(
                        "Get-ChildItem",
                        "Select-Object @{Name='x';Expression={ $_ -replace ';', '|' }}",
                        "Get-Content file.txt"
                ),
                PowerShellSemantics.splitPipeline(command)
        );
    }

    @Test
    void extractBaseCommandSkipsAssignmentsAndNormalizesExecutableName() {
        assertEquals("pwsh", PowerShellSemantics.extractBaseCommand("$env:FOO=1 C:/Tools/pwsh.exe -NoProfile"));
    }

    @Test
    void isReadOnlyAcceptsReadLikeAliasesAndNeutralCommands() {
        assertTrue(PowerShellSemantics.isReadOnly("Get-Content foo | Select Name | Write-Output"));
    }

    @Test
    void isReadOnlyRejectsMutatingCommands() {
        assertFalse(PowerShellSemantics.isReadOnly("Get-Content foo | Set-Content bar"));
    }

    @Test
    void isSilentRequiresOnlySilentCommands() {
        assertTrue(PowerShellSemantics.isSilent("Set-Location tmp; Push-Location other; Pop-Location"));
        assertFalse(PowerShellSemantics.isSilent("Set-Location tmp; Get-Content foo"));
    }

    @Test
    void interpretExitCodeTreatsSearchExitOneWithoutOutputAsNoMatch() {
        ExitCodeMeaning meaning = PowerShellSemantics.interpretExitCode("Select-String needle", 1, "", "");

        assertFalse(meaning.isError());
        assertEquals("No matches found", meaning.message());
    }

    @Test
    void interpretExitCodeTreatsReadOnlyPartialOutputAsNonFatal() {
        ExitCodeMeaning meaning = PowerShellSemantics.interpretExitCode("Get-ChildItem tmp", 1, "foo.txt", "");

        assertFalse(meaning.isError());
        assertEquals("Partial results produced; some items may be inaccessible", meaning.message());
    }

    @Test
    void interpretExitCodeUsesReadOnlyFallbackForUnknownCommandsWithOutput() {
        ExitCodeMeaning meaning = PowerShellSemantics.interpretExitCode("Get-FileHash foo.txt", 1, "hash", "");

        assertFalse(meaning.isError());
        assertEquals(
                "PowerShell returned exit code 1 after producing output; treating output as partial result",
                meaning.message()
        );
    }
}
