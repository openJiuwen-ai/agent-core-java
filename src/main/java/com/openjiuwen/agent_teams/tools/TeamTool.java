/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.Iterator;
import java.util.Map;

/**
 * Base class for minimal Java team tools.
 *
 * <p>Mirrors Python's {@code TeamTool} in
 * {@code openjiuwen.agent_teams.tools.team_tools}.
 */
public abstract class TeamTool extends Tool {

    protected final TeamBackend team;

    protected TeamTool(ToolCard card, TeamBackend team) {
        super(card);
        this.team = team;
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return java.util.List.of(invoke(inputs, kwargs)).iterator();
    }

    protected static ToolCard toolCard(String id, String name, String description) {
        ToolCard card = new ToolCard();
        assignField(card, "id", id);
        assignField(card, "name", name);
        assignField(card, "description", description);
        return card;
    }

    protected static void assignField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
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
