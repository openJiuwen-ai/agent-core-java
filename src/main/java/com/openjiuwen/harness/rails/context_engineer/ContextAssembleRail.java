/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.ContextSection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.prompts.sections.WorkspaceSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Rail that injects workspace, tools, and context sections into a system prompt builder.
 *
 * <p>Mirrors Python's {@code ContextAssembleRail} in
 * {@code openjiuwen/harness/rails/context_engineer/context_assemble_rail.py}.</p>
 */
public class ContextAssembleRail extends DeepAgentRail {

    private static final int PRIORITY = 85;
    private static final String DEFAULT_LANGUAGE = "cn";

    private SystemPromptBuilder systemPromptBuilder;
    private AbilityManager abilityManager;

    public ContextAssembleRail() {
        setPriority(PRIORITY);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        Object reactAgent = agent == null ? null : agent.reactAgent();
        systemPromptBuilder = resolveSystemPromptBuilder(agent, reactAgent);
        abilityManager = resolveAbilityManager(agent, reactAgent);
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.WORKSPACE);
            systemPromptBuilder.removeSection(SectionName.CONTEXT);
        }
        systemPromptBuilder = null;
        abilityManager = null;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        Workspace workspace = normalizeWorkspace(getWorkspace(), language());
        if (workspace == null) {
            systemPromptBuilder.removeSection(SectionName.WORKSPACE);
            systemPromptBuilder.removeSection(SectionName.CONTEXT);
            return;
        }

        String language = language();
        Object sysOperation = getSysOperation();
        PromptSection workspaceSection = WorkspaceSection.buildWorkspaceSection(sysOperation, workspace, language);
        PromptSection toolsSection = ContextSection.buildToolsSection(abilityManager, language);
        PromptSection contextSection = ContextSection.buildContextSection(
                sysOperation instanceof SysOperation operation ? operation : null,
                workspace,
                language,
                null,
                null,
                includeDailyMemory(ctx)
        );

        replaceOrRemove(SectionName.WORKSPACE, workspaceSection);
        replaceOrRemove(SectionName.TOOLS, toolsSection);
        replaceOrRemove(SectionName.CONTEXT, contextSection);
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    static boolean includeDailyMemory(CallbackContext ctx) {
        return !isHeartbeat(runKindValue(ctx));
    }

    private String language() {
        String language = systemPromptBuilder == null ? null : systemPromptBuilder.getLanguage();
        return "en".equals(language) ? "en" : DEFAULT_LANGUAGE;
    }

    private void replaceOrRemove(String sectionName, PromptSection section) {
        if (section == null) {
            systemPromptBuilder.removeSection(sectionName);
        } else {
            systemPromptBuilder.addSection(section);
        }
    }

    private static Object runKindValue(CallbackContext ctx) {
        if (ctx == null) {
            return null;
        }
        Map<String, Object> values = ctx.getValues();
        Object direct = values.get("run_kind");
        if (direct != null) {
            return direct;
        }
        Object extra = values.get("extra");
        if (extra instanceof Map<?, ?> extraMap) {
            return extraMap.get("run_kind");
        }
        return null;
    }

    private static boolean isHeartbeat(Object runKind) {
        if (runKind == RunKind.HEARTBEAT) {
            return true;
        }
        String value = Objects.toString(runKind, "");
        return RunKind.HEARTBEAT.getValue().equals(value) || RunKind.HEARTBEAT.name().equalsIgnoreCase(value);
    }

    private static Workspace normalizeWorkspace(Object workspace, String language) {
        if (workspace == null) {
            return null;
        }
        if (workspace instanceof Workspace typedWorkspace) {
            return typedWorkspace;
        }
        if (workspace instanceof Path path) {
            return new Workspace(path.toString(), language);
        }
        if (workspace instanceof CharSequence text) {
            return new Workspace(text.toString(), language);
        }
        String rootPath = readStringProperty(workspace, "getRootPath", "rootPath");
        if (rootPath == null || rootPath.isBlank()) {
            return null;
        }
        return new Workspace(rootPath, language);
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object... candidates) {
        for (Object candidate : candidates) {
            Object value = invokeNoArg(candidate, "getSystemPromptBuilder", "getPromptBuilder");
            if (value instanceof SystemPromptBuilder builder) {
                return builder;
            }
            Object fieldValue = readField(candidate, "systemPromptBuilder", "system_prompt_builder", "promptBuilder");
            if (fieldValue instanceof SystemPromptBuilder builder) {
                return builder;
            }
        }
        return null;
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

    private static String readStringProperty(Object target, String... names) {
        for (String name : names) {
            Object value = invokeNoArg(target, name);
            if (value != null) {
                return String.valueOf(value);
            }
            value = readField(target, name);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
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
                } catch (IllegalAccessException exception) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
