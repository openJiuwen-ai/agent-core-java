/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.prompts.sections.SafetySection;
import com.openjiuwen.harness.rails.CallbackContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Rail that injects the safety prompt section into model-call context.
 *
 * <p>Mirrors Python's {@code SafetyPromptRail} in
 * {@code openjiuwen/harness/rails/security/prompt_security_rail.py}.</p>
 */
public class SafetyPromptRail extends BaseSecurityRail {

    private SystemPromptBuilder systemPromptBuilder;
    private final PromptSection safetySection = SafetySection.buildMultilingualSection();

    public SafetyPromptRail() {
        setPriority(85);
        setSupportedEvents(java.util.Set.of(BEFORE_MODEL_CALL));
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        systemPromptBuilder = resolveSystemPromptBuilder(agent, agent == null ? null : agent.reactAgent());
        prepareSafetySection();
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.SAFETY);
        }
        systemPromptBuilder = null;
    }

    /**
     * Prepare before the first request is rendered, including after configure replaces the builder.
     *
     * @param ctx outer invocation context
     */
    @Override
    public void beforeInvoke(CallbackContext ctx) {
        super.beforeInvoke(ctx);
        if (ctx != null) {
            refreshSystemPromptBuilder(ctx);
            prepareSafetySection();
        }
    }

    private void prepareSafetySection() {
        if (!getSupportedEvents().contains(BEFORE_MODEL_CALL)) {
            removeOwnedSection();
            return;
        }
        if (systemPromptBuilder != null
                && (!systemPromptBuilder.hasSection(SectionName.SAFETY)
                    || systemPromptBuilder.getSection(SectionName.SAFETY).orElse(null) == safetySection)) {
            // ReActAgent clears transient sections before each inner invocation.
            systemPromptBuilder.addPersistentSection(safetySection);
        }
    }

    private void removeOwnedSection() {
        if (systemPromptBuilder != null
                && systemPromptBuilder.getSection(SectionName.SAFETY).orElse(null) == safetySection) {
            systemPromptBuilder.removeSection(SectionName.SAFETY);
        }
    }

    private void refreshSystemPromptBuilder(CallbackContext ctx) {
        SystemPromptBuilder current = resolveSystemPromptBuilder(
                ctx.getAgent() == null ? null : ctx.getAgent().reactAgent(), ctx.getAgent(), ctx.get("agent"));
        if (current != null && current != systemPromptBuilder) {
            removeOwnedSection();
            systemPromptBuilder = current;
        }
    }

    @Override
    protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
        CallbackContext ctx = securityCtx.callbackContext();
        if (systemPromptBuilder == null) {
            systemPromptBuilder = resolveSystemPromptBuilder(ctx.getAgent(), ctx.get("agent"));
        }
        if (systemPromptBuilder == null) {
            return allow();
        }
        systemPromptBuilder.addPersistentSection(safetySection);
        ctx.put("safety_section", safetySection);
        return allow();
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate instanceof SystemPromptBuilder builder) {
                return builder;
            }
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

    private static Object invokeNoArg(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next compatibility name.
            }
        }
        return null;
    }

    private static Object readField(Object target, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    current = current.getSuperclass();
                }
            }
        }
        return null;
    }
}
