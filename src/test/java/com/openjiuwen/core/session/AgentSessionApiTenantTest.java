package com.openjiuwen.core.session;

import com.openjiuwen.core.multitenant.TenantContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionApiTenantTest {
    @Test
    void testTenantContext_defaultIsNull_backwardCompat() {
        AgentSessionApi session = new AgentSessionApi("s1");
        assertThat(session.getTenantContext()).isNull();
    }

    @Test
    void testTenantContext_withTenantContext_setsField() {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        AgentSessionApi session = new AgentSessionApi("s1");
        AgentSessionApi result = session.withTenantContext(ctx);
        assertThat(result).isSameAs(session);
        assertThat(session.getTenantContext()).isEqualTo(ctx);
    }

    @Test
    void testTenantContext_withNull_backwardCompat() {
        AgentSessionApi session = new AgentSessionApi("s1");
        session.withTenantContext(null);
        assertThat(session.getTenantContext()).isNull();
    }

    @Test
    void testAgentGroupSessionApi_inheritsTenantContext() {
        TenantContext ctx = TenantContext.builder().tenantId("group_tenant").build();
        AgentGroupSessionApi groupSession = new AgentGroupSessionApi("gs1");
        assertThat(groupSession.getTenantContext()).isNull();
        AgentSessionApi result = groupSession.withTenantContext(ctx);
        assertThat(result).isSameAs(groupSession);
        assertThat(groupSession.getTenantContext()).isEqualTo(ctx);
    }

    @Test
    void testAgentGroupSessionApi_withNullTenantContext_backwardCompat() {
        AgentGroupSessionApi groupSession = new AgentGroupSessionApi("gs2");
        groupSession.withTenantContext(null);
        assertThat(groupSession.getTenantContext()).isNull();
    }

    @Test
    void testCreateFactoryMethod_withTenantContext() {
        TenantContext ctx = TenantContext.builder().tenantId("factory_tenant").build();
        AgentSessionApi session = AgentSessionApi.create("factory-s1", null, null);
        session.withTenantContext(ctx);
        assertThat(session.getTenantContext()).isEqualTo(ctx);
        assertThat(session.getSessionId()).isEqualTo("factory-s1");
    }
}
