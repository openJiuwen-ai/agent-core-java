/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * Small helpers for team plan-mode configuration.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.runtime.team_plan} in
 * {@code openjiuwen/agent_teams/runtime/team_plan.py}.</p>
 */
public final class TeamPlan {
    private static final Object MISSING = new Object();

    private TeamPlan() {
    }

    public static boolean isTeamPlanEnabled(Object spec) {
        return isTruthy(getField(spec, "enable_team_plan", "enableTeamPlan"));
    }

    private static Object getField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            if (target instanceof Map<?, ?> map && map.containsKey(name)) {
                return map.get(name);
            }
            Object value = readMember(target, name);
            if (value != MISSING) {
                return value;
            }
        }
        return null;
    }

    private static Object readMember(Object target, String name) {
        for (String methodName : new String[] {name, "get" + accessorSuffix(name), "is" + accessorSuffix(name)}) {
            try {
                Method method = target.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(target);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        try {
            Field field = target.getClass().getField(name);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
        }
        return MISSING;
    }

    private static String accessorSuffix(String name) {
        StringBuilder builder = new StringBuilder(name.length());
        boolean upperNext = true;
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            if (current == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(current) : current);
            upperNext = false;
        }
        return builder.toString();
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence sequenceValue) {
            return sequenceValue.length() > 0;
        }
        if (value instanceof Collection<?> collectionValue) {
            return !collectionValue.isEmpty();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }
}
