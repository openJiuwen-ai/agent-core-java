package com.openjiuwen.core.multitenant;

@FunctionalInterface
public interface TenantNamespaceFactory {
    String namespace(TenantContext ctx, String rawKey);
}
