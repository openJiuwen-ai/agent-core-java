/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.Model;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Model backup rail for fallback on model exception.
 *
 * <p>Mirrors Python's {@code ModelBackupRail} in
 * {@code openjiuwen/core/single_agent/rail/model_backup.py}.</p>
 */
public class ModelBackupRail extends AgentRail {

    private final List<Model> backupModels;

    private int index;

    public ModelBackupRail(List<Model> backupModels) {
        this.backupModels = backupModels == null ? List.of() : new ArrayList<>(backupModels);
    }

    @Override
    public CompletionStage<Void> onModelException(AgentCallbackContext context) {
        if (context != null && index < backupModels.size() && setLlm(context.getAgent(), backupModels.get(index))) {
            index++;
            context.requestRetry(0);
        }
        return completed();
    }

    public List<Model> getBackupModels() {
        return new ArrayList<>(backupModels);
    }

    public int getIndex() {
        return index;
    }

    private boolean setLlm(Object agent, Model llm) {
        if (agent == null) {
            return false;
        }
        for (String methodName : List.of("setLlm", "set_llm")) {
            if (invokeSetLlm(agent, methodName, llm)) {
                return true;
            }
        }
        return false;
    }

    private boolean invokeSetLlm(Object agent, String methodName, Model llm) {
        Class<?> type = agent.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (llm != null && !parameterType.isInstance(llm) && parameterType != Object.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(agent, llm);
                    return true;
                } catch (ReflectiveOperationException ignored) {
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }
}
