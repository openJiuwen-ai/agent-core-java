/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.test_bash.test_semantics} in
 * {@code tests/unit_tests/harness/tools/test_bash/test_semantics.py}.</p>
 */
class BashSemanticsPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "TestClassifyCommand::test_search[grep -r foo .-CommandKind.SEARCH]",
            "TestClassifyCommand::test_search[rg pattern-CommandKind.SEARCH]",
            "TestClassifyCommand::test_search[find . -name '*.py'-CommandKind.SEARCH]",
            "TestClassifyCommand::test_search[/usr/bin/grep foo-CommandKind.SEARCH]",
            "TestClassifyCommand::test_read[cat foo.txt-CommandKind.READ]",
            "TestClassifyCommand::test_read[head -20 file.log-CommandKind.READ]",
            "TestClassifyCommand::test_read[wc -l *.py-CommandKind.READ]",
            "TestClassifyCommand::test_read[jq .name data.json-CommandKind.READ]",
            "TestClassifyCommand::test_read[Select-Object Name, Length-CommandKind.READ]",
            "TestClassifyCommand::test_list[ls -la-CommandKind.LIST]",
            "TestClassifyCommand::test_list[Get-ChildItem C:\\tmp-CommandKind.LIST]",
            "TestClassifyCommand::test_list[tree src/-CommandKind.LIST]",
            "TestClassifyCommand::test_list[du -sh .-CommandKind.LIST]",
            "TestClassifyCommand::test_neutral[echo hello-CommandKind.NEUTRAL]",
            "TestClassifyCommand::test_neutral[printf '%s\\n' foo-CommandKind.NEUTRAL]",
            "TestClassifyCommand::test_neutral[true-CommandKind.NEUTRAL]",
            "TestClassifyCommand::test_silent[mkdir -p /tmp/foo-CommandKind.SILENT]",
            "TestClassifyCommand::test_silent[mv a.txt b.txt-CommandKind.SILENT]",
            "TestClassifyCommand::test_silent[chmod 755 script.sh-CommandKind.SILENT]",
            "TestClassifyCommand::test_pipeline_uses_last_segment",
            "TestClassifyCommand::test_unknown_command",
            "TestClassifyCommand::test_empty",
            "TestClassifyCommand::test_env_prefix_stripped",
            "TestIsReadOnly::test_pure_read_pipeline",
            "TestIsReadOnly::test_echo_is_neutral",
            "TestIsReadOnly::test_write_command_breaks_readonly",
            "TestIsReadOnly::test_unknown_breaks_readonly",
            "TestIsReadOnly::test_single_list",
            "TestIsReadOnly::test_powershell_read_pipeline",
            "TestIsReadOnly::test_empty",
            "TestIsSilent::test_mkdir",
            "TestIsSilent::test_mv_and_cp",
            "TestIsSilent::test_echo_is_neutral_not_silent",
            "TestIsSilent::test_grep_not_silent",
            "TestIsSilent::test_empty",
            "TestInterpretExitCode::test_zero_always_ok",
            "TestInterpretExitCode::test_grep_1_no_match",
            "TestInterpretExitCode::test_grep_2_is_error",
            "TestInterpretExitCode::test_diff_1_files_differ",
            "TestInterpretExitCode::test_diff_2_is_error",
            "TestInterpretExitCode::test_find_1_partial",
            "TestInterpretExitCode::test_test_1_false",
            "TestInterpretExitCode::test_pipeline_uses_last_segment",
            "TestInterpretExitCode::test_unknown_command_1_is_error",
            "TestInterpretExitCode::test_rg_1_no_match",
            "TestInterpretExitCode::test_powershell_read_1_without_stderr_is_no_output",
            "TestInterpretExitCode::test_powershell_read_1_with_stderr_is_error"
    );

    @TestFactory
    Collection<DynamicTest> pythonBashSemanticsCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "TestClassifyCommand::test_search[grep -r foo .-CommandKind.SEARCH]" ->
                    assertClassifies("grep -r foo .", CommandKind.SEARCH);
            case "TestClassifyCommand::test_search[rg pattern-CommandKind.SEARCH]" ->
                    assertClassifies("rg pattern", CommandKind.SEARCH);
            case "TestClassifyCommand::test_search[find . -name '*.py'-CommandKind.SEARCH]" ->
                    assertClassifies("find . -name '*.py'", CommandKind.SEARCH);
            case "TestClassifyCommand::test_search[/usr/bin/grep foo-CommandKind.SEARCH]" ->
                    assertClassifies("/usr/bin/grep foo", CommandKind.SEARCH);
            case "TestClassifyCommand::test_read[cat foo.txt-CommandKind.READ]" ->
                    assertClassifies("cat foo.txt", CommandKind.READ);
            case "TestClassifyCommand::test_read[head -20 file.log-CommandKind.READ]" ->
                    assertClassifies("head -20 file.log", CommandKind.READ);
            case "TestClassifyCommand::test_read[wc -l *.py-CommandKind.READ]" ->
                    assertClassifies("wc -l *.py", CommandKind.READ);
            case "TestClassifyCommand::test_read[jq .name data.json-CommandKind.READ]" ->
                    assertClassifies("jq .name data.json", CommandKind.READ);
            case "TestClassifyCommand::test_read[Select-Object Name, Length-CommandKind.READ]" ->
                    assertClassifies("Select-Object Name, Length", CommandKind.READ);
            case "TestClassifyCommand::test_list[ls -la-CommandKind.LIST]" ->
                    assertClassifies("ls -la", CommandKind.LIST);
            case "TestClassifyCommand::test_list[Get-ChildItem C:\\tmp-CommandKind.LIST]" ->
                    assertClassifies("Get-ChildItem C:\\tmp", CommandKind.LIST);
            case "TestClassifyCommand::test_list[tree src/-CommandKind.LIST]" ->
                    assertClassifies("tree src/", CommandKind.LIST);
            case "TestClassifyCommand::test_list[du -sh .-CommandKind.LIST]" ->
                    assertClassifies("du -sh .", CommandKind.LIST);
            case "TestClassifyCommand::test_neutral[echo hello-CommandKind.NEUTRAL]" ->
                    assertClassifies("echo hello", CommandKind.NEUTRAL);
            case "TestClassifyCommand::test_neutral[printf '%s\\n' foo-CommandKind.NEUTRAL]" ->
                    assertClassifies("printf '%s\\n' foo", CommandKind.NEUTRAL);
            case "TestClassifyCommand::test_neutral[true-CommandKind.NEUTRAL]" ->
                    assertClassifies("true", CommandKind.NEUTRAL);
            case "TestClassifyCommand::test_silent[mkdir -p /tmp/foo-CommandKind.SILENT]" ->
                    assertClassifies("mkdir -p /tmp/foo", CommandKind.SILENT);
            case "TestClassifyCommand::test_silent[mv a.txt b.txt-CommandKind.SILENT]" ->
                    assertClassifies("mv a.txt b.txt", CommandKind.SILENT);
            case "TestClassifyCommand::test_silent[chmod 755 script.sh-CommandKind.SILENT]" ->
                    assertClassifies("chmod 755 script.sh", CommandKind.SILENT);
            case "TestClassifyCommand::test_pipeline_uses_last_segment" -> pipelineUsesLastSegment();
            case "TestClassifyCommand::test_unknown_command" -> assertClassifies("docker build .", CommandKind.OTHER);
            case "TestClassifyCommand::test_empty" -> assertClassifies("", CommandKind.OTHER);
            case "TestClassifyCommand::test_env_prefix_stripped" ->
                    assertClassifies("FOO=bar grep pattern", CommandKind.SEARCH);
            case "TestIsReadOnly::test_pure_read_pipeline" ->
                    assertTrue(BashSemantics.isReadOnly("cat foo.txt | grep bar | wc -l"));
            case "TestIsReadOnly::test_echo_is_neutral" ->
                    assertTrue(BashSemantics.isReadOnly("echo hello | grep h"));
            case "TestIsReadOnly::test_write_command_breaks_readonly" ->
                    assertFalse(BashSemantics.isReadOnly("cat foo.txt && rm foo.txt"));
            case "TestIsReadOnly::test_unknown_breaks_readonly" ->
                    assertFalse(BashSemantics.isReadOnly("docker ps"));
            case "TestIsReadOnly::test_single_list" -> assertTrue(BashSemantics.isReadOnly("ls -la"));
            case "TestIsReadOnly::test_powershell_read_pipeline" ->
                    assertTrue(BashSemantics.isReadOnly("Get-Item missing.md | Select-Object Name, Length"));
            case "TestIsReadOnly::test_empty" -> assertFalse(BashSemantics.isReadOnly(""));
            case "TestIsSilent::test_mkdir" -> assertTrue(BashSemantics.isSilent("mkdir -p /tmp/foo"));
            case "TestIsSilent::test_mv_and_cp" -> assertTrue(BashSemantics.isSilent("mv a b && cp c d"));
            case "TestIsSilent::test_echo_is_neutral_not_silent" -> assertTrue(BashSemantics.isSilent("echo hello"));
            case "TestIsSilent::test_grep_not_silent" -> assertFalse(BashSemantics.isSilent("grep foo bar"));
            case "TestIsSilent::test_empty" -> assertFalse(BashSemantics.isSilent(""));
            case "TestInterpretExitCode::test_zero_always_ok" -> zeroAlwaysOk();
            case "TestInterpretExitCode::test_grep_1_no_match" -> grepOneNoMatch();
            case "TestInterpretExitCode::test_grep_2_is_error" ->
                    assertTrue(BashSemantics.interpretExitCode("grep foo bar.txt", 2, "", "").isError());
            case "TestInterpretExitCode::test_diff_1_files_differ" -> diffOneFilesDiffer();
            case "TestInterpretExitCode::test_diff_2_is_error" ->
                    assertTrue(BashSemantics.interpretExitCode("diff a.txt b.txt", 2, "", "").isError());
            case "TestInterpretExitCode::test_find_1_partial" ->
                    assertFalse(BashSemantics.interpretExitCode("find / -name foo", 1, "", "").isError());
            case "TestInterpretExitCode::test_test_1_false" -> testOneFalse();
            case "TestInterpretExitCode::test_pipeline_uses_last_segment" -> exitCodePipelineUsesLastSegment();
            case "TestInterpretExitCode::test_unknown_command_1_is_error" ->
                    assertTrue(BashSemantics.interpretExitCode("python script.py", 1, "", "").isError());
            case "TestInterpretExitCode::test_rg_1_no_match" ->
                    assertFalse(BashSemantics.interpretExitCode("rg pattern .", 1, "", "").isError());
            case "TestInterpretExitCode::test_powershell_read_1_without_stderr_is_no_output" ->
                    powershellReadOneWithoutStderrIsNoOutput();
            case "TestInterpretExitCode::test_powershell_read_1_with_stderr_is_error" ->
                    assertTrue(BashSemantics.interpretExitCode(
                            "Get-Item missing.md | Select-Object Name", 1, "", "Cannot find path").isError());
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private static void assertClassifies(String command, CommandKind expected) {
        assertEquals(expected, BashSemantics.classifyCommand(command));
    }

    private static void pipelineUsesLastSegment() {
        assertClassifies("cat foo | grep bar", CommandKind.SEARCH);
        assertClassifies("grep foo | wc -l", CommandKind.READ);
    }

    private static void zeroAlwaysOk() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("anything", 0, "", "");
        assertFalse(meaning.isError());
        assertNull(meaning.message());
    }

    private static void grepOneNoMatch() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("grep foo bar.txt", 1, "", "");
        assertFalse(meaning.isError());
        assertEquals("No matches found", meaning.message());
    }

    private static void diffOneFilesDiffer() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("diff a.txt b.txt", 1, "", "");
        assertFalse(meaning.isError());
        assertEquals("Files differ", meaning.message());
    }

    private static void testOneFalse() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("test -f missing.txt", 1, "", "");
        assertFalse(meaning.isError());
        assertEquals("Condition is false", meaning.message());
    }

    private static void exitCodePipelineUsesLastSegment() {
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode("cat file | grep missing", 1, "", "");
        assertFalse(meaning.isError());
        assertEquals("No matches found", meaning.message());
    }

    private static void powershellReadOneWithoutStderrIsNoOutput() {
        String command = "powershell -Command "
                + "\"Get-Item 'C:\\tmp\\missing.md' -ErrorAction SilentlyContinue | Select-Object Name, Length\"";
        ExitCodeMeaning meaning = BashSemantics.interpretExitCode(command, 1, "", "");
        assertFalse(meaning.isError());
        assertEquals("No output returned", meaning.message());
    }
}
