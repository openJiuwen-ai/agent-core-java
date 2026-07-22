package com.openjiuwen.harness.tools;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;

import java.io.IOException;
import java.util.List;

public interface TodoStorage {
    List<TodoItem> load(String sessionId) throws IOException;

    void save(String sessionId, List<TodoItem> todos) throws IOException;

    void delete(String sessionId) throws IOException;

    default List<TodoItem> load(String sessionId, TenantContext tenantContext) throws IOException {
        if (tenantContext != null && tenantContext.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(tenantContext);
            try {
                return load(sessionId);
            } finally {
                TenantContextHolder.clearCurrentTenant();
            }
        }
        return load(sessionId);
    }

    default void save(String sessionId, List<TodoItem> todos, TenantContext tenantContext) throws IOException {
        if (tenantContext != null && tenantContext.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(tenantContext);
            try {
                save(sessionId, todos);
            } finally {
                TenantContextHolder.clearCurrentTenant();
            }
        } else {
            save(sessionId, todos);
        }
    }

    default void delete(String sessionId, TenantContext tenantContext) throws IOException {
        if (tenantContext != null && tenantContext.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(tenantContext);
            try {
                delete(sessionId);
            } finally {
                TenantContextHolder.clearCurrentTenant();
            }
        } else {
            delete(sessionId);
        }
    }
}
