package com.openjiuwen.core.multitenant;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class TenantContext {
    private final String tenantId;
    private final Map<String, String> extensions;

    public boolean isTenantAware() {
        return tenantId != null && !tenantId.isEmpty();
    }

    public String safeTenantId() {
        if (tenantId == null) return null;
        String sanitized = tenantId.replaceAll("[^a-zA-Z0-9_\\-]+", "_");
        if (sanitized.isEmpty() || sanitized.matches("^_+$")) return null;
        return sanitized;
    }
}
