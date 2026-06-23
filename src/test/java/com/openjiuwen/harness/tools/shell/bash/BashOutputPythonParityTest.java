/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/tools/test_bash/test_output.py}.
 */
class BashOutputPythonParityTest {

    @Test
    void shortTextUnchanged() {
        String text = "hello world";

        assertEquals(text, BashOutput.truncateOutput(text, 1000));
    }

    @Test
    void exactLimitUnchanged() {
        String text = "x".repeat(100);

        assertEquals(text, BashOutput.truncateOutput(text, 100));
    }

    @Test
    void longTextHasGapMarker() {
        String text = "x".repeat(500);

        String result = BashOutput.truncateOutput(text, 250);

        assertTrue(result.contains("lines omitted"));
    }

    @Test
    void headAndTailPreserved() {
        String text = IntStream.range(0, 100)
                .mapToObj(index -> "line-" + index)
                .collect(Collectors.joining("\n"));

        String result = BashOutput.truncateOutput(text, 200);

        assertTrue(result.startsWith("line-0"));
        assertTrue(result.contains("line-99"));
        assertTrue(result.contains("lines omitted"));
    }

    @Test
    void totalLengthReasonable() {
        String text = "x".repeat(500);

        String result = BashOutput.truncateOutput(text, 250);

        assertTrue(result.length() < 300);
    }

    @Test
    void emptyText() {
        assertEquals("", BashOutput.truncateOutput("", 100));
    }

    @Test
    void customHeadRatio() {
        String text = "A".repeat(300) + "B".repeat(300);

        String result = BashOutput.truncateOutput(text, 200, 0.5);

        assertTrue(result.startsWith("A"));
        assertTrue(result.endsWith("B".repeat(100)));
    }

    @Test
    void multilineOmittedCount() {
        String text = IntStream.range(0, 50)
                .mapToObj(index -> "L" + index)
                .collect(Collectors.joining("\n"));

        String result = BashOutput.truncateOutput(text, 60);

        assertTrue(result.contains("lines omitted"));
    }
}
