/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.test_bash;

import com.openjiuwen.harness.tools.shell.bash.BashOutputUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Output.
 * <p>
 * Mirrors Python's {@code test_output.py} from
 * {@code tests/unit_tests/harness/tools/test_bash/test_output.py}.
 *
 * <p>Tests the truncate_output function behavior.
 */
@DisplayName("Output Tests")
class TestOutput {

    @Nested
    @DisplayName("Truncate Output Tests")
    class TruncateOutputTests {

        @Test
        @DisplayName("test short text unchanged")
        void testShortTextUnchanged() {
            // Python: test_short_text_unchanged
            String text = "hello world";
            String result = BashOutputUtils.truncateOutput(text, 1000);
            
            assertEquals(text, result);
        }

        @Test
        @DisplayName("test exact limit unchanged")
        void testExactLimitUnchanged() {
            // Python: test_exact_limit_unchanged
            String text = "x".repeat(100);
            String result = BashOutputUtils.truncateOutput(text, 100);
            
            assertEquals(text, result);
        }

        @Test
        @DisplayName("test long text has gap marker")
        void testLongTextHasGapMarker() {
            // Python: test_long_text_has_gap_marker
            String text = "x".repeat(500);
            String result = BashOutputUtils.truncateOutput(text, 250);
            
            assertTrue(result.contains("lines omitted"));
        }

        @Test
        @DisplayName("test head and tail preserved")
        void testHeadAndTailPreserved() {
            // Python: test_head_and_tail_preserved
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("line-" + i).append("\n");
            }
            String text = sb.toString();
            String result = BashOutputUtils.truncateOutput(text, 200);
            
            assertTrue(result.startsWith("line-0"));
            assertTrue(result.contains("line-99"));
            assertTrue(result.contains("lines omitted"));
        }

        @Test
        @DisplayName("test total length reasonable")
        void testTotalLengthReasonable() {
            // Python: test_total_length_reasonable
            String text = "x".repeat(500);
            String result = BashOutputUtils.truncateOutput(text, 250);
            
            // head(200) + gap marker + tail(50) + newlines — should be in reasonable range
            assertTrue(result.length() < 300);
        }

        @Test
        @DisplayName("test empty text")
        void testEmptyText() {
            // Python: test_empty_text
            String result = BashOutputUtils.truncateOutput("", 100);
            
            assertEquals("", result);
        }

        @Test
        @DisplayName("test custom head ratio")
        void testCustomHeadRatio() {
            // Python: test_custom_head_ratio
            String text = "A".repeat(300) + "B".repeat(300);
            String result = BashOutputUtils.truncateOutput(text, 200, 0.5);
            
            assertTrue(result.startsWith("A"));
            assertTrue(result.endsWith("B".repeat(100)));
        }

        @Test
        @DisplayName("test multiline omitted count")
        void testMultilineOmittedCount() {
            // Python: test_multiline_omitted_count
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                sb.append("L" + i).append("\n");
            }
            String text = sb.toString();
            String result = BashOutputUtils.truncateOutput(text, 60);
            
            assertTrue(result.contains("lines omitted"));
        }
    }
}