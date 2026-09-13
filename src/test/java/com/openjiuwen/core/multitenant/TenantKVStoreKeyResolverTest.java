package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantKVStoreKeyResolverTest {

    @BeforeEach
    void clearContext() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testResolveKey_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());
        String result = TenantKVStoreKeyResolver.resolveKey("UMD/user1/scope1/mem1");
        assertThat(result).isEqualTo("abc123:UMD/user1/scope1/mem1");
    }

    @Test
    void testResolveKey_noTenant_backwardCompat() {
        String result = TenantKVStoreKeyResolver.resolveKey("UMD/user1/scope1/mem1");
        assertThat(result).isEqualTo("UMD/user1/scope1/mem1");
    }

    @Test
    void testResolvePrefix_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());
        String result = TenantKVStoreKeyResolver.resolvePrefix("UMD/");
        assertThat(result).isEqualTo("abc123:UMD/");
    }

    @Test
    void testResolvePrefix_noTenant_backwardCompat() {
        String result = TenantKVStoreKeyResolver.resolvePrefix("UMD/");
        assertThat(result).isEqualTo("UMD/");
    }

    @Test
    void testResolveKey_specialTenantIdThrowsIllegalArgumentException() {
        assertThatThrownBy(() ->
            TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc../def").build())
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
