/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.sys_operation.BaseShellOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.SysOperationCard;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/tools/test_powershell/test_powershell_tool.py}.
 */
class PowerShellToolMissingTest {

    @TempDir
    private Path workspace;

    @Test
    void invokeForcesPowershellShellType() throws Exception {
        FakeSysOperation sysOperation = new FakeSysOperation();
        PowerShellTool tool = new PowerShellTool(sysOperation, "en", PermissionMode.AUTO, null, null, null);

        ToolOutput result = invoke(tool, Map.of("command", "Write-Output ok"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(sysOperation.shell().calls).isEqualTo(1);
        assertThat(sysOperation.shell().shellType).isEqualTo(BaseShellOperation.ShellType.POWERSHELL);
    }

    @Test
    void readOnlyBlocksWriteCommands() throws Exception {
        PowerShellTool tool = new PowerShellTool(new FakeSysOperation(), PermissionMode.READ_ONLY, null);

        ToolOutput result = invoke(tool, Map.of("command", "Set-Content test.txt hi"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("Read-only");
    }

    @Test
    void injectionPatternBlocked() throws Exception {
        PowerShellTool tool = new PowerShellTool(new FakeSysOperation());

        ToolOutput result = invoke(tool, Map.of("command", "Invoke-Expression \"Get-ChildItem\""));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).containsIgnoringCase("injection");
    }

    @Test
    void historyPathContainsAgentIdAndSessionId() {
        PowerShellTool tool = new PowerShellTool(new FakeSysOperation(), PermissionMode.AUTO, "agent_xyz");

        String path = tool.buildHistoryPath(new FakeSession("sess_abc"));

        assertThat(path).contains("agent_xyz").contains("sess_abc");
    }

    @Test
    void defaultAgentIdUsedWhenNone() {
        PowerShellTool tool = new PowerShellTool(new FakeSysOperation(), PermissionMode.AUTO, null);

        String path = tool.buildHistoryPath(new FakeSession("s1"));

        assertThat(path).contains("default");
    }

    @Test
    void workspacePathIsBaseDir() {
        Cwd.setWorkspace(workspace.toString());
        try {
            PowerShellTool tool = new PowerShellTool(new FakeSysOperation(), PermissionMode.AUTO, "a");

            String path = tool.buildHistoryPath(new FakeSession("s1"));

            assertThat(path).startsWith(workspace.toAbsolutePath().normalize().toString());
            assertThat(path).contains(".agent_history");
        } finally {
            Cwd.clear();
        }
    }

    @Test
    void filenamePattern() {
        PowerShellTool tool = new PowerShellTool(new FakeSysOperation(), PermissionMode.AUTO, "myagent");

        String path = tool.buildHistoryPath(new FakeSession("sess123"));

        assertThat(Path.of(path).getFileName().toString()).isEqualTo("file_ops_myagent_sess123.json");
    }

    private static ToolOutput invoke(PowerShellTool tool, Map<String, Object> inputs) throws Exception {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    public static final class FakeSysOperation extends SysOperation {
        private final FakeShell shell = new FakeShell();

        public FakeSysOperation() {
            super(new SysOperationCard("fake", OperationMode.LOCAL, null));
        }

        @Override
        public FakeShell shell() {
            return shell;
        }
    }

    public static final class FakeShell extends BaseShellOperation {
        private int calls;
        private ShellType shellType;

        public FakeShell() {
            super("shell", OperationMode.LOCAL, "fake shell", null);
        }

        @Override
        public CompletableFuture<ExecuteCmdResult> executeCmd(
                String command,
                String cwd,
                Integer timeout,
                Map<String, String> environment,
                Map<String, Object> options,
                ShellType shellType
        ) {
            this.calls += 1;
            this.shellType = shellType;
            ExecuteCmdResult result = new ExecuteCmdResult();
            result.setCode(StatusCode.SUCCESS.code());
            result.setMessage("");
            result.setData(ExecuteCmdData.builder()
                    .command(command)
                    .cwd(cwd)
                    .stdout("ok\n")
                    .stderr("")
                    .exitCode(0)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(
                String command,
                String cwd,
                Map<String, String> environment,
                double grace,
                ShellType shellType
        ) {
            return CompletableFuture.completedFuture(new ExecuteCmdBackgroundResult());
        }
    }

    public record FakeSession(String sessionId) implements AgentSessionApi {
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return Collections.emptyIterator();
        }
    }
}
