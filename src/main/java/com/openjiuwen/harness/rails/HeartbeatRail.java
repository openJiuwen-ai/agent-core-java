/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.sections.HeartbeatSection;
import com.openjiuwen.harness.prompts.sections.SectionName;

/**
 * Injects heartbeat prompt reminders before model calls.
 *
 * <p>Mirrors Python's {@code HeartbeatRail} in
 * {@code openjiuwen/harness/rails/heartbeat_rail.py}.</p>
 */
public class HeartbeatRail extends DeepAgentRail {

    private SystemPromptBuilder systemPromptBuilder;

    public HeartbeatRail() {
        setPriority(80);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        String language = agent == null || agent.deepConfig() == null ? "cn" : agent.deepConfig().getLanguage();
        systemPromptBuilder = new SystemPromptBuilder(language, null);
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.HEARTBEAT);
        }
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    void setSystemPromptBuilder(SystemPromptBuilder systemPromptBuilder) {
        this.systemPromptBuilder = systemPromptBuilder;
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Object inputs = ctx.get("inputs");
        if (!(inputs instanceof InvokeInputs invokeInputs) || !invokeInputs.isHeartbeat()) {
            return;
        }
        ctx.put("run_kind", invokeInputs.getRunKind());
        ctx.put("run_context", invokeInputs.getRunContext());
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null || !isHeartbeatContext(ctx) || systemPromptBuilder == null) {
            return;
        }
        PromptSection section = HeartbeatSection.build(systemPromptBuilder.getLanguage());
        systemPromptBuilder.addSection(section);
        ctx.put("heartbeat_section", section);
        ctx.put("heartbeat_enabled", true);
    }

    private static boolean isHeartbeatContext(CallbackContext ctx) {
        Object runKind = ctx.get("run_kind");
        if (runKind == RunKind.HEARTBEAT) {
            return true;
        }
        Object inputs = ctx.get("inputs");
        return inputs instanceof InvokeInputs invokeInputs && invokeInputs.isHeartbeat();
    }
}
