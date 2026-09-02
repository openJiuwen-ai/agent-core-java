/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;

import java.io.IOException;
import java.util.List;

/**
 * TodoStorage.
 *
 * @since 0.1.7
 */
public interface TodoStorage {
    /**
     * load.
     *
     * @param sessionId sessionId
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    List<TodoItem> load(String sessionId) throws IOException;

    /**
     * save.
     *
     * @param sessionId sessionId
     * @param todos todos
     * @throws IOException IOException
     * @since 0.1.7
     */
    void save(String sessionId, List<TodoItem> todos) throws IOException;

    /**
     * delete.
     *
     * @param sessionId sessionId
     * @throws IOException IOException
     * @since 0.1.7
     */
    void delete(String sessionId) throws IOException;

    /**
     * load.
     *
     * @param sessionId sessionId
     * @param tenantContext tenantContext
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
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

    /**
     * save.
     *
     * @param sessionId sessionId
     * @param todos todos
     * @param tenantContext tenantContext
     * @throws IOException IOException
     * @since 0.1.7
     */
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

    /**
     * delete.
     *
     * @param sessionId sessionId
     * @param tenantContext tenantContext
     * @throws IOException IOException
     * @since 0.1.7
     */
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
