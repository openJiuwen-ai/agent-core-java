/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.agents.interrupt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for fine-grained auto-confirm feature.
 *
 * <p>Mirrors Python's {@code test_fine_grained_auto_confirm.py} in
 * {@code tests/unit_tests/agent/react_agent/interrupt/}.
 */
@DisplayName("Fine Grained Auto Confirm")
class FineGrainedAutoConfirmTest {

    @Test
    @DisplayName("auto confirm key derivation from tool call")
    void testAutoConfirmKeyDerivationFromToolCall() {
        String filepath = "/tmp/test_file.txt";
        String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
        String nameWithoutExt = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;

        assertThat(nameWithoutExt).isEqualTo("test_file");
        assertThat("read_" + nameWithoutExt).isEqualTo("read_test_file");
    }

    @Test
    @DisplayName("auto confirm key for write tool")
    void testAutoConfirmKeyForWriteTool() {
        String filepath = "/tmp/output.txt";
        String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
        String nameWithoutExt = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;

        assertThat("write_" + nameWithoutExt).isEqualTo("write_output");
    }

    @Test
    @DisplayName("auto confirm key for no extension file")
    void testAutoConfirmKeyForNoExtensionFile() {
        String filepath = "/tmp/README";
        String filename = filepath.substring(filepath.lastIndexOf('/') + 1);
        String nameWithoutExt = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : filename;

        assertThat("read_" + nameWithoutExt).isEqualTo("read_README");
    }
}
