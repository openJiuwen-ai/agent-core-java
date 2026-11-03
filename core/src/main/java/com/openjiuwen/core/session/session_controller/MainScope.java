/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

/**
 * Mirrors Python's {@code MainScope} in
 * {@code openjiuwen/core/session/session_controller/scope.py}.
 */
public final class MainScope implements Scope {

    @Override
    public String toString() {
        return "main";
    }

    public static MainScope fromString(String scopeString) {
        if (!"main".equals(scopeString)) {
            throw new IllegalArgumentException("Expected 'main', got '" + scopeString + "'");
        }
        return new MainScope();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MainScope;
    }

    @Override
    public int hashCode() {
        return "main".hashCode();
    }
}
