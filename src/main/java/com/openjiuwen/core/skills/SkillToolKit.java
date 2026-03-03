// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcesmanager.Result;
import com.openjiuwen.core.sysoperation.SysOperation;
import com.openjiuwen.core.sysoperation.SysOperationCard;
import com.openjiuwen.core.sysoperation.code.BaseCodeOperation;
import com.openjiuwen.core.sysoperation.result.Language;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeData;
import com.openjiuwen.core.sysoperation.result.code.ExecuteCodeResult;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdData;
import com.openjiuwen.core.sysoperation.result.shell.ExecuteCmdResult;
import com.openjiuwen.core.sysoperation.shell.BaseShellOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Toolkit for creating skill-related tools.
 *
 * <p>This class provides methods to create tools that can be used by agents
 * to interact with skills, including viewing files, executing code, and
 * running commands.
 *
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/skills/skill_tool_kit.py::SkillToolKit}
 *
 * @since 0.1.4
 */
public class SkillToolKit {

    private static final Logger log = LoggerFactory.getLogger(SkillToolKit.class);

    /**
     * The system operation ID used for file and code operations.
     */
    private String sysOperationId;

    /**
     * The runner instance (optional, for future use).
     */
    private Object runner;

    /**
     * Constructs a SkillToolKit with the specified system operation ID.
     *
     * @param sysOperationId the system operation ID
     */
    public SkillToolKit(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Creates a tool for viewing file contents.
     *
     * <p>This tool reads and returns the content of a text file.
     * It is designed for reading skill documentation files (.md, .txt).
     *
     * @return a ToolFunction that views file contents
     */
    public ToolFunction createViewFileTool() {
        return new ToolFunction(
                "_internal_view_file",
                "view_file",
                "Given a file_path, reads and returns the file content stored at file_path. " +
                        "Used only for reading the skills this agent is equipped with (e.g. .md and .txt files), " +
                        "and does NOT read binary files (e.g. .pdf, .xlsx, .ppt etc.)",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file_path", Map.of(
                                        "description", "The path to the file to view",
                                        "type", "string"
                                )
                        ),
                        "required", List.of("file_path")
                ),
                this::viewFile
        );
    }

    /**
     * Creates a tool for executing code.
     *
     * <p>This tool executes code in the specified language (Python, JavaScript).
     *
     * @return a ToolFunction that executes code
     */
    public ToolFunction createExecuteCodeTool() {
        return new ToolFunction(
                "_internal_execute_code",
                "execute_code",
                "Execute code in the specified language (Python, JavaScript)",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "code_block", Map.of(
                                        "description", "The code to execute",
                                        "type", "string"
                                ),
                                "language", Map.of(
                                        "description", "The programming language (python, javascript)",
                                        "type", "string"
                                )
                        ),
                        "required", List.of("code_block")
                ),
                this::executeCode
        );
    }

    /**
     * Creates a tool for running shell commands.
     *
     * <p>This tool executes bash commands in a Linux terminal.
     *
     * @return a ToolFunction that runs commands
     */
    public ToolFunction createRunCommandTool() {
        return new ToolFunction(
                "_internal_run_command",
                "run_command",
                "Execute bash commands in a Linux terminal",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "bash_command", Map.of(
                                        "description", "One or more bash commands to execute",
                                        "type", "string"
                                )
                        ),
                        "required", List.of("bash_command")
                ),
                this::runCommand
        );
    }

    /**
     * Views the content of a file.
     *
     * @param params the parameters containing "file_path"
     * @return the file content
     */
    private CompletableFuture<String> viewFile(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String filePath = (String) params.get("file_path");
            if (filePath == null || filePath.isEmpty()) {
                throw BaseError.builder(StatusCode.SKILL_TOOL_EXECUTION_ERROR)
                        .param("tool_name", "view_file")
                        .param("error_msg", "file_path is required")
                        .build();
            }

            try {
                Path path = Path.of(filePath);

                // Check if it's a binary file
                String fileName = path.getFileName().toString().toLowerCase();
                if (isBinaryFile(fileName)) {
                    return String.format("Binary file detected at %s. Use execute_code to read it with appropriate libraries.",
                            filePath);
                }

                String content = Files.readString(path);
                return content;

            } catch (Exception e) {
                log.warn("Failed to view file: {}", filePath, e);
                throw BaseError.builder(StatusCode.SKILL_FILE_READ_ERROR)
                        .param("file_path", filePath)
                        .param("error_msg", e.getMessage())
                        .cause(e)
                        .build();
            }
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Executes code in the specified language.
     *
     * <p>Note: This is a placeholder implementation. In production, this would
     * integrate with the actual code execution system.
     *
     * @param params the parameters containing "code_block" and optionally "language"
     * @return the execution result
     */
    private CompletableFuture<String> executeCode(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String codeBlock = (String) params.get("code_block");
            String languageValue = (String) params.getOrDefault("language", "python");

            if (codeBlock == null || codeBlock.isEmpty()) {
                throw BaseError.builder(StatusCode.SKILL_TOOL_EXECUTION_ERROR)
                        .param("tool_name", "execute_code")
                        .param("error_msg", "code_block is required")
                        .build();
            }

            Language language;
            try {
                language = Language.fromValue(languageValue);
            } catch (Exception e) {
                throw BaseError.builder(StatusCode.SKILL_TOOL_EXECUTION_ERROR)
                        .param("tool_name", "execute_code")
                        .param("error_msg", "language must be one of [python, javascript]")
                        .cause(e)
                        .build();
            }

            SysOperation sysOperation = getSysOperationOrThrow();
            Object codeOperation = sysOperation.code();
            if (!(codeOperation instanceof BaseCodeOperation baseCodeOperation)) {
                throw BaseError.builder(StatusCode.SKILL_SYS_OPERATION_NOT_AVAILABLE)
                        .build();
            }

            log.info("Executing {} code: {} chars", language.getValue(), codeBlock.length());
            ExecuteCodeResult result = baseCodeOperation.executeCode(
                    codeBlock,
                    language,
                    BaseCodeOperation.DEFAULT_TIMEOUT,
                    null,
                    null
            ).join();

            if (result == null) {
                throw BaseError.builder(StatusCode.SKILL_TOOL_EXECUTION_ERROR)
                        .param("tool_name", "execute_code")
                        .param("error_msg", "empty result from sys_operation.code")
                        .build();
            }

            ExecuteCodeData data = result.getData();
            String stdout = data != null ? data.getStdout() : "";
            String stderr = data != null ? data.getStderr() : "";
            Integer exitCode = data != null ? data.getExitCode() : null;

            if (result.isFailure()) {
                return formatExecutionResult("execute_code", result.getCode(), result.getMessage(), exitCode, stdout, stderr);
            }

            String successMessage = String.format("Code execution requested (language=%s)", language.getValue());
            return formatExecutionResult("execute_code", result.getCode(), successMessage, exitCode, stdout, stderr);
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Runs a shell command.
     *
     * <p>Note: This is a placeholder implementation. In production, this would
     * integrate with the actual shell execution system.
     *
     * @param params the parameters containing "bash_command"
     * @return the command output
     */
    private CompletableFuture<String> runCommand(Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            String bashCommand = (String) params.get("bash_command");

            if (bashCommand == null || bashCommand.isEmpty()) {
                throw BaseError.builder(StatusCode.SKILL_TOOL_EXECUTION_ERROR)
                        .param("tool_name", "run_command")
                        .param("error_msg", "bash_command is required")
                        .build();
            }

            SysOperation sysOperation = getSysOperationOrThrow();
            Object shellOperation = sysOperation.shell();
            if (!(shellOperation instanceof BaseShellOperation baseShellOperation)) {
                throw BaseError.builder(StatusCode.SKILL_SYS_OPERATION_NOT_AVAILABLE)
                        .build();
            }

            log.info("Running command: {}", bashCommand);
            ExecuteCmdResult result = baseShellOperation.executeCmd(
                    bashCommand,
                    null,
                    BaseShellOperation.DEFAULT_TIMEOUT,
                    null,
                    Map.of("encoding", "UTF-8")
            ).join();

            if (result == null) {
                throw BaseError.builder(StatusCode.SKILL_TOOL_EXECUTION_ERROR)
                        .param("tool_name", "run_command")
                        .param("error_msg", "empty result from sys_operation.shell")
                        .build();
            }

            ExecuteCmdData data = result.getData();
            String stdout = data != null ? data.getStdout() : "";
            String stderr = data != null ? data.getStderr() : "";
            Integer exitCode = data != null ? data.getExitCode() : null;

            if (result.isFailure()) {
                return formatExecutionResult("run_command", result.getCode(), result.getMessage(), exitCode, stdout, stderr);
            }

            String successMessage = String.format("Command execution requested: %s", bashCommand);
            return formatExecutionResult("run_command", result.getCode(), successMessage, exitCode, stdout, stderr);
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    private SysOperation getSysOperationOrThrow() {
        if (sysOperationId == null || sysOperationId.isBlank()) {
            throw BaseError.builder(StatusCode.SKILL_SYS_OPERATION_NOT_AVAILABLE)
                    .build();
        }

        Object sysOperationObj = Runner.getResourceMgr().getSysOperation(sysOperationId, null, null);
        if (!(sysOperationObj instanceof SysOperation)) {
            SysOperationCard fallbackCard = SysOperationCard.builder()
                    .id(sysOperationId)
                    .mode("local")
                    .build();

            Result<?> addResult = Runner.getResourceMgr().addSysOperation(fallbackCard, null);
            if (addResult != null && addResult.isErr()) {
                log.warn("Failed to auto-register sys_operation, id={}, reason={}", sysOperationId, addResult.msg());
                throw BaseError.builder(StatusCode.SKILL_SYS_OPERATION_NOT_AVAILABLE)
                        .param("error_msg", String.valueOf(addResult.msg()))
                        .build();
            }

            sysOperationObj = Runner.getResourceMgr().getSysOperation(sysOperationId, null, null);
        }

        if (!(sysOperationObj instanceof SysOperation sysOperation)) {
            throw BaseError.builder(StatusCode.SKILL_SYS_OPERATION_NOT_AVAILABLE)
                    .build();
        }

        return sysOperation;
    }

    private String formatExecutionResult(String toolName,
                                         int code,
                                         String message,
                                         Integer exitCode,
                                         String stdout,
                                         String stderr) {
        String safeMessage = message == null ? "" : message;
        String safeStdout = stdout == null ? "" : stdout;
        String safeStderr = stderr == null ? "" : stderr;
        String exitCodeText = exitCode == null ? "null" : String.valueOf(exitCode);

        return String.format(
                "tool=%s\nstatus_code=%d\nmessage=%s\nexit_code=%s\nstdout:\n%s\nstderr:\n%s",
                toolName,
                code,
                safeMessage,
                exitCodeText,
                safeStdout,
                safeStderr
        );
    }

    /**
     * Checks if a file is likely to be binary based on its extension.
     *
     * @param fileName the file name to check
     * @return true if the file is likely binary
     */
    private boolean isBinaryFile(String fileName) {
        String[] binaryExtensions = {
                ".pdf", ".xlsx", ".xls", ".ppt", ".pptx",
                ".doc", ".docx", ".zip", ".tar", ".gz",
                ".png", ".jpg", ".jpeg", ".gif", ".bmp",
                ".exe", ".dll", ".so", ".dylib",
                ".class", ".jar"
        };

        for (String ext : binaryExtensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the runner instance.
     *
     * @param runner the runner instance
     */
    public void setRunner(Object runner) {
        this.runner = runner;
    }

    /**
     * Gets the runner instance.
     *
     * @return the runner instance
     */
    public Object getRunner() {
        return runner;
    }

    /**
     * Gets the system operation ID.
     *
     * @return the system operation ID
     */
    public String getSysOperationId() {
        return sysOperationId;
    }

    /**
     * Sets the system operation ID.
     *
     * @param sysOperationId the new system operation ID
     */
    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Represents a tool function that can be executed.
     */
    public record ToolFunction(
            String id,
            String name,
            String description,
            Map<String, Object> inputSchema,
            java.util.function.Function<Map<String, Object>, CompletableFuture<String>> executor
    ) {
        /**
         * Executes this tool function with the given parameters.
         *
         * @param params the parameters
         * @return a CompletableFuture containing the result
         */
        public CompletableFuture<String> execute(Map<String, Object> params) {
            return executor.apply(params);
        }
    }
}
