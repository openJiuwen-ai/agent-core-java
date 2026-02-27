// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SkillToolKit.
 *
 * <p>Python reference: tests/system_tests/agent/skill/test_skill_real_system.py
 * (SkillToolKit related tests)
 *
 * @since 0.1.4
 */
@DisplayName("SkillToolKit Tests")
class SkillToolKitTest {

    private SkillToolKit skillToolKit;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.skillToolKit = new SkillToolKit("test-operation-id");
        this.tempDir = tempDir;
    }

    // ==================== view_file Tool Tests ====================

    @Test
    @DisplayName("Should create view_file tool with correct metadata")
    void createViewFileToolMetadata() {
        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();

        assertThat(tool.id()).isEqualTo("_internal_view_file");
        assertThat(tool.name()).isEqualTo("view_file");
        assertThat(tool.description()).contains("reads and returns the file content");
    }

    @Test
    @DisplayName("view_file should read text file content")
    void viewFileReadsTextContent() throws Exception {
        // Create test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "hello_skill_tool");

        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of("file_path", testFile.toString());

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        assertThat(result).isEqualTo("hello_skill_tool");
    }

    @Test
    @DisplayName("view_file should detect binary files by extension")
    void viewFileDetectsBinaryFilesByExtension() throws Exception {
        // Create PDF file (detected by .pdf extension)
        Path pdfFile = tempDir.resolve("test.pdf");
        Files.writeString(pdfFile, "fake pdf content");

        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of("file_path", pdfFile.toString());

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        assertThat(result).contains("Binary file detected");
    }

    @Test
    @DisplayName("view_file should detect ZIP files as binary")
    void viewFileDetectsZipAsBinary() throws Exception {
        Path zipFile = tempDir.resolve("test.zip");
        Files.writeString(zipFile, "fake zip content");

        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of("file_path", zipFile.toString());

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        assertThat(result).contains("Binary file detected");
    }

    @Test
    @DisplayName("view_file should detect PDF as binary")
    void viewFileDetectsPdfAsBinary() throws Exception {
        Path pdfFile = tempDir.resolve("test.pdf");
        Files.writeString(pdfFile, "%PDF-1.4 fake content");

        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of("file_path", pdfFile.toString());

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        assertThat(result).contains("Binary file detected");
    }

    @Test
    @DisplayName("view_file should detect Excel as binary")
    void viewFileDetectsExcelAsBinary() throws Exception {
        Path xlsxFile = tempDir.resolve("test.xlsx");
        Files.writeString(xlsxFile, "fake excel content");

        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of("file_path", xlsxFile.toString());

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        assertThat(result).contains("Binary file detected");
    }

    @Test
    @DisplayName("view_file should fail when file_path is missing")
    void viewFileFailsWithoutFilePath() {
        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of();

        CompletableFuture<String> future = tool.execute(params);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("view_file should fail when file does not exist")
    void viewFileFailsForNonExistentFile() {
        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> params = Map.of("file_path", "/non/existent/file.txt");

        CompletableFuture<String> future = tool.execute(params);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(Exception.class);
    }

    // ==================== execute_code Tool Tests ====================

    @Test
    @DisplayName("Should create execute_code tool with correct metadata")
    void createExecuteCodeToolMetadata() {
        SkillToolKit.ToolFunction tool = skillToolKit.createExecuteCodeTool();

        assertThat(tool.id()).isEqualTo("_internal_execute_code");
        assertThat(tool.name()).isEqualTo("execute_code");
        assertThat(tool.description()).contains("Execute code");
    }

    @Test
    @DisplayName("execute_code should require code_block parameter")
    void executeCodeRequiresCodeBlock() {
        SkillToolKit.ToolFunction tool = skillToolKit.createExecuteCodeTool();
        Map<String, Object> params = Map.of();

        CompletableFuture<String> future = tool.execute(params);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("execute_code should accept code_block parameter")
    void executeCodeAcceptsCodeBlock() throws Exception {
        SkillToolKit.ToolFunction tool = skillToolKit.createExecuteCodeTool();
        Map<String, Object> params = Map.of(
                "code_block", "print(123 + 456)",
                "language", "python"
        );

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        // Current implementation returns a placeholder message
        assertThat(result).contains("Code execution requested");
    }

    @Test
    @DisplayName("execute_code should default to python language")
    void executeCodeDefaultsToPython() throws Exception {
        SkillToolKit.ToolFunction tool = skillToolKit.createExecuteCodeTool();
        Map<String, Object> params = Map.of("code_block", "print('hello')");

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        assertThat(result).contains("python");
    }

    // ==================== run_command Tool Tests ====================

    @Test
    @DisplayName("Should create run_command tool with correct metadata")
    void createRunCommandToolMetadata() {
        SkillToolKit.ToolFunction tool = skillToolKit.createRunCommandTool();

        assertThat(tool.id()).isEqualTo("_internal_run_command");
        assertThat(tool.name()).isEqualTo("run_command");
        assertThat(tool.description()).contains("Execute bash commands");
    }

    @Test
    @DisplayName("run_command should require bash_command parameter")
    void runCommandRequiresBashCommand() {
        SkillToolKit.ToolFunction tool = skillToolKit.createRunCommandTool();
        Map<String, Object> params = Map.of();

        CompletableFuture<String> future = tool.execute(params);

        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("run_command should accept bash_command parameter")
    void runCommandAcceptsBashCommand() throws Exception {
        SkillToolKit.ToolFunction tool = skillToolKit.createRunCommandTool();
        Map<String, Object> params = Map.of("bash_command", "echo hello");

        CompletableFuture<String> future = tool.execute(params);
        String result = future.get();

        // Current implementation returns a placeholder message
        assertThat(result).contains("Command execution requested");
        assertThat(result).contains("echo hello");
    }

    // ==================== Input Schema Tests ====================

    @Test
    @DisplayName("view_file input schema should require file_path")
    void viewFileInputSchema() {
        SkillToolKit.ToolFunction tool = skillToolKit.createViewFileTool();
        Map<String, Object> schema = tool.inputSchema();

        assertThat(schema.get("type")).isEqualTo("object");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("file_path");

        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertThat(required).contains("file_path");
    }

    @Test
    @DisplayName("execute_code input schema should require code_block")
    void executeCodeInputSchema() {
        SkillToolKit.ToolFunction tool = skillToolKit.createExecuteCodeTool();
        Map<String, Object> schema = tool.inputSchema();

        assertThat(schema.get("type")).isEqualTo("object");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("code_block");
        assertThat(properties).containsKey("language");

        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertThat(required).contains("code_block");
    }

    @Test
    @DisplayName("run_command input schema should require bash_command")
    void runCommandInputSchema() {
        SkillToolKit.ToolFunction tool = skillToolKit.createRunCommandTool();
        Map<String, Object> schema = tool.inputSchema();

        assertThat(schema.get("type")).isEqualTo("object");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("bash_command");

        java.util.List<String> required = (java.util.List<String>) schema.get("required");
        assertThat(required).contains("bash_command");
    }

    // ==================== Runner Tests ====================

    @Test
    @DisplayName("Should set and get runner")
    void setAndGetRunner() {
        Object mockRunner = new Object();
        skillToolKit.setRunner(mockRunner);

        assertThat(skillToolKit.getRunner()).isSameAs(mockRunner);
    }

    @Test
    @DisplayName("Should set and get sysOperationId")
    void setAndGetSysOperationId() {
        skillToolKit.setSysOperationId("new-operation-id");

        assertThat(skillToolKit.getSysOperationId()).isEqualTo("new-operation-id");
    }
}
