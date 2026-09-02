package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextHolderTest {

    @BeforeEach
    void clearContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testSetAndGet() {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        TenantContextHolder.setCurrentTenant(ctx);
        assertThat(TenantContextHolder.getCurrentTenant()).isEqualTo(ctx);
    }

    @Test
    void testClear() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());
        TenantContextHolder.clearCurrentTenant();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    void testDefaultIsNull() {
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    void testInheritableThreadLocal_childThreadInherits() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();
        TenantContextHolder.setCurrentTenant(ctx);

        Thread child = new Thread(() -> {
            assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
            assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("abc123");
        });
        child.start();
        child.join();
    }

    @Test
    void testInheritableThreadLocal_childThreadIndependentClear() throws Exception {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("parent").build());

        Thread child = new Thread(() -> {
            TenantContextHolder.clearCurrentTenant();
        });
        child.start();
        child.join();

        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("parent");
    }

    @Test
    void testSetCurrentTenant_invalidFormat_throwsException() {
        assertThatThrownBy(() -> TenantContextHolder.setCurrentTenant(
            TenantContext.builder().tenantId("dept.01").build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("[a-zA-Z0-9_-]");

        assertThatThrownBy(() -> TenantContextHolder.setCurrentTenant(
            TenantContext.builder().tenantId("dept/01").build()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TenantContextHolder.setCurrentTenant(
            TenantContext.builder().tenantId("dept 01").build()))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> TenantContextHolder.setCurrentTenant(
            TenantContext.builder().tenantId("dept@01").build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetCurrentTenant_validFormat_succeeds() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("dept-01").build());
        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("dept-01");
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc_123").build());
        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("abc_123");
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("ABC-123_xyz").build());
        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("ABC-123_xyz");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testSetCurrentTenant_nullContext_succeeds() {
        TenantContextHolder.setCurrentTenant(null);
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    void testSetCurrentTenant_notTenantAware_succeeds() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId(null).build());
        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant().isTenantAware()).isFalse();
    }
}
