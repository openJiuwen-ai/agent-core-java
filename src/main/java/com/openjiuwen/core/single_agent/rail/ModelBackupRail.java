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
        if (index < backupModels.size() && setLlm(ctx.getAgent(), backupModels.get(index))) {
            index++;
            ctx.requestRetry(0);
        }
    }

    /**
     * Set LLM on agent.
     */
    private boolean setLlm(Object agent, Object llm) {
        if (agent == null) {
            return false;
        }
        Class<?> type = agent.getClass();
        while (type != null) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("setLlm") || method.getParameterCount() != 1) {
                    continue;
                }
                if (llm != null && !method.getParameterTypes()[0].isInstance(llm)
                        && method.getParameterTypes()[0] != Object.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(agent, llm);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        try {
            java.lang.reflect.Method method = agent.getClass().getMethod("setLlm", Object.class);
            method.setAccessible(true);
            method.invoke(agent, llm);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
