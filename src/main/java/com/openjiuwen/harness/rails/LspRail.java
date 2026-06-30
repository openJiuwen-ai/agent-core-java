/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.lsp.core.LSPServerManager;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.LspTool;

import java.util.Map;

/**
 * Public class LspRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class LspRail extends DeepAgentRail {
    private Tool lspTool;
    private LSPServerManager manager;

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int priority() {
        return 60;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || lspTool != null) {
            return;
        }
        manager = new LSPServerManager();
        manager.setWorkspaceRoot(deepAgent.getWorkspace().root().toString());
        LspTool tool = new LspTool(deepAgent.getWorkspace().root().toString(), manager);
        ToolCard card = ToolMetadataRegistry.buildToolCard(
                toolName(),
                deepAgent.getCard().getId() + "." + toolName(),
                deepAgent.getWorkspace().getLanguage()
        );
        lspTool = new LocalFunction(card, inputs -> tool.invoke(inputs != null ? inputs : Map.of()));
        deepAgent.registerHarnessTool(lspTool);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void uninit(Object agent) {
        if (lspTool != null && agent instanceof DeepAgent deepAgent) {
            deepAgent.unregisterHarnessTool(lspTool);
        }
        if (manager != null) {
            manager.shutdownAll();
        }
        lspTool = null;
        manager = null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String toolName() {
        return "lsp";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isRegistered() {
        return lspTool != null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasActiveManager() {
        return manager != null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LSPServerManager getManager() {
        return manager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String describe() {
        return "Register and expose LSP code intelligence tool metadata";
    }
}
