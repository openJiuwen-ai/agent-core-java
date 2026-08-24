package com.openjiuwen.core.session.store;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAwareFileStoreTest {

    @TempDir
    Path tempDir;

    TenantWorkspaceResolver resolver;
    TenantContext tenantA;
    TenantContext tenantB;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        resolver = new TenantWorkspaceResolver(tempDir.toString());
        tenantA = TenantContext.builder().tenantId("tenantA").build();
        tenantB = TenantContext.builder().tenantId("tenantB").build();
        resolver.initializeTenantSpace(tenantA);
        resolver.initializeTenantSpace(tenantB);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testWrite_noTenant_defaultPath() {
        Path storeFile = tempDir.resolve("session_store.json");
        FileStore store = new FileStore(storeFile);
        store.write(Map.of("key1", "value1"));
        assertThat(Files.exists(storeFile)).isTrue();
    }

    @Test
    void testWrite_withTenant_tenantPath() {
        FileStore store = new FileStore(tempDir.resolve("session_store.json"), resolver);
        TenantContextHolder.setCurrentTenant(tenantA);
        store.write(Map.of("key1", "value1"));
        TenantContextHolder.clearCurrentTenant();

        Path tenantFile = tempDir.resolve("tenants").resolve("tenantA").resolve("session_store.json");
        assertThat(Files.exists(tenantFile)).isTrue();
    }

    @Test
    void testWrite_withTenant_pathContainsTenantsAndTid() {
        FileStore store = new FileStore(tempDir.resolve("session_store.json"), resolver);
        TenantContextHolder.setCurrentTenant(tenantA);
        store.write(Map.of("path_key", "path_value"));
        TenantContextHolder.clearCurrentTenant();

        Path expectedPath = resolver.resolveWorkspaceRoot(tenantA).resolve("session_store.json");
        assertThat(Files.exists(expectedPath)).isTrue();
        assertThat(expectedPath.toString()).contains("tenants").contains("tenantA");
    }

    @Test
    void testWrite_withTenant_defaultPathNotWritten() {
        Path defaultStoreFile = tempDir.resolve("session_store.json");
        FileStore store = new FileStore(defaultStoreFile, resolver);
        TenantContextHolder.setCurrentTenant(tenantA);
        store.write(Map.of("tenant_only", "alpha_data"));
        TenantContextHolder.clearCurrentTenant();

        assertThat(Files.exists(defaultStoreFile)).isFalse();

        Path tenantFile = tempDir.resolve("tenants").resolve("tenantA").resolve("session_store.json");
        assertThat(Files.exists(tenantFile)).isTrue();
    }

    @Test
    void testRead_noTenant_defaultPath() {
        Path storeFile = tempDir.resolve("session_store.json");
        FileStore store = new FileStore(storeFile);
        store.write(Map.of("key1", "value1"));

        Object result = store.read("key1");
        assertThat(result).isEqualTo("value1");
    }

    @Test
    void testRead_withTenant_tenantPath() {
        FileStore store = new FileStore(tempDir.resolve("session_store.json"), resolver);
        TenantContextHolder.setCurrentTenant(tenantA);
        store.write(Map.of("key1", "tenantA_value"));
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantA);
        Object result = store.read("key1");
        assertThat(result).isEqualTo("tenantA_value");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testRead_withTenant_cannotSeeDefaultData() {
        Path defaultStoreFile = tempDir.resolve("session_store.json");
        FileStore defaultStore = new FileStore(defaultStoreFile);
        defaultStore.write(Map.of("shared_key", "shared_value"));

        FileStore tenantStore = new FileStore(defaultStoreFile, resolver);
        TenantContextHolder.setCurrentTenant(tenantA);
        Object result = tenantStore.read("shared_key");
        assertThat(result).isNull();
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testTenantIsolation_sessionStore() {
        FileStore storeA = new FileStore(tempDir.resolve("session_store.json"), resolver);
        FileStore storeB = new FileStore(tempDir.resolve("session_store.json"), resolver);

        TenantContextHolder.setCurrentTenant(tenantA);
        storeA.write(Map.of("secret", "alpha_data"));
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantB);
        storeB.write(Map.of("secret", "beta_data"));
        TenantContextHolder.clearCurrentTenant();

        Path tenantAFile = tempDir.resolve("tenants").resolve("tenantA").resolve("session_store.json");
        Path tenantBFile = tempDir.resolve("tenants").resolve("tenantB").resolve("session_store.json");
        assertThat(Files.exists(tenantAFile)).isTrue();
        assertThat(Files.exists(tenantBFile)).isTrue();
        assertThat(tenantAFile).isNotEqualTo(tenantBFile);

        TenantContextHolder.setCurrentTenant(tenantB);
        Object result = storeB.read("secret");
        assertThat(result).isEqualTo("beta_data");
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantA);
        Object resultA = storeA.read("secret");
        assertThat(resultA).isEqualTo("alpha_data");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testTenantIsolation_crossAccessPrevented() {
        FileStore store = new FileStore(tempDir.resolve("session_store.json"), resolver);

        TenantContextHolder.setCurrentTenant(tenantA);
        store.write(Map.of("alpha_secret", "alpha_only_data"));
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantB);
        Object result = store.read("alpha_secret");
        assertThat(result).isNull();
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testRead_emptyTenant_returnsDefault() {
        Path storeFile = tempDir.resolve("session_store.json");
        FileStore store = new FileStore(storeFile, resolver);
        store.write(Map.of("shared", "default_value"));

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("").build());
        Object result = store.read("shared");
        assertThat(result).isEqualTo("default_value");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testNoResolver_alwaysUsesDefaultPath() {
        Path storeFile = tempDir.resolve("no_resolver_store.json");
        FileStore store = new FileStore(storeFile);
        store.write(Map.of("no_resolver_key", "no_resolver_value"));

        assertThat(Files.exists(storeFile)).isTrue();
        assertThat(store.read("no_resolver_key")).isEqualTo("no_resolver_value");

        TenantContextHolder.setCurrentTenant(tenantA);
        assertThat(store.read("no_resolver_key")).isEqualTo("no_resolver_value");
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testWriteMerge_semanticsPreservedAcrossTenants() {
        FileStore store = new FileStore(tempDir.resolve("session_store.json"), resolver);

        TenantContextHolder.setCurrentTenant(tenantA);
        store.write(Map.of("key1", "value1"));
        store.write(Map.of("key2", "value2"));
        assertThat(store.read("key1")).isEqualTo("value1");
        assertThat(store.read("key2")).isEqualTo("value2");
        TenantContextHolder.clearCurrentTenant();

        TenantContextHolder.setCurrentTenant(tenantB);
        assertThat(store.read("key1")).isNull();
        assertThat(store.read("key2")).isNull();
        store.write(Map.of("key1", "beta_value1"));
        assertThat(store.read("key1")).isEqualTo("beta_value1");
        TenantContextHolder.clearCurrentTenant();
    }
}
