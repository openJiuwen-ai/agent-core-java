/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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

    public TeamBackend getTeam() {
        return team;
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return java.util.List.of(invoke(inputs, kwargs)).iterator();
    }

    public String mapResult(TeamToolOutput output) {
        if (output == null) {
            return "";
        }
        if (!output.isSuccess()) {
            return output.getError() != null ? output.getError() : "Operation failed";
        }
        return formatValue(output.getData());
    }

    public MappedToolOutput mappedResult(TeamToolOutput output) {
        return MappedToolOutput.fromOutput(
                ToolOutput.of(output.isSuccess(), output.getData(), output.getError()),
                mapResult(output)
        );
    }

    protected static ToolCard toolCard(String id, String name, String description) {
        return toolCard(id, name, description, Map.of(), List.of());
    }

    protected static ToolCard toolCard(
            String id,
            String name,
            String description,
            Map<String, Object> properties,
            List<String> required
    ) {
        ToolCard card = new ToolCard();
        card.setId(id);
        card.setName(name);
        card.setDescription(description);
        card.setInputParams(inputSchema(properties, required));
        return card;
    }

    protected static Map<String, Object> inputSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties != null ? new LinkedHashMap<>(properties) : new LinkedHashMap<>());
        if (required != null && !required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        return schema;
    }

    protected static Map<String, Object> stringParam(String description) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "string");
        param.put("description", description);
        return param;
    }

    protected static Map<String, Object> booleanParam(String description) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "boolean");
        param.put("description", description);
        return param;
    }

    protected static Map<String, Object> arrayParam(String description) {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("type", "array");
        param.put("description", description);
        return param;
    }

    @SuppressWarnings("unchecked")
    protected static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    protected static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static String formatValue(Object value) {
        if (value == null) {
            return "OK";
        }
        if (value instanceof Map<?, ?> map) {
            StringJoiner joiner = new StringJoiner(" ");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                joiner.add(entry.getKey() + "=" + formatValue(entry.getValue()));
            }
            return joiner.toString();
        }
        if (value instanceof List<?> list) {
            StringJoiner joiner = new StringJoiner("\n");
            for (Object item : list) {
                joiner.add(formatValue(item));
            }
            return joiner.toString();
        }
        return String.valueOf(value);
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
