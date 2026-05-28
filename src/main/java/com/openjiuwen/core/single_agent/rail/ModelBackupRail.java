/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import java.util.List;

/**
 * Model backup rail for fallback on model exception.
 *
 * <p>Mirrors Python's {@code ModelBackupRail} in
 * {@code openjiuwen.core.single_agent.rail.model_backup}.</p>
 */
public class ModelBackupRail extends AgentRail {

    /** Backup models list. */
    private final List<Object> backupModels;

    /** Current backup model index. */
    private int index = 0;

    /**
     * Create ModelBackupRail.
     *
     * @param backupModels list of backup models
     */
    public ModelBackupRail(List<Object> backupModels) {
        this.backupModels = backupModels;
    }

    @Override
    public void onModelException(AgentCallbackContext ctx) {
        // Try to set backup LLM if agent supports it
        if (hasSetLlmMethod(ctx.getAgent()) && index < backupModels.size()) {
            setLlm(ctx.getAgent(), backupModels.get(index));
            index++;
            ctx.requestRetry(0);
        }
    }

    /**
     * Check if agent has set_llm method.
     */
    private boolean hasSetLlmMethod(Object agent) {
        try {
            agent.getClass().getMethod("setLlm", Object.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Set LLM on agent.
     */
    private void setLlm(Object agent, Object llm) {
        try {
            java.lang.reflect.Method method = agent.getClass().getMethod("setLlm", Object.class);
            method.invoke(agent, llm);
        } catch (Exception e) {
            // Ignore
        }
    }
}