package com.openjiuwen.core.multitenant;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.regex.Pattern;

@Data
@Builder
public class TenantContext {
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9_\\-]+");
    private static final Pattern ALL_UNDERSCORE_PATTERN = Pattern.compile("^_+$");

    private final String tenantId;
    private final Map<String, String> extensions;

    public boolean isTenantAware() {
        return tenantId != null && !tenantId.isEmpty();
    }

    public String safeTenantId() {
        if (tenantId == null) return null;
        String sanitized = SANITIZE_PATTERN.matcher(tenantId).replaceAll("_");
        if (sanitized.isEmpty() || ALL_UNDERSCORE_PATTERN.matcher(sanitized).matches()) return null;
        return sanitized;
    }
}
