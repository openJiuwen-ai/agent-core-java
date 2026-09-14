/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import com.openjiuwen.core.multitenant.TenantContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies TenantContext is part of the {@link AgentSessionApi} contract
 * (organic merge of 9b1 onto develop's narrow interface).
 */
class AgentSessionApiTenantTest {

    @Test
    void testTenantContext_defaultIsNull_backwardCompat() {
        AgentSessionApi session = new AgentSession("s1", null, null);
        assertThat(session.getTenantContext()).isNull();
    }

    @Test
    void testTenantContext_withTenantContext_setsField() {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        AgentSessionApi session = new AgentSession("s1", null, null);
        AgentSessionApi result = session.withTenantContext(ctx);
        assertThat(result).isSameAs(session);
        assertThat(session.getTenantContext()).isEqualTo(ctx);
    }

    @Test
    void testTenantContext_withNull_backwardCompat() {
        AgentSessionApi session = new AgentSession("s1", null, null);
        session.withTenantContext(null);
        assertThat(session.getTenantContext()).isNull();
    }

    @Test
    void testAgentGroupSession_inheritsTenantContext() {
        TenantContext ctx = TenantContext.builder().tenantId("group_tenant").build();
        AgentGroupSession groupSession = new AgentGroupSession("gs1");
        assertThat(groupSession.getTenantContext()).isNull();
        AgentSessionApi result = groupSession.withTenantContext(ctx);
        assertThat(result).isSameAs(groupSession);
        assertThat(groupSession.getTenantContext()).isEqualTo(ctx);
    }

    @Test
    void testAgentGroupSession_withNullTenantContext_backwardCompat() {
        AgentGroupSession groupSession = new AgentGroupSession("gs2");
        groupSession.withTenantContext(null);
        assertThat(groupSession.getTenantContext()).isNull();
    }

    @Test
    void testCreateFactoryMethod_withTenantContext() {
        TenantContext ctx = TenantContext.builder().tenantId("factory_tenant").build();
        AgentSessionApi session = AgentSession.createAgentSession("factory-s1", null, null);
        session.withTenantContext(ctx);
        assertThat(session.getTenantContext()).isEqualTo(ctx);
        assertThat(session.getSessionId()).isEqualTo("factory-s1");
    }

    @Test
    void testInterfaceDefault_returnsNullWithoutOverride() {
        AgentSessionApi stub = new AgentSessionApi() {
            @Override
            public String getSessionId() {
                return "stub";
            }

            @Override
            public Object getState(String key) {
                return null;
            }

            @Override
            public void updateState(Map<String, Object> data) {
            }

            @Override
            public void writeStream(Object data) {
            }

            @Override
            public java.util.Iterator<Object> streamIterator() {
                return java.util.Collections.emptyIterator();
            }
        };
        assertThat(stub.getTenantContext()).isNull();
        assertThat(stub.withTenantContext(TenantContext.builder().tenantId("x").build())).isSameAs(stub);
        assertThat(stub.getTenantContext()).isNull();
    }
}
