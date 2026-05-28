/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.test_bash;

import com.openjiuwen.harness.tools.shell.bash.BashSemanticsUtils;
import com.openjiuwen.harness.tools.shell.bash.CommandKind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Semantics.
 * <p>
 * Mirrors Python's {@code test_semantics.py} from
 * {@code tests/unit_tests/harness/tools/test_bash/test_semantics.py}.
 *
 * <p>Tests command classification and exit code semantic interpretation.
 */
@DisplayName("Semantics Tests")
class TestSemantics {

    @Nested
    @DisplayName("Classify Command Tests")
    class ClassifyCommandTests {

        @Test
        @DisplayName("test grep classified as search")
        void testGrepClassifiedAsSearch() {
            // Python: test_search - grep
            CommandKind kind = BashSemanticsUtils.classifyCommand("grep -r foo .");
            
            assertEquals(CommandKind.SEARCH, kind);
        }

        @Test
        @DisplayName("test rg classified as search")
        void testRgClassifiedAsSearch() {
            // Python: test_search - rg
            CommandKind kind = BashSemanticsUtils.classifyCommand("rg pattern");
            
            assertEquals(CommandKind.SEARCH, kind);
        }

        @Test
        @DisplayName("test find classified as search")
        void testFindClassifiedAsSearch() {
            // Python: test_search - find
            CommandKind kind = BashSemanticsUtils.classifyCommand("find . -name '*.py'");
            
            assertEquals(CommandKind.SEARCH, kind);
        }

        @Test
        @DisplayName("test cat classified as read")
        void testCatClassifiedAsRead() {
            // Python: test_read - cat
            CommandKind kind = BashSemanticsUtils.classifyCommand("cat foo.txt");
            
            assertEquals(CommandKind.READ, kind);
        }

        @Test
        @DisplayName("test head classified as read")
        void testHeadClassifiedAsRead() {
            // Python: test_read - head
            CommandKind kind = BashSemanticsUtils.classifyCommand("head -20 file.log");
            
            assertEquals(CommandKind.READ, kind);
        }

        @Test
        @DisplayName("test wc classified as read")
        void testWcClassifiedAsRead() {
            // Python: test_read - wc
            CommandKind kind = BashSemanticsUtils.classifyCommand("wc -l *.py");
            
            assertEquals(CommandKind.READ, kind);
        }

        @Test
        @DisplayName("test ls classified as list")
        void testLsClassifiedAsList() {
            // Python: test_list - ls
            CommandKind kind = BashSemanticsUtils.classifyCommand("ls -la");
            
            assertEquals(CommandKind.LIST, kind);
        }

        @Test
        @DisplayName("test tree classified as list")
        void testTreeClassifiedAsList() {
            // Python: test_list - tree
            CommandKind kind = BashSemanticsUtils.classifyCommand("tree src/");
            
            assertEquals(CommandKind.LIST, kind);
        }

        @Test
        @DisplayName("test echo classified as neutral")
        void testEchoClassifiedAsNeutral() {
            // Python: test_neutral - echo
            CommandKind kind = BashSemanticsUtils.classifyCommand("echo hello");
            
            assertEquals(CommandKind.NEUTRAL, kind);
        }

        @Test
        @DisplayName("test true classified as neutral")
        void testTrueClassifiedAsNeutral() {
            // Python: test_neutral - true
            CommandKind kind = BashSemanticsUtils.classifyCommand("true");
            
            assertEquals(CommandKind.NEUTRAL, kind);
        }

        @Test
        @DisplayName("test mkdir classified as silent")
        void testMkdirClassifiedAsSilent() {
            // Python: test_silent - mkdir
            CommandKind kind = BashSemanticsUtils.classifyCommand("mkdir -p /tmp/foo");
            
            assertEquals(CommandKind.SILENT, kind);
        }

        @Test
        @DisplayName("test mv classified as silent")
        void testMvClassifiedAsSilent() {
            // Python: test_silent - mv
            CommandKind kind = BashSemanticsUtils.classifyCommand("mv a.txt b.txt");
            
            assertEquals(CommandKind.SILENT, kind);
        }

        @Test
        @DisplayName("test chmod classified as silent")
        void testChmodClassifiedAsSilent() {
            // Python: test_silent - chmod
            CommandKind kind = BashSemanticsUtils.classifyCommand("chmod 755 script.sh");
            
            assertEquals(CommandKind.SILENT, kind);
        }

        @Test
        @DisplayName("test pipeline uses last segment")
        void testPipelineUsesLastSegment() {
            // Python: test_pipeline_uses_last_segment
            CommandKind kind1 = BashSemanticsUtils.classifyCommand("cat foo | grep bar");
            CommandKind kind2 = BashSemanticsUtils.classifyCommand("grep foo | wc -l");
            
            assertEquals(CommandKind.SEARCH, kind1);
            assertEquals(CommandKind.READ, kind2);
        }

        @Test
        @DisplayName("test unknown command")
        void testUnknownCommand() {
            // Python: test_unknown_command
            CommandKind kind = BashSemanticsUtils.classifyCommand("docker build .");
            
            assertEquals(CommandKind.OTHER, kind);
        }

        @Test
        @DisplayName("test empty command")
        void testEmptyCommand() {
            // Python: test_empty
            CommandKind kind = BashSemanticsUtils.classifyCommand("");
            
            assertEquals(CommandKind.OTHER, kind);
        }
    }

    @Nested
    @DisplayName("Is Read Only Tests")
    class IsReadOnlyTests {

        @Test
        @DisplayName("test grep is read only")
        void testGrepIsReadOnly() {
            // Python: test_read_only commands
            assertTrue(BashSemanticsUtils.isReadOnly("grep foo"));
        }

        @Test
        @DisplayName("test cat is read only")
        void testCatIsReadOnly() {
            assertTrue(BashSemanticsUtils.isReadOnly("cat foo.txt"));
        }

        @Test
        @DisplayName("test ls is read only")
        void testLsIsReadOnly() {
            assertTrue(BashSemanticsUtils.isReadOnly("ls -la"));
        }

        @Test
        @DisplayName("test rm is not read only")
        void testRmIsNotReadOnly() {
            assertFalse(BashSemanticsUtils.isReadOnly("rm foo.txt"));
        }
    }

    @Nested
    @DisplayName("Is Silent Tests")
    class IsSilentTests {

        @Test
        @DisplayName("test mkdir is silent")
        void testMkdirIsSilent() {
            // Python: test_silent commands
            assertTrue(BashSemanticsUtils.isSilent("mkdir -p foo"));
        }

        @Test
        @DisplayName("test mv is silent")
        void testMvIsSilent() {
            assertTrue(BashSemanticsUtils.isSilent("mv a.txt b.txt"));
        }

        @Test
        @DisplayName("test echo is not silent")
        void testEchoIsNotSilent() {
            assertFalse(BashSemanticsUtils.isSilent("echo hello"));
        }
    }
}