/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BashOutputTest {

    @Test
    void truncateOutputTreatsZeroAsUnlimited() {
        assertEquals("abcdef", BashOutput.truncateOutput("abcdef", 0));
    }

    @Test
    void persistLargeOutputReturnsStablePathAndByteCount() {
        BashOutput.PersistResult result = BashOutput.persistLargeOutput("hello", "stderr");

        assertTrue(result.path().contains("openjiuwen_bash_outputs"));
        assertEquals("hello\n--- stderr ---\nstderr".getBytes().length, result.byteCount());
    }

    @Test
    void renderToolContentForErrorsPutsWarningAndExitCodeFirst() {
        BashOutput.CommandOutput output = new BashOutput.CommandOutput(
                "stdout line",
                "stderr line",
                7,
                "warning text",
                100
        );

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(output, true);

        assertTrue(rendered.isError());
        assertEquals("warning text\nExit code 7\nstderr line\nstdout line", rendered.content());
    }

    @Test
    void renderToolContentPersistsOversizedSuccessfulOutput() {
        String stdout = "\n\n" + "a".repeat(2500);
        BashOutput.CommandOutput output = new BashOutput.CommandOutput(stdout, "", 0, null, 100);

        BashOutput.RenderedContent rendered = BashOutput.renderToolContent(output, false);

        assertFalse(rendered.isError());
        assertTrue(rendered.content().startsWith("<persisted-output>\nOutput too large"));
        assertTrue(rendered.content().contains("Preview (first 2KB):"));
        assertTrue(rendered.content().contains("</persisted-output>"));
    }

    @Test
    void renderPartialOnFailureReturnsFailureHeaderWhenOutputExists() {
        BashOutput.CommandOutput output = new BashOutput.CommandOutput("stdout", "", 124, null, 100);

        String rendered = BashOutput.renderPartialOnFailure(output, "timeout");

        assertNotNull(rendered);
        assertEquals("timeout\nExit code 124\nstdout", rendered);
    }
}
