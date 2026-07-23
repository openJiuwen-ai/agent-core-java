/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import static org.assertj.core.api.Assertions.assertThat;

class TenantCheckpointerIsolationTest {

    @BeforeEach
    void clearContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testBuildKeyWithTenant_withTenantId() {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        String key = Checkpointer.buildKeyWithTenant(ctx, "session-1", "agent", "react", "state");
        assertThat(key).isEqualTo("abc123:session-1:agent:react:state");
    }

    @Test
    void testBuildKeyWithTenant_noTenant_backwardCompat() {
        TenantContext ctx = TenantContext.builder().tenantId(null).build();
        String key = Checkpointer.buildKeyWithTenant(ctx, "session-1", "agent", "react", "state");
        assertThat(key).isEqualTo("session-1:agent:react:state");
    }

    @Test
    void testTenantAwareSessionId_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        assertThat(cp.tenantAwareSessionId("session-1")).isEqualTo("abc123:session-1");
    }

    @Test
    void testTenantAwareSessionId_noTenant_backwardCompat() {
        TenantContextHolder.clearCurrentTenant();
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        assertThat(cp.tenantAwareSessionId("session-1")).isEqualTo("session-1");
    }

    @Test
    void testInMemoryCheckpointer_isolationBetweenTenants() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        String keyA = cp.tenantAwareSessionId("session-1");
        assertThat(keyA).isEqualTo("tenant_a:session-1");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_b").build());
        String keyB = cp.tenantAwareSessionId("session-1");
        assertThat(keyB).isEqualTo("tenant_b:session-1");

        assertThat(keyA).isNotEqualTo(keyB);
    }
}
