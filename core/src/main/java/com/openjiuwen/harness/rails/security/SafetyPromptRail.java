/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
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

    public SafetyPromptRail() {
        setPriority(85);
        setSupportedEvents(java.util.Set.of(BEFORE_MODEL_CALL));
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        systemPromptBuilder = resolveSystemPromptBuilder(agent, agent == null ? null : agent.reactAgent());
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.SAFETY);
        }
        systemPromptBuilder = null;
    }

    /**
     * Updates the already prepared request as well as the builder used by later calls.
     *
     * @param context current native model callback
     */
    @Override
    public void beforeModelCall(AgentCallbackContext context) {
        CallbackContext callback = toCallbackContext(context);
        callback.put("agent", context.getAgent());
        refreshSystemPromptBuilder(callback);
        if (systemPromptBuilder == null) {
            return;
        }
        String previousPrompt = systemPromptBuilder.build();
        String previousSafety = systemPromptBuilder.getSection(SectionName.SAFETY)
                .map(section -> section.render(systemPromptBuilder.getLanguage())).orElse("");
        beforeModelCall(callback);
        applyCallbackContext(context, callback);
        if (context.getInputs() instanceof ModelCallInputs inputs
                && callback.get("safety_section") instanceof PromptSection section) {
            updateRequest(inputs, previousPrompt, previousSafety,
                    section.render(systemPromptBuilder.getLanguage()));
        }
    }

    private void updateRequest(ModelCallInputs inputs, String previousPrompt,
                               String previousSafety, String safety) {
        // Replace only the builder-owned message/section. Never rebuild the context
        // window: another rail may already have projected messages or filtered tools.
        for (Object value : inputs.getMessages()) {
            if (value instanceof BaseMessage message && "system".equals(message.getRole())
                    && message.getContent() instanceof String content) {
                if (!previousPrompt.isBlank() && content.equals(previousPrompt)) {
                    message.setContent(systemPromptBuilder.build());
                    return;
                }
                if (!previousSafety.isBlank() && content.contains(previousSafety)) {
                    message.setContent(content.replace(previousSafety, safety));
                    return;
                }
                if (content.contains(safety)) {
                    return;
                }
            }
        }
        // A rail replaced the builder message, or the original prompt was empty.
        // Preserve that request and add only this rail's section.
        inputs.getMessages().add(0, new SystemMessage(safety));
    }

    private void refreshSystemPromptBuilder(CallbackContext ctx) {
        SystemPromptBuilder current = resolveSystemPromptBuilder(ctx.get("agent"),
                ctx.getAgent() == null || systemPromptBuilder == null ? null : ctx.getAgent().reactAgent(),
                ctx.getAgent());
        if (current != null) {
            systemPromptBuilder = current;
        }
    }

    @Override
    protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
        CallbackContext ctx = securityCtx.callbackContext();
        refreshSystemPromptBuilder(ctx);
        if (systemPromptBuilder == null) {
            return allow();
        }

        String language = systemPromptBuilder.getLanguage();
        PromptSection section = SafetySection.buildSafetySection(language);
        systemPromptBuilder.removeSection(SectionName.SAFETY);
        systemPromptBuilder.addSection(section);
        ctx.put("safety_section", section);
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
