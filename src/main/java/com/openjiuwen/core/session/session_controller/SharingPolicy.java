/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Downstream session sharing policy.
 *
 * <p>Mirrors Python's {@code SharingPolicy} in
 * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
 */
public class SharingPolicy {

    private Permission permission = Permission.READ;

    private Set<String> fieldScopes;

    public SharingPolicy() {
    }

    public SharingPolicy(Permission permission, Set<String> fieldScopes) {
        this.permission = permission == null ? Permission.READ : permission;
        this.fieldScopes = fieldScopes == null ? null : new LinkedHashSet<>(fieldScopes);
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission == null ? Permission.READ : permission;
    }

    public Set<String> getFieldScopes() {
        return fieldScopes == null ? null : new LinkedHashSet<>(fieldScopes);
    }

    public void setFieldScopes(Set<String> fieldScopes) {
        this.fieldScopes = fieldScopes == null ? null : new LinkedHashSet<>(fieldScopes);
    }
}
