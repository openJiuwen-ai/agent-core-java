/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.Field;

/**
 * Entry point for creating Java harness agents.
 *
 * <p>Mirrors Python's {@code create_deep_agent} style factory flow in
 * {@code openjiuwen.harness.factory}.
 *
 * <p>This intentionally creates a minimal but executable harness agent backed
 * by the existing Java ReAct runtime.
 */
public final class HarnessFactory {

    private HarnessFactory() {
    }

    public static DeepAgent createDeepAgent() {
        AgentCard card = createCard("deep_agent", "Harness deep agent");
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(card);
        return createDeepAgent(config);
    }

    public static DeepAgent createDeepAgent(DeepAgentConfig config) {
        DeepAgentConfig effectiveConfig = config != null ? config : new DeepAgentConfig();
        AgentCard card = effectiveConfig.getCard();
        if (card == null) {
            card = createCard("deep_agent", "Harness deep agent");
            effectiveConfig.setCard(card);
        }
        DeepAgent agent = new DeepAgent(card);
        agent.configure(effectiveConfig);
        return agent;
    }

    private static AgentCard createCard(String name, String description) {
        AgentCard card = new AgentCard();
        assignField(card, "name", name);
        assignField(card, "description", description);
        return card;
    }

    private static void assignField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to set field '" + fieldName + "'", e);
            }
        }
        throw new IllegalStateException("Field not found: " + fieldName + " on " + target.getClass().getName());
    }
}
