/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.harness.cli.ui.CliRunner;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.harness.cli.__main__} in
 * {@code openjiuwen/harness/cli/__main__.py}.
 */
class HarnessCliMainTest {

    @Test
    void exposesModuleEntrypoint() throws NoSuchMethodException {
        assertEquals("openjiuwen/harness/cli/__main__.py", HarnessCliMain.PYTHON_MODULE);
        assertNotNull(HarnessCliMain.class.getMethod("main", String[].class));
    }

    @Test
    void runCommandDelegatesToTranslatedCliFacade() {
        RecordingRunner runner = new RecordingRunner();

        int code = HarnessCliMain.run(
                new String[]{"run", "hello", "--output-format", "json"},
                true,
                () -> "ignored",
                runner
        );

        assertEquals(23, code);
        assertEquals("hello", runner.prompt);
        assertEquals(CliRunner.OUTPUT_JSON, runner.outputFormat);
    }

    @Test
    void pipedDefaultUsesRunCommandPromptResolution() {
        RecordingRunner runner = new RecordingRunner();

        int code = HarnessCliMain.run(new String[]{}, false, () -> " piped prompt\n", runner);

        assertEquals(23, code);
        assertEquals("piped prompt", runner.prompt);
        assertEquals(CliRunner.OUTPUT_TEXT, runner.outputFormat);
    }

    @Test
    void invalidOutputFormatIsRejectedBeforeTheRun() {
        RecordingRunner runner = new RecordingRunner();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> HarnessCliMain.run(
                new String[]{"run", "hello", "--output-format", "xml"},
                true,
                () -> "ignored",
                runner
        ));

        assertTrue(error.getMessage().contains("xml"));
        assertTrue(error.getMessage().contains("text"));
        assertNull(runner.prompt);
    }

    @Test
    void outputFormatWithoutValueIsRejectedInsteadOfBecomingPromptText() {
        RecordingRunner runner = new RecordingRunner();

        assertThrows(IllegalArgumentException.class, () -> HarnessCliMain.run(
                new String[]{"run", "hello", "-f"},
                true,
                () -> "ignored",
                runner
        ));

        assertNull(runner.prompt);
    }

    @Test
    void usageErrorsAreReportedWithClickExitCode() throws Exception {
        PrintStream originalError = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream stream = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setErr(stream);
            exitCode = HarnessCliMain.runSafely(new String[]{"bogus"});
        } finally {
            System.setErr(originalError);
        }

        assertEquals(2, exitCode);
        assertTrue(captured.toString(StandardCharsets.UTF_8).contains("Unknown command: bogus"));
    }

    static final class RecordingRunner extends CliRunner {
        String prompt;
        String outputFormat;

        @Override
        public int runOnce(Map<String, Object> config, String prompt, String outputFormat) {
            this.prompt = prompt;
            this.outputFormat = outputFormat;
            return 23;
        }
    }
}
