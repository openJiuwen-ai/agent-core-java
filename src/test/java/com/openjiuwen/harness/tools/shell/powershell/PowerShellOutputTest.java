/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PowerShellOutputTest {

    @Test
    void truncateOutputTreatsZeroAsUnlimited() {
        assertEquals("abcdef", PowerShellOutput.truncateOutput("abcdef", 0));
    }

    @Test
    void persistLargeOutputReturnsStablePathAndByteCount() {
        PowerShellOutput.PersistResult result = PowerShellOutput.persistLargeOutput("hello", "stderr");

        assertTrue(result.path().contains("openjiuwen_powershell_outputs"));
        assertEquals("hello\n--- stderr ---\nstderr".getBytes().length, result.byteCount());
    }

    @Test
    void renderToolContentForErrorsPutsWarningAndExitCodeFirst() {
        PowerShellOutput.CommandOutput output = new PowerShellOutput.CommandOutput(
                "stdout line",
                "stderr line",
                7,
                "warning text",
                100
        );

        PowerShellOutput.RenderedContent rendered = PowerShellOutput.renderToolContent(output, true);

        assertTrue(rendered.isError());
        assertEquals("warning text\nExit code 7\nstderr line\nstdout line", rendered.content());
    }

    @Test
    void renderToolContentPersistsOversizedSuccessfulOutput() {
        String stdout = "\n\n" + "a".repeat(2500);
        PowerShellOutput.CommandOutput output = new PowerShellOutput.CommandOutput(stdout, "", 0, null, 100);

        PowerShellOutput.RenderedContent rendered = PowerShellOutput.renderToolContent(output, false);

        assertFalse(rendered.isError());
        assertTrue(rendered.content().startsWith("<persisted-output>\nOutput too large"));
        assertTrue(rendered.content().contains("Preview (first 2KB):"));
        assertTrue(rendered.content().contains("</persisted-output>"));
    }

    @Test
    void renderPartialOnFailureReturnsFailureHeaderWhenOutputExists() {
        PowerShellOutput.CommandOutput output = new PowerShellOutput.CommandOutput("stdout", "", 124, null, 100);

        String rendered = PowerShellOutput.renderPartialOnFailure(output, "timeout");

        assertNotNull(rendered);
        assertEquals("timeout\nExit code 124\nstdout", rendered);
    }
}
