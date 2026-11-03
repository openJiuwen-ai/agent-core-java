package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class TenantNamespaceFactoryTest {

    @TempDir
    Path baseDir;

    TenantContext tenantA;
    TenantContext noTenant;

    @BeforeEach
    void setup() {
        tenantA = TenantContext.builder().tenantId("abc123").build();
        noTenant = TenantContext.builder().tenantId(null).build();
    }

    @Test
    void testKVStoreNamespaceFactory_withTenant() {
        TenantNamespaceFactory factory = TenantNamespaceFactories.KV_STORE_DEFAULT;
        String result = factory.namespace(tenantA, "session-1:agent:state");
        assertThat(result).isEqualTo("abc123:session-1:agent:state");
    }

    @Test
    void testKVStoreNamespaceFactory_noTenant() {
        TenantNamespaceFactory factory = TenantNamespaceFactories.KV_STORE_DEFAULT;
        String result = factory.namespace(noTenant, "session-1:agent:state");
        assertThat(result).isEqualTo("session-1:agent:state");
    }

    @Test
    void testPathNamespaceFactory_withTenant() {
        TenantNamespaceFactory factory = TenantNamespaceFactories.PATH_DEFAULT;
        String result = factory.namespace(tenantA, "tenants");
        assertThat(result).isEqualTo("tenants/abc123");
    }

    @Test
    void testPathNamespaceFactory_noTenant() {
        TenantNamespaceFactory factory = TenantNamespaceFactories.PATH_DEFAULT;
        String result = factory.namespace(noTenant, "tenants");
        assertThat(result).isEqualTo("");
    }

    @Test
    void testCustomNamespaceFactory() {
        TenantNamespaceFactory custom = (ctx, rawKey) -> {
            if (ctx != null && ctx.isTenantAware()) {
                return "ns_" + ctx.safeTenantId() + "|" + rawKey;
            }
            return rawKey;
        };
        String result = custom.namespace(tenantA, "data");
        assertThat(result).isEqualTo("ns_abc123|data");
    }

    @Test
    void testWorkspaceResolverWithCustomNamespaceFactory() {
        TenantNamespaceFactory custom = (ctx, rawKey) -> {
            if (ctx != null && ctx.isTenantAware()) {
                return "custom_prefix/" + ctx.safeTenantId() + "/" + rawKey;
            }
            return rawKey;
        };
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString(), custom);
        Path result = resolver.resolveWorkspaceRoot(tenantA);
        assertThat(result.toString()).contains("custom_prefix").contains("abc123");
    }

    @Test
    void testWorkspaceResolverDefaultNamespaceFactory_backwardCompat() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        Path result = resolver.resolveWorkspaceRoot(tenantA);
        assertThat(result.toString()).contains("tenants").contains("abc123");
    }
}
