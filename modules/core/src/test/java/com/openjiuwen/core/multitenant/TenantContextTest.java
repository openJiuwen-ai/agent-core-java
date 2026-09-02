package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @Test
    void testIsTenantAware_withValidId() {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        assertThat(ctx.isTenantAware()).isTrue();
    }

    @Test
    void testIsTenantAware_withNullId() {
        TenantContext ctx = TenantContext.builder().tenantId(null).build();
        assertThat(ctx.isTenantAware()).isFalse();
    }

    @Test
    void testIsTenantAware_withEmptyId() {
        TenantContext ctx = TenantContext.builder().tenantId("").build();
        assertThat(ctx.isTenantAware()).isFalse();
    }

    @Test
    void testSafeTenantId_normalInput() {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        assertThat(ctx.safeTenantId()).isEqualTo("abc123");
    }

    @Test
    void testSafeTenantId_specialCharacters() {
        TenantContext ctx = TenantContext.builder().tenantId("abc../../123").build();
        assertThat(ctx.safeTenantId()).isEqualTo("abc_123");
    }

    @Test
    void testSafeTenantId_nullInput() {
        TenantContext ctx = TenantContext.builder().tenantId(null).build();
        assertThat(ctx.safeTenantId()).isNull();
    }

    @Test
    void testSafeTenantId_emptyAfterSanitize() {
        TenantContext ctx = TenantContext.builder().tenantId("!@#$%").build();
        assertThat(ctx.safeTenantId()).isNull();
    }

    @Test
    void testSafeTenantId_preservesHyphenAndUnderscore() {
        TenantContext ctx = TenantContext.builder().tenantId("dept_fin-03").build();
        assertThat(ctx.safeTenantId()).isEqualTo("dept_fin-03");
    }
}
