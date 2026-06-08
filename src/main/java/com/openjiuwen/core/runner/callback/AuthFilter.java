/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code AuthFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
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
    public FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        String userRole = String.valueOf(safeKwargs(kwargs).getOrDefault("user_role", "guest"));
        if (!requiredRole.equals(userRole)) {
            return FilterResult.skipResult(
                    "Unauthorized: requires " + requiredRole + ", got " + userRole
            );
        }
        return FilterResult.continueResult();
    }
}
