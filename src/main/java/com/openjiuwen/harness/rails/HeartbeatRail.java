/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.prompts.sections.HeartbeatSection;
import com.openjiuwen.harness.workspace.WorkspaceNode;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rail that injects heartbeat system prompt.
 *
 * <p>Mirrors Python's {@code HeartbeatRail} in
 * {@code openjiuwen.harness.rails.heartbeat_rail}.</p>
 */
public class HeartbeatRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatRail.class);

    public static final int PRIORITY = 80;

    private Object systemPromptBuilder;
    private Path heartbeatPath;

    public HeartbeatRail() {
        setPriority(PRIORITY);
    }

    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            this.systemPromptBuilder = deepAgent.getSystemPromptBuilder();
            Object configObj = deepAgent.getConfig();
            if (configObj instanceof DeepAgentConfig config) {
                if (this.sysOperation == null) {
                    this.sysOperation = config.getSysOperation();
                }
                if (this.workspace == null) {
                    this.workspace = config.getWorkspace();
                }
            }
        } else {
            try {
                this.systemPromptBuilder = agent.getClass().getMethod("getSystemPromptBuilder").invoke(agent);
            } catch (Exception ignored) {
                this.systemPromptBuilder = null;
            }
        }
        if (workspace != null) {
            this.heartbeatPath = workspace.getNodePath(WorkspaceNode.HEARTBEAT_MD);
        }
    }

    @Override
    public void uninit(Object agent) {
        removeHeartbeatSection();
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof InvokeInputs inputs)) {
            return;
        }
        if (inputs.getRunKind() == RunKind.HEARTBEAT) {
            ctx.getExtra().put("run_kind", inputs.getRunKind());
            ctx.getExtra().put("run_context", inputs.getRunContext());
        }
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (systemPromptBuilder == null || ctx.getExtra().get("run_kind") != RunKind.HEARTBEAT) {
            return;
        }
        if (sysOperation == null || heartbeatPath == null) {
            LOG.warn("HeartbeatRail: sys_operation or workspace not configured");
            return;
        }

        String content = "";
        BaseFsOperation fs = sysOperation.fs();
        if (fs != null) {
            ReadFileResult readResult = fs.readFile(
                    heartbeatPath.toString(),
                    "text",
                    null,
                    null,
                    null,
                    "UTF-8",
                    0,
                    Map.of());
            if (readResult != null && readResult.getCode() == 0 && readResult.getData() != null) {
                content = readResult.getData().getContentAsString();
            } else {
                LOG.warn("HeartbeatRail: failed to read HEARTBEAT.md");
            }
        }

        try {
            String language = String.valueOf(systemPromptBuilder.getClass().getMethod("getLanguage").invoke(systemPromptBuilder));
            Object section = HeartbeatSection.build(language, content);
            systemPromptBuilder.getClass().getMethod("addSection", section.getClass()).invoke(systemPromptBuilder, section);
        } catch (NoSuchMethodException e) {
            try {
                Object section = HeartbeatSection.build("cn", content);
                systemPromptBuilder.getClass().getMethod("addSection",
                                com.openjiuwen.core.single_agent.prompts.PromptSection.class)
                        .invoke(systemPromptBuilder, section);
            } catch (Exception inner) {
                throw new IllegalStateException("Failed to inject heartbeat section", inner);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inject heartbeat section", e);
        }
    }

    private void removeHeartbeatSection() {
        if (systemPromptBuilder == null) {
            return;
        }
        try {
            systemPromptBuilder.getClass().getMethod("removeSection", String.class)
                    .invoke(systemPromptBuilder, "heartbeat");
        } catch (Exception e) {
            LOG.debug("HeartbeatRail: could not remove heartbeat section", e);
        }
    }
}
