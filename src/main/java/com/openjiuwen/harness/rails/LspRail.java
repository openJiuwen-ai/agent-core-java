/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.lsp.HarnessLspPackage;
import com.openjiuwen.harness.lsp.InitializeOptions;
import com.openjiuwen.harness.lsp.InitializeResult;
import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.tools.lsp_tool.LspTool;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Initializes LSP subsystem and registers LspTool on DeepAgent.
 *
 * <p>Mirrors Python's {@code LspRail} in
 * {@code openjiuwen/harness/rails/lsp_rail.py}.</p>
 */
public class LspRail extends DeepAgentRail {

    public static final int PRIORITY = 60;
    private static final int DEFAULT_MAX_PER_FILE = 10;
    private static final int DEFAULT_MAX_TOTAL = 30;
    private static final String EDIT_FILE = "edit_file";
    private static final String WRITE_FILE = "write_file";

    private InitializeOptions options;
    private final boolean verbose;
    private final int maxPerFile;
    private final int maxTotal;
    private Tool lspTool;
    private boolean initialized;

    public LspRail() {
        this(null, false, DEFAULT_MAX_PER_FILE, DEFAULT_MAX_TOTAL);
    }

    public LspRail(InitializeOptions options) {
        this(options, false, DEFAULT_MAX_PER_FILE, DEFAULT_MAX_TOTAL);
    }

    public LspRail(InitializeOptions options, boolean verbose) {
        this(options, verbose, DEFAULT_MAX_PER_FILE, DEFAULT_MAX_TOTAL);
    }

    public LspRail(int maxPerFile, int maxTotal) {
        this(null, false, maxPerFile, maxTotal);
    }

    private LspRail(InitializeOptions options, boolean verbose, int maxPerFile, int maxTotal) {
        setPriority(PRIORITY);
        this.options = options;
        this.verbose = verbose;
        this.maxPerFile = maxPerFile;
        this.maxTotal = maxTotal;
    }

