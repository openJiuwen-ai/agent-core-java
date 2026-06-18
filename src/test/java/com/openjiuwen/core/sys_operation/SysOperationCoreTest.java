/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.SysOperationEvent;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sys_operation.config.ContainerScope;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxIsolationConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's sys-operation core behavior for
 * {@code BaseOperation} in {@code openjiuwen/core/sys_operation/base.py},
 * {@code BaseShellOperation} in {@code openjiuwen/core/sys_operation/shell.py},
 * {@code openjiuwen/core/sys_operation/registry.py}, and
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.
 */
class SysOperationCoreTest {

    @AfterEach
    void cleanRegistry() {
        OperationRegistry.clearForTest();
    }

    @Test
    void operationModeParsesLikePythonEnum() {
        assertThat(OperationMode.fromValue("LOCAL")).isEqualTo(OperationMode.LOCAL);
        assertThat(BaseShellOperation.ShellType.fromValue("PowerShell")).isEqualTo(BaseShellOperation.ShellType.POWERSHELL);
        assertThat(BaseShellOperation.ShellType.fromValue("unknown")).isEqualTo(BaseShellOperation.ShellType.AUTO);
    }

    @Test
    void baseOperationGeneratesToolCardsFromMethodNames() {
        ExampleShellOperation operation = new ExampleShellOperation("shell", OperationMode.LOCAL, "demo", new Object());

        List<ToolCard> cards = operation.listTools();

        assertThat(cards).extracting(ToolCard::getName)
                .contains("execute_cmd", "execute_cmd_stream", "execute_cmd_background");
    }

    @Test
    void baseShellOperationDefaultStreamMatchesPythonPass() {
        ExampleShellOperation operation = new ExampleShellOperation("shell", OperationMode.LOCAL, "demo", new Object());

        assertThat(operation.executeCmdStream("pwd", null, 300, null, null, BaseShellOperation.ShellType.AUTO))
                .isNull();
    }

    @Test
    void baseOperationDefaultListToolsMatchesPythonPass() {
        MinimalOperation operation = new MinimalOperation("base", OperationMode.LOCAL, "demo", new Object());

        assertThat(operation.listTools()).isNull();
    }

    @Test
    void baseOperationCreatesOnlySysOperationEvents() {
        MinimalOperation operation = new MinimalOperation("base", OperationMode.SANDBOX, "demo", new Object());

        SysOperationEvent event = operation.createEvent(LogEventType.SYS_OP_START, Map.of(
                "module_id", "custom_module",
                "session_id", "session-1",
                "metadata", Map.of("retained", true),
                "ignored_field", "ignored"
        ));

        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(LogEventType.SYS_OP_START);
        assertThat(event.getOperationName()).isEqualTo("base");
        assertThat(event.getOperationMode()).isEqualTo("sandbox");
        assertThat(event.getOperationDesc()).isEqualTo("demo");
        assertThat(event.getMethodName()).isEqualTo("run");
        assertThat(event.getModuleId()).isEqualTo("custom_module");
        assertThat(event.getModuleName()).isEqualTo("sys_operation");
        assertThat(event.getSessionId()).isEqualTo("session-1");
        assertThat(event.getMetadata())
                .containsEntry("retained", true)
                .doesNotContainKey("ignored_field");

        assertThat(operation.createEvent(LogEventType.AGENT_START, Map.of())).isNull();
        assertThat(operation.createEvent("sys_operation_start", Map.of())).isNull();
    }

    @Test
    void registrySupportsRegisterOverrideAndSortedNames() {
        OperationRegistry.register(ExampleShellOperation.class, "shell", OperationMode.LOCAL, "first");
        OperationRegistry.register(ExampleFsOperation.class, "fs", OperationMode.LOCAL, "fs");

        assertThat(OperationRegistry.getSupportedOperations(OperationMode.LOCAL)).containsExactly("fs", "shell");
        assertThat(OperationRegistry.getOperationInfo("shell", OperationMode.LOCAL).description()).isEqualTo("first");

        OperationRegistry.register(ExampleShellOperation.class, "shell", OperationMode.LOCAL, "override");
        assertThat(OperationRegistry.getOperationInfo("shell", OperationMode.LOCAL).description()).isEqualTo("override");
    }

    @Test
    void sysOperationCreatesAndCachesRegisteredOperations() {
        OperationRegistry.register(ExampleShellOperation.class, "shell", OperationMode.LOCAL, "local shell");
        SysOperationCard card = new SysOperationCard("sys_op", OperationMode.LOCAL, new LocalWorkConfig());
        SysOperation sysOperation = new SysOperation(card);

        BaseShellOperation first = sysOperation.shell();
        BaseShellOperation second = sysOperation.shell();

        assertThat(first).isSameAs(second);
        assertThat(first.getName()).isEqualTo("shell");
        assertThat(first.getMode()).isEqualTo(OperationMode.LOCAL);
    }

