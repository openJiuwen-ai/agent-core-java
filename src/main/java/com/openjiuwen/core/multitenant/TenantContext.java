/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Immutable tenant context carrying the tenant id and extension attributes.
 *
 * @since 0.1.7
 */
@Data
@Builder
public class TenantContext {
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9_\\-]+");
    private static final Pattern ALL_UNDERSCORE_PATTERN = Pattern.compile("^_+$");

    private final String tenantId;
    private final Map<String, String> extensions;

    /**
     * Whether a usable tenant identifier is bound to this context.
     *
     * @return true if a non-empty tenant id is present
     * @since 0.1.7
     */
    public boolean isTenantAware() {
        return tenantId != null && !tenantId.isEmpty();
    }

    /**
     * Return the sanitized tenant id, or null if no usable id remains after sanitization.
     *
     * @return the sanitized tenant id, or null
     * @since 0.1.7
     */
    public String safeTenantId() {
        if (tenantId == null) {
            return null;
        }
        String sanitized = SANITIZE_PATTERN.matcher(tenantId).replaceAll("_");
        if (sanitized.isEmpty() || ALL_UNDERSCORE_PATTERN.matcher(sanitized).matches()) {
            return null;
        }
        return sanitized;
    }
}