    @Override
    public void init(DeepAgent agent) {
        init((Object) agent);
    }

    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || deepAgent.deepConfig() == null) {
            return;
        }
        AbilityManager abilityManager = resolveAbilityManager(deepAgent, deepAgent.reactAgent());
        if (abilityManager == null) {
            return;
        }

        super.init(deepAgent);
        InitializeOptions effectiveOptions = buildEffectiveOptions();
        try {
            if (LspServerManager.getInstance() == null) {
                asyncInitLsp(effectiveOptions).join();
            }
        } catch (RuntimeException ignored) {
            // Python logs and continues when LSP initialization fails.
        }

        LspTool tool = new LspTool(
                null,
                deepAgent.deepConfig().getSysOperation(),
                deepAgent.deepConfig().getLanguage(),
                resolveWorkspaceRoot(getWorkspace()),
                deepAgent.getCard() == null ? null : deepAgent.getCard().getId()
        );
        try {
            Runner.resourceMgr().addTool(tool, null, true);
            abilityManager.add(tool.getCard());
            lspTool = tool;
            initialized = true;
        } catch (RuntimeException ignored) {
            initialized = false;
        }
    }

    @Override
    public void uninit(DeepAgent agent) {
        uninit((Object) agent);
    }

    public void uninit(Object agent) {
        AbilityManager abilityManager = null;
        if (agent instanceof DeepAgent deepAgent) {
            abilityManager = resolveAbilityManager(deepAgent, deepAgent.reactAgent());
        }
        if (lspTool != null) {
            if (abilityManager != null) {
                abilityManager.remove(lspTool.getCard().getName());
            }
            try {
                if (Runner.resourceMgr().getTool(lspTool.getCard().getId()) != null) {
                    Runner.resourceMgr().removeTool(lspTool.getCard().getId());
                }
            } catch (RuntimeException ignored) {
                // Python cleanup is best-effort and must not raise during rail teardown.
            }
        }
        try {
            asyncShutdownLsp().join();
        } catch (RuntimeException ignored) {
            // Keep teardown non-throwing, matching Python's exception handling.
        }
        lspTool = null;
        initialized = false;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        ctx.put("lsp_diagnostics", HarnessLspPackage.getPendingLspDiagnostics(maxPerFile, maxTotal));
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Object rawToolName = ctx.get("tool_name");
        String toolName = rawToolName == null ? "" : String.valueOf(rawToolName);
        if (!EDIT_FILE.equals(toolName) && !WRITE_FILE.equals(toolName)) {
            return;
        }
        String filePath = toolArg(ctx.get("tool_args"), "file_path");
        if (filePath.isBlank()) {
            return;
        }
        LspServerManager manager = LspServerManager.getInstance();
        if (manager == null) {
            return;
        }
        try {
            String resolvedPath = resolvePath(filePath);
            String languageId = languageId(resolvedPath);
            String uri = FileUriUtils.pathToFileUri(resolvedPath);
            if (manager.isFileOpen(uri)) {
                manager.changeFile(resolvedPath, languageId, null);
            } else {
                manager.openFile(resolvedPath, languageId);
            }
        } catch (RuntimeException ignored) {
            // Diagnostics refresh is fire-and-forget in Python.
        }
    }

    @Override
    public Map<String, String> getCallbacks() {
        Map<String, String> callbacks = new LinkedHashMap<>();
        callbacks.put(AgentCallbackEvent.BEFORE_MODEL_CALL.getValue(), "beforeModelCall");
        callbacks.put(AgentCallbackEvent.AFTER_TOOL_CALL.getValue(), "afterToolCall");
        return callbacks;
    }

    public InitializeOptions getOptions() {
        return options;
    }

    public Tool getLspTool() {
        return lspTool;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public CompletableFuture<InitializeResult> asyncInitLsp(InitializeOptions initializeOptions) {
        try {
            CompletableFuture<InitializeResult> future = doInitializeLsp(initializeOptions);
            return future.exceptionally(error -> failedInitializeResult());
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(failedInitializeResult());
        }
    }

    protected CompletableFuture<InitializeResult> doInitializeLsp(InitializeOptions initializeOptions) {
        return HarnessLspPackage.initializeLsp(initializeOptions);
    }

    public CompletableFuture<Void> asyncShutdownLsp() {
        try {
            CompletableFuture<Void> future = doShutdownLsp();
            return future.exceptionally(error -> null);
        } catch (RuntimeException error) {
            return CompletableFuture.completedFuture(null);
        }
    }

    protected CompletableFuture<Void> doShutdownLsp() {
        return HarnessLspPackage.shutdownLsp();
    }

    private InitializeOptions buildEffectiveOptions() {
        String effectiveCwd = resolveWorkspaceRoot(getWorkspace());
        if (options == null) {
            InitializeOptions created = new InitializeOptions();
            created.setCwd(effectiveCwd);
            return created;
        }
        if ((options.getCwd() == null || options.getCwd().isBlank()) && effectiveCwd != null && !effectiveCwd.isBlank()) {
            InitializeOptions copied = new InitializeOptions();
            copied.setCwd(effectiveCwd);
            copied.setCustomServers(options.getCustomServers());
            return copied;
        }
        return options;
    }

    private String resolvePath(String filePath) {
        Path path = Path.of(filePath);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize().toString();
        }
        String root = resolveWorkspaceRoot(getWorkspace());
        Path base = root == null || root.isBlank() ? Path.of(System.getProperty("user.dir")) : Path.of(root);
        return base.resolve(path).toAbsolutePath().normalize().toString();
    }

    private static String languageId(String path) {
        String fileName = Path.of(path).getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot).toLowerCase();
        if (".py".equals(ext) || ".pyi".equals(ext)) {
            return "python";
        }
        return ext.isBlank() ? "plaintext" : ext.substring(1);
    }

    private static String toolArg(Object args, String key) {
        if (args instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    private static InitializeResult failedInitializeResult() {
        InitializeResult result = new InitializeResult();
        result.setSuccess(false);
        result.setServersLoaded(0);
        result.setDurationMs(0.0d);
        return result;
    }

    private static AbilityManager resolveAbilityManager(Object... candidates) {
        for (Object candidate : candidates) {
            Object value = invokeNoArg(candidate, "getAbilityManager", "get_ability_manager");
            if (value instanceof AbilityManager manager) {
                return manager;
            }
            Object fieldValue = readField(candidate, "abilityManager", "ability_manager");
            if (fieldValue instanceof AbilityManager manager) {
                return manager;
            }
        }
        return null;
    }

    private static String resolveWorkspaceRoot(Object workspace) {
        if (workspace == null) {
            return null;
        }
        if (workspace instanceof String value) {
            return value;
        }
        if (workspace instanceof Path path) {
            return path.toString();
        }
        Object value = invokeNoArg(workspace, "getRootPath", "rootPath", "get_root_path");
        if (value != null) {
            return String.valueOf(value);
        }
        Object fieldValue = readField(workspace, "rootPath", "root_path");
        return fieldValue == null ? null : String.valueOf(fieldValue);
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next Python/Java accessor spelling.
            }
        }
        return null;
    }

    private static Object readField(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = type.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the next field spelling.
                } catch (IllegalAccessException error) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
