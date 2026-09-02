package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.session.AgentSessionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionApiTenantTest {

    @AfterEach
    void clearThreadLocal() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    @DisplayName("新建 AgentSessionApi 时 tenantContext 默认为 null")
    void testTenantContext_defaultNull() {
        AgentSessionApi session = new AgentSessionApi("test-session-default");
        assertThat(session.getTenantContext()).isNull();
    }

    @Test
    @DisplayName("withTenantContext(ctx) 后 getTenantContext() 返回该 ctx")
    void testWithTenantContext_setsField() {
        TenantContext ctx = TenantContext.builder().tenantId("tenant-42").build();
        AgentSessionApi session = new AgentSessionApi("test-session-set");

        session.withTenantContext(ctx);

        assertThat(session.getTenantContext()).isEqualTo(ctx);
        assertThat(session.getTenantContext().getTenantId()).isEqualTo("tenant-42");
    }

    @Test
    @DisplayName("withTenantContext 返回 this，支持链式调用")
    void testWithTenantContext_returnsSelf_forChaining() {
        TenantContext ctx = TenantContext.builder().tenantId("chain-tenant").build();
        AgentSessionApi session = new AgentSessionApi("test-session-chain");

        AgentSessionApi returned = session.withTenantContext(ctx);

        assertThat(returned).isSameAs(session);
    }

    @Test
    @DisplayName("withTenantContext(null) 后 tenantContext 变为 null")
    void testWithTenantContext_nullContext_clearsField() {
        TenantContext ctx = TenantContext.builder().tenantId("will-be-cleared").build();
        AgentSessionApi session = new AgentSessionApi("test-session-clear");
        session.withTenantContext(ctx);
        assertThat(session.getTenantContext()).isNotNull();

        session.withTenantContext(null);

        assertThat(session.getTenantContext()).isNull();
    }

    @Test
    @DisplayName("设置有效 ctx 后 getTenantContext().isTenantAware() 返回 true")
    void testWithTenantContext_isTenantAware_true() {
        TenantContext ctx = TenantContext.builder().tenantId("aware-tenant").build();
        AgentSessionApi session = new AgentSessionApi("test-session-aware");

        session.withTenantContext(ctx);

        assertThat(session.getTenantContext()).isNotNull();
        assertThat(session.getTenantContext().isTenantAware()).isTrue();
    }

    @Test
    @DisplayName("不设置 tenantContext 时所有行为与原一致")
    void testWithTenantContext_backwardCompat() {
        AgentSessionApi session = new AgentSessionApi("test-session-compat");

        assertThat(session.getTenantContext()).isNull();
        assertThat(session.getSessionId()).isEqualTo("test-session-compat");
        assertThat(session.getInner()).isNotNull();
    }
}
