package com.openjiuwen.core.multitenant.workspace;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantNamespaceFactory;
import com.openjiuwen.core.multitenant.TenantNamespaceFactories;
import com.openjiuwen.core.multitenant.workspace.store.LocalWorkspaceStore;
import com.openjiuwen.core.multitenant.workspace.store.ObjectStorageWorkspaceStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TieredWorkspaceManagerTest {

    @TempDir
    Path baseDir;

    TieredWorkspaceManager manager;
    TieredWorkspaceManager managerWithRemote;
    TenantContext tenantA;
    TenantContext noTenant;

    @BeforeEach
    void setup() {
        LocalWorkspaceStore localStore = new LocalWorkspaceStore(baseDir.toString());
        ObjectStorageWorkspaceStore obsStore = new ObjectStorageWorkspaceStore("obs", "deepagent-workspace", "https://obs.example.com");
        manager = new TieredWorkspaceManager(localStore, List.of());
        managerWithRemote = new TieredWorkspaceManager(localStore, List.of(obsStore));
        tenantA = TenantContext.builder().tenantId("abc123").build();
        noTenant = TenantContext.builder().tenantId(null).build();
    }

    @Test
    void testResolveWorkspace_withTenant() {
        WorkspaceResolution result = manager.resolve(tenantA, WorkspaceType.WORKSPACE);
        assertThat(result.getLocalPath().toString()).contains("abc123");
        assertThat(result.getType()).isEqualTo(WorkspaceType.WORKSPACE);
    }

    @Test
    void testResolveSkills_withTenant() {
        WorkspaceResolution result = manager.resolve(tenantA, WorkspaceType.SKILLS);
        assertThat(result.getLocalPath().toString()).contains("abc123").contains("skills");
    }

    @Test
    void testResolveTmp_withTenant() {
        WorkspaceResolution result = manager.resolve(tenantA, WorkspaceType.TMP);
        assertThat(result.getLocalPath().toString()).contains("abc123").contains("tmp");
    }

    @Test
    void testResolveDefault_noTenant() {
        WorkspaceResolution result = manager.resolveDefault(WorkspaceType.WORKSPACE);
        assertThat(result.getLocalPath()).isEqualTo(baseDir.toAbsolutePath().normalize());
    }

    @Test
    void testInitializeTenantSpace_createsDirectories() {
        manager.initializeTenantSpace(tenantA);
        assertThat(Files.exists(baseDir.resolve("tenants").resolve("abc123").resolve("skills"))).isTrue();
    }

    @Test
    void testResolveWithRemoteStore_hasRemotePaths() {
        WorkspaceResolution result = managerWithRemote.resolve(tenantA, WorkspaceType.WORKSPACE);
        assertThat(result.hasRemoteStore("obs")).isTrue();
        assertThat(result.getRemotePath("obs")).contains("abc123");
    }

    @Test
    void testResolveDefaultWithRemoteStore_hasRemotePaths() {
        WorkspaceResolution result = managerWithRemote.resolveDefault(WorkspaceType.WORKSPACE);
        assertThat(result.hasRemoteStore("obs")).isTrue();
        assertThat(result.getRemotePath("obs")).isEqualTo("");
    }

    @Test
    void testCustomNamespaceFactory() {
        TenantNamespaceFactory custom = (ctx, rawKey) -> {
            if (ctx != null && ctx.isTenantAware()) {
                return "custom_" + ctx.safeTenantId() + "/" + rawKey;
            }
            return rawKey;
        };
        LocalWorkspaceStore localStore = new LocalWorkspaceStore(baseDir.toString());
        TieredWorkspaceManager customManager = new TieredWorkspaceManager(localStore, List.of(), custom);
        WorkspaceResolution result = customManager.resolve(tenantA, WorkspaceType.WORKSPACE);
        assertThat(result.getLocalPath().toString()).contains("custom_abc123");
    }

    @Test
    void testDefaultNamespaceFactory_backwardCompat() {
        assertThat(manager.getNamespaceFactory()).isEqualTo(TenantNamespaceFactories.PATH_DEFAULT);
    }

    @Test
    void testPrimaryStore_isLocal() {
        assertThat(manager.getPrimaryStore().tierName()).isEqualTo("local");
    }

    @Test
    void testSecondaryStores_containsObs() {
        assertThat(managerWithRemote.getSecondaryStores()).hasSize(1);
        assertThat(managerWithRemote.getSecondaryStores().get(0).tierName()).isEqualTo("obs");
    }
}
