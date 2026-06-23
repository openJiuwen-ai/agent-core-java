/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sys_operation.BaseCodeOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.FilesystemTools;
import com.openjiuwen.harness.tools.shell.bash.BashTool;
import com.openjiuwen.harness.tools.shell.powershell.PowerShellTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registers filesystem, shell and code tools for a sys-operation-backed agent.
 *
 * <p>Mirrors Python's {@code SysOperationRail} in
 * {@code openjiuwen/harness/rails/sys_operation_rail.py}.</p>
 */
public class SysOperationRail extends DeepAgentRail {

    private static final int PRIORITY = 100;

    private final boolean withCodeTool;
    private final boolean readOnly;
    private final Boolean enableReadImageMultimodal;
    private final List<Tool> tools = new ArrayList<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private boolean active;

    public SysOperationRail() {
        this(false, false, null);
    }

    public SysOperationRail(boolean withCodeTool, boolean readOnly) {
        this(withCodeTool, readOnly, null);
    }

    public SysOperationRail(boolean withCodeTool, boolean readOnly, Boolean enableReadImageMultimodal) {
        this.withCodeTool = withCodeTool;
        this.readOnly = readOnly;
        this.enableReadImageMultimodal = enableReadImageMultimodal;
        setPriority(PRIORITY);
    }

    @Override
    public void init(DeepAgent agent) {
        Object explicitSysOperation = getSysOperation();
        super.init(agent);
        if (explicitSysOperation != null) {
            setSysOperation(explicitSysOperation);
        }
        removeRegisteredTools(agent);
        tools.clear();

        String workspaceRoot = resolveWorkspaceRoot(agent);
        boolean readImageMultimodal = resolveReadImageMultimodal(agent);
        String agentId = resolveAgentId(agent);
        SysOperation operation = typedSysOperation();

        addTool(new FilesystemTools.ReadFileTool(workspaceRoot, readImageMultimodal),
                "read_file", "ReadFileTool", agentId);
        if (!readOnly) {
            addTool(new FilesystemTools.WriteFileTool(workspaceRoot), "write_file", "WriteFileTool", agentId);
            addTool(new FilesystemTools.EditFileTool(workspaceRoot), "edit_file", "EditFileTool", agentId);
        }
        addTool(new FilesystemTools.GlobTool(workspaceRoot), "glob", "GlobTool", agentId);
        addTool(new FilesystemTools.ListDirTool(workspaceRoot), "list_files", "ListDirTool", agentId);
        addTool(new FilesystemTools.GrepTool(workspaceRoot), "grep", "GrepTool", agentId);
        addTool(new BashTool(), "bash", "BashTool", agentId);
        if (isWindows()) {
            addTool(new PowerShellTool(operation), "powershell", "PowerShellTool", agentId);
        }
        if (withCodeTool && !readOnly) {
            addTool(new CodeTool(operation == null ? null : operationBackedCodeExecutor(operation)),
                    "code", "CodeTool", agentId);
        }

        // Tool ids are agent-scoped, so remove stale registrations before adding current instances.
        for (Tool tool : tools) {
            if (Runner.resourceMgr().getTool(tool.getCard().getId()) != null) {
                Runner.resourceMgr().removeTool(tool.getCard().getId());
            }
            Runner.resourceMgr().addTool(tool);
            ownedToolIds.add(tool.getCard().getId());
            if (agent != null && agent.getAbilityManager() != null) {
                agent.getAbilityManager().add(tool.getCard());
                ownedToolNames.add(tool.getCard().getName());
            }
        }
    }

    @Override
    public void uninit(DeepAgent agent) {
        removeRegisteredTools(agent);
        tools.clear();
    }

    public List<Tool> getTools() {
        return List.copyOf(tools);
    }

    public boolean isWithCodeTool() {
        return withCodeTool;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        active = true;
        ctx.put("sys_operation_active", true);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        active = false;
        ctx.put("sys_operation_active", false);
    }

    public boolean isActive() {
        return active;
    }

    private void addTool(Tool tool, String pythonName, String toolIdPrefix, String agentId) {
        tool.getCard().setName(pythonName);
        tool.getCard().setId(toolIdPrefix + "_" + (agentId == null || agentId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : agentId));
        tools.add(tool);
    }

    private void removeRegisteredTools(DeepAgent agent) {
        for (String toolName : Set.copyOf(ownedToolNames)) {
            if (agent != null && agent.getAbilityManager() != null) {
                agent.getAbilityManager().remove(toolName);
            }
        }
        for (String toolId : Set.copyOf(ownedToolIds)) {
            if (Runner.resourceMgr().getTool(toolId) != null) {
                Runner.resourceMgr().removeTool(toolId);
            }
        }
        ownedToolNames.clear();
        ownedToolIds.clear();
    }

    private SysOperation typedSysOperation() {
        Object value = getSysOperation();
        return value instanceof SysOperation operation ? operation : null;
    }

    private boolean resolveReadImageMultimodal(DeepAgent agent) {
        if (enableReadImageMultimodal != null) {
            return enableReadImageMultimodal;
        }
        DeepAgentConfig config = agent == null ? null : agent.deepConfig();
        return config == null || config.isEnableReadImageMultimodal();
    }

    private static String resolveWorkspaceRoot(DeepAgent agent) {
        Object workspace = agent == null || agent.deepConfig() == null ? null : agent.deepConfig().getWorkspace();
        if (workspace instanceof Path path) {
            return path.toString();
        }
        if (workspace instanceof CharSequence text && !text.toString().isBlank()) {
            return text.toString();
        }
        return Cwd.getCwd();
    }

    private static String resolveAgentId(DeepAgent agent) {
        if (agent == null || agent.getCard() == null) {
            return null;
        }
        String id = agent.getCard().getId();
        if (id != null && !id.isBlank()) {
            return id;
        }
        return agent.getCard().getName();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static CodeTool.CodeExecutor operationBackedCodeExecutor(SysOperation operation) {
        return (code, language, timeoutSeconds, kwargs) -> {
            if (operation.code() == null) {
                return new CodeTool.CodeExecutionResult("", "code operation is not configured", -1);
            }
            String cwd = kwargs == null || kwargs.get("cwd") == null ? Cwd.getCwd() : String.valueOf(kwargs.get("cwd"));
            ExecuteCodeResult result = operation.code().executeCode(
                    code,
                    BaseCodeOperation.CodeLanguage.fromValue(language),
                    timeoutSeconds,
                    Map.of(),
                    cwd,
                    kwargs == null ? Map.of() : kwargs
            ).join();
            ExecuteCodeData data = result == null ? null : result.getData();
            String stdout = data == null || data.getStdout() == null ? "" : data.getStdout();
            String stderr = data == null || data.getStderr() == null ? "" : data.getStderr();
            int exitCode = data == null || data.getExitCode() == null ? -1 : data.getExitCode();
            if (result != null && result.getCode() != StatusCode.SUCCESS.code() && stderr.isBlank()) {
                stderr = result.getMessage();
            }
            return new CodeTool.CodeExecutionResult(stdout, stderr, exitCode);
        };
    }
}
