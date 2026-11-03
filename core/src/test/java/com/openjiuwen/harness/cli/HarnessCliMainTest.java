/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.cli;

import com.openjiuwen.harness.cli.ui.CliRunner;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
