/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.SysOperation;

import java.util.Iterator;
import java.util.Map;

/**
 * Base class for minimal harness tools.
 *
 * <p>Mirrors Python's base harness tool abstractions in
 * {@code openjiuwen.harness.tools.base_tool}.
 */
public abstract class AbstractHarnessTool extends Tool {

    protected final SysOperation sysOperation;

    protected AbstractHarnessTool(ToolCard card, SysOperation sysOperation) {
        super(card);
        this.sysOperation = sysOperation;
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return java.util.List.of(invoke(inputs, kwargs)).iterator();
    }

    protected static ToolCard toolCard(String id, String name, String description) {
        ToolCard card = new ToolCard();
        assignCardField(card, "id", id);
        assignCardField(card, "name", name);
        assignCardField(card, "description", description);
        return card;
    }

    protected static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    protected static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value != null ? String.valueOf(value) : null;
    }

    protected static Integer readIntField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static void assignCardField(Object target, String fieldName, Object value) {
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
        throw new IllegalStateException("Field not found: " + fieldName);
    }
}
