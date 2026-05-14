/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.lsp.LspInitializeOptions;
import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.LspDiagnosticsTool;
import com.openjiuwen.harness.tools.LspDocumentSyncTool;
import com.openjiuwen.harness.tools.LspFindReferencesTool;
import com.openjiuwen.harness.tools.LspGotoDefinitionTool;
import com.openjiuwen.harness.tools.LspImplementationTool;
import com.openjiuwen.harness.tools.LspCallHierarchyTool;
import com.openjiuwen.harness.tools.LspIncomingCallsTool;
import com.openjiuwen.harness.tools.LspOutgoingCallsTool;
import com.openjiuwen.harness.tools.LspPublishDiagnosticsTool;
import com.openjiuwen.harness.tools.LspSymbolsTool;
import com.openjiuwen.harness.tools.LspTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Java harness rail that initializes the LSP subsystem and registers LSP tools.
 *
 * <p>Mirrors Python's {@code LspRail} in {@code openjiuwen.harness.rails.lsp_rail}.
 */
public class LspRail extends DeepAgentRail {

    private final LspInitializeOptions options;
    private final List<com.openjiuwen.core.foundation.tool.Tool> tools = new ArrayList<>();

    public LspRail() {
        this(null);
    }

    public LspRail(LspInitializeOptions options) {
        this.options = options;
        setPriority(60);
    }

    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        DeepAgentConfig config = (DeepAgentConfig) deepAgent.getConfig();
        String cwd = options != null && options.getCwd() != null && !options.getCwd().isBlank()
                ? options.getCwd()
                : config != null && config.getWorkspace() != null ? config.getWorkspace().getRootPath() : null;

        LspInitializeOptions effectiveOptions = new LspInitializeOptions();
        effectiveOptions.setCwd(cwd);
        if (options != null) {
            effectiveOptions.setCustomServers(options.getCustomServers());
        }
        LspServerManager.initialize(effectiveOptions);

        tools.clear();
        tools.add(new LspTool());
        tools.add(new LspDiagnosticsTool());
        tools.add(new LspSymbolsTool());
        tools.add(new LspGotoDefinitionTool());
        tools.add(new LspFindReferencesTool());
        tools.add(new LspImplementationTool());
        tools.add(new LspCallHierarchyTool());
        tools.add(new LspIncomingCallsTool());
        tools.add(new LspOutgoingCallsTool());
        tools.add(new LspPublishDiagnosticsTool());
        tools.add(new LspDocumentSyncTool());

        for (com.openjiuwen.core.foundation.tool.Tool tool : tools) {
            Runner.resourceMgr().addTool(tool, readField(deepAgent.getCard(), "id"));
            deepAgent.getDelegate().getAbilityManager().add(tool.getCard());
        }
    }

    @Override
    public void uninit(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        for (com.openjiuwen.core.foundation.tool.Tool tool : tools) {
            deepAgent.getDelegate().getAbilityManager().remove(readStringField(tool.getCard(), "name"));
            Runner.resourceMgr().removeTool(readField(tool.getCard(), "id"), readField(deepAgent.getCard(), "id"),
                    TagMatchStrategy.ALL, true);
        }
        tools.clear();
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }
}