    @Test
    void sysOperationCardParsesModeAndGeneratesToolIds() {
        SysOperationCard card = new SysOperationCard();
        card.setId("sys_op");

        card.setMode("SANDBOX");

        assertThat(card.getMode()).isEqualTo(OperationMode.SANDBOX);
        assertThat(card.getFs().toolId("read_file")).isEqualTo("sys_op.fs.read_file");
        assertThat(card.operation("browser").toolId("navigate")).isEqualTo("sys_op.browser.navigate");
        assertThat(SysOperationCard.generateToolId("sys_op", "code", "run")).isEqualTo("sys_op.code.run");
        assertThatThrownBy(() -> card.setMode(" "))
                .hasMessageContaining("mode must be one of [local, sandbox]");
        assertThatThrownBy(() -> card.setMode("remote"))
                .hasMessageContaining("mode must be one of [local, sandbox]");
    }

    @Test
    void sandboxIsolationTemplateMatchesPythonRules() {
        String sessionTemplate = SysOperation.generateIsolationKeyTemplate(
                "agent",
                ContainerScope.SESSION,
                null,
                "pre_deploy",
                "aio"
        );
        assertThat(sessionTemplate).isEqualTo("session_pre_deploy_aio_agent_{session_id}");

        String systemTemplate = SysOperation.generateIsolationKeyTemplate(
                null,
                ContainerScope.SYSTEM,
                null,
                "pre_deploy",
                "aio"
        );
        assertThat(systemTemplate).isEqualTo("system_pre_deploy_aio_system");

        assertThatThrownBy(() -> SysOperation.generateIsolationKeyTemplate(
                null,
                ContainerScope.CUSTOM,
                null,
                "pre_deploy",
                "aio"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sysOperationValidatesSandboxLauncherConfig() {
        SysOperationCard card = new SysOperationCard();
        card.setId("sandbox");
        card.setMode(OperationMode.SANDBOX);
        card.setGatewayConfig(SandboxGatewayConfig.builder()
                .isolation(SandboxIsolationConfig.builder().containerScope(ContainerScope.SESSION).build())
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .sandboxType("aio")
                        .build())
                .build());

        SysOperation sysOperation = new SysOperation(card);

        assertThat(sysOperation.getIsolationKeyTemplate()).isEqualTo("session_pre_deploy_aio_{session_id}");
    }

    private static final class MinimalOperation extends BaseOperation {

        private MinimalOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        private SysOperationEvent createEvent(LogEventType eventType, Map<String, Object> kwargs) {
            return createSysOperationEvent(eventType, "run", Map.of("input", "value"), Map.of("ok", true), 12.5, kwargs);
        }

        private SysOperationEvent createEvent(String eventType, Map<String, Object> kwargs) {
            return createSysOperationEvent(eventType, "run", Map.of("input", "value"), Map.of("ok", true), 12.5, kwargs);
        }
    }

    private static final class ExampleShellOperation extends BaseShellOperation {

        public ExampleShellOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.ExecuteCmdResult> executeCmd(
                String command,
                String cwd,
                Integer timeout,
                java.util.Map<String, String> environment,
                java.util.Map<String, Object> options,
                ShellType shellType) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new com.openjiuwen.core.sys_operation.result.ExecuteCmdResult()
            );
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult> executeCmdBackground(
                String command,
                String cwd,
                java.util.Map<String, String> environment,
                double grace,
                ShellType shellType) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult()
            );
        }
    }

    private static final class ExampleFsOperation extends BaseFsOperation {

        public ExampleFsOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.ReadFileResult> readFile(
                String path,
                FileMode mode,
                Integer head,
                Integer tail,
                com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.Flow.Publisher<com.openjiuwen.core.sys_operation.result.ReadFileStreamResult> readFileStream(
                String path,
                FileMode mode,
                Integer head,
                Integer tail,
                com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.WriteFileResult> writeFile(
                String path,
                String content,
                FileMode mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.WriteFileResult> writeFile(
                String path,
                byte[] content,
                FileMode mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.UploadFileResult> uploadFile(
                String localPath,
                String targetPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.Flow.Publisher<com.openjiuwen.core.sys_operation.result.UploadFileStreamResult> uploadFileStream(
                String localPath,
                String targetPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.DownloadFileResult> downloadFile(
                String sourcePath,
                String localPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.Flow.Publisher<com.openjiuwen.core.sys_operation.result.DownloadFileStreamResult> downloadFileStream(
                String sourcePath,
                String localPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.ListFilesResult> listFiles(
                String path,
                boolean recursive,
                Integer maxDepth,
                SortBy sortBy,
                boolean sortDescending,
                java.util.List<String> fileTypes,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.ListDirsResult> listDirectories(
                String path,
                boolean recursive,
                Integer maxDepth,
                SortBy sortBy,
                boolean sortDescending,
                java.util.Map<String, Object> options) {
            return null;
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.openjiuwen.core.sys_operation.result.SearchFilesResult> searchFiles(
                String path,
                String pattern,
                java.util.List<String> excludePatterns) {
            return null;
        }
    }
}
