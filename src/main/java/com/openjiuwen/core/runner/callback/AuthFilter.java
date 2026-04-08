/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;

/**
 * Authorization filter for role-based access control.
 * <p>
 * Validates that callbacks are executed only by authorized users with the required role.
 */
public class AuthFilter extends EventFilter {

    private final String requiredRole;

    public AuthFilter(String requiredRole) {
        this(requiredRole, "Auth");
    }

    public AuthFilter(String requiredRole, String name) {
        super(name);
        this.requiredRole = requiredRole;
    }

    @Override
    public FilterResult filter(String event, CallbackInfo callback,
                                Object[] args, Map<String, Object> kwargs) {
        String userRole = kwargs != null
                ? String.valueOf(kwargs.getOrDefault("user_role", "guest"))
                : "guest";

        if (!requiredRole.equals(userRole)) {
            return FilterResult.skipResult(
                    "Unauthorized: requires " + requiredRole + ", got " + userRole);
        }
        return FilterResult.continueResult();
    }
}
