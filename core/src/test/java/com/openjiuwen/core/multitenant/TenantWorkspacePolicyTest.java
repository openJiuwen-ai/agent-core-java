package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantWorkspacePolicyTest {

    @TempDir
    Path baseDir;

    @TempDir
    Path mountDir;

    @Test
    void testResolveTenantRoot_noTenant_returnsBase() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder().tenantId(null).build();

        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(baseDir.toAbsolutePath().normalize());
        assertThat(policy.resolveNamespace(ctx)).isEmpty();
    }

    @Test
    void testExplicitWorkspaceRoot_winsOverLocatorAndDefaultLayout() {
        Path explicit = baseDir.resolve("branch_01").resolve("dept_07").resolve("u_1");
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .locator(ctx -> Optional.of(baseDir.resolve("from_locator")))
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("u_1")
            .workspaceRoot(explicit.toString())
            .build();

        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(explicit);

        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(policy);
        resolver.initializeTenantSpace(ctx);

        assertThat(explicit.resolve("skills")).isDirectory();
        assertThat(explicit.resolve("tmp")).isDirectory();
        assertThat(explicit.resolve("checkpoints")).isDirectory();
        assertThat(explicit.resolve("team_memory")).isDirectory();
        assertThat(explicit.resolve("todo")).isDirectory();
        assertThat(explicit.resolve(".overlay")).isDirectory();
    }

    @Test
    void testLocatorOnly_resolvesLocatedPath() {
        Path located = baseDir.resolve("located_tenant");
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .locator(ctx -> Optional.of(located))
            .build();
        TenantContext ctx = TenantContext.builder().tenantId("u_2").build();

        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(located);
    }

    @Test
    void testLocatorEmpty_fallsBackToDefaultLayout() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .locator(ctx -> Optional.empty())
            .build();
        TenantContext ctx = TenantContext.builder().tenantId("u_3").build();

        Path expected = baseDir.resolve("tenants").resolve("u_3").toAbsolutePath().normalize();
        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(expected);
    }

    @Test
    void testExplicitPathOutsideBase_withoutAllowList_isRejected() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("u_4")
            .workspaceRoot(mountDir.resolve("u_4").toString())
            .build();

        assertThatThrownBy(() -> policy.resolveTenantRoot(ctx)).isInstanceOf(SecurityException.class);
    }

    @Test
    void testAllowList_permitsMultipleMountPoints() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .allowedTenantRoots(List.of(baseDir.toString(), mountDir.toString()))
            .build();
        Path explicit = mountDir.resolve("tenant_a").toAbsolutePath().normalize();
        TenantContext ctx = TenantContext.builder()
            .tenantId("tenant_a")
            .workspaceRoot(explicit.toString())
            .build();

        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(explicit);
    }

    @Test
    void testAllowList_keepsDefaultLayoutWorking() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .allowedTenantRoots(List.of(mountDir.toString()))
            .build();
        TenantContext ctx = TenantContext.builder().tenantId("u_default").build();

        Path expected = baseDir.resolve("tenants").resolve("u_default").toAbsolutePath().normalize();
        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(expected);
    }

    @Test
    void testResolveNamespace_shardedLayout_usesForwardSlashes() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("u_1")
            .workspaceRoot(baseDir.resolve("branch_01").resolve("dept_07").resolve("u_1").toString())
            .build();

        assertThat(policy.resolveNamespace(ctx)).isEqualTo("branch_01/dept_07/u_1");
    }

    @Test
    void testResolveNamespace_defaultLayout_returnsTenantNamespace() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder().tenantId("u_5").build();

        assertThat(policy.resolveNamespace(ctx)).isEqualTo("tenants/u_5");
    }

    @Test
    void testResolveNamespace_outsideBase_isRejected() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .allowedTenantRoots(List.of(baseDir.toString(), mountDir.toString()))
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("tenant_b")
            .workspaceRoot(mountDir.resolve("tenant_b").toString())
            .build();

        assertThatThrownBy(() -> policy.resolveNamespace(ctx)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTryResolveNamespace_outsideBase_isEmpty() {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .allowedTenantRoots(List.of(mountDir.toString()))
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("tenant_b")
            .workspaceRoot(mountDir.resolve("tenant_b").toString())
            .build();

        assertThat(policy.tryResolveNamespace(ctx)).isEmpty();
    }

    @Test
    void testLegacyResolverConstructor_keepsDefaultLayout() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();

        Path expected = baseDir.resolve("tenants").resolve("abc123").toAbsolutePath().normalize();
        assertThat(resolver.resolveWorkspaceRoot(ctx)).isEqualTo(expected);
        assertThat(resolver.resolveTenantRoot(ctx)).isEqualTo(expected);
    }

    @Test
    void testBaseWorkspaceIsSymlink_defaultLayoutStillResolves() throws Exception {
        Assumptions.assumeTrue(directoryLinksSupported(),
            "Skipping: symbolic link creation requires elevated privileges on this system");
        Path realBase = Files.createDirectory(baseDir.resolve("real_base"));
        Path baseLink = baseDir.resolve("base_link");
        Files.createSymbolicLink(baseLink, realBase);

        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseLink.toString())
            .build();
        TenantContext ctx = TenantContext.builder().tenantId("u_6").build();

        Path expected = baseLink.resolve("tenants").resolve("u_6").toAbsolutePath().normalize();
        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(expected);

        new TenantWorkspaceResolver(policy).initializeTenantSpace(ctx);
        assertThat(realBase.resolve("tenants").resolve("u_6").resolve("tmp")).isDirectory();
    }

    @Test
    void testSymlinkEscape_withExistingTarget_isRejected() throws Exception {
        Assumptions.assumeTrue(directoryLinksSupported(),
            "Skipping: symbolic link creation requires elevated privileges on this system");
        Path link = baseDir.resolve("escape_link");
        Files.createSymbolicLink(link, mountDir);
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("u_7")
            .workspaceRoot(link.toString())
            .build();

        assertThatThrownBy(() -> policy.resolveTenantRoot(ctx)).isInstanceOf(SecurityException.class);
    }

    @Test
    void testSymlinkEscape_withNonExistingChild_isRejected() throws Exception {
        Assumptions.assumeTrue(directoryLinksSupported(),
            "Skipping: symbolic link creation requires elevated privileges on this system");
        Path link = baseDir.resolve("escape_link");
        Files.createSymbolicLink(link, mountDir);
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("u_8")
            .workspaceRoot(link.resolve("new_tenant").toString())
            .build();

        assertThatThrownBy(() -> policy.resolveTenantRoot(ctx)).isInstanceOf(SecurityException.class);
    }

    @Test
    void testSymlinkInsideBase_isAllowed() throws Exception {
        Assumptions.assumeTrue(directoryLinksSupported(),
            "Skipping: symbolic link creation requires elevated privileges on this system");
        Path target = Files.createDirectory(baseDir.resolve("branch_link_target"));
        Path link = baseDir.resolve("branch_link");
        Files.createSymbolicLink(link, target);
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
            .baseWorkspacePath(baseDir.toString())
            .build();
        TenantContext ctx = TenantContext.builder()
            .tenantId("u_9")
            .workspaceRoot(link.resolve("u_9").toString())
            .build();

        Path expected = link.resolve("u_9").toAbsolutePath().normalize();
        assertThat(policy.resolveTenantRoot(ctx)).isEqualTo(expected);
    }

    private boolean directoryLinksSupported() {
        Path target = baseDir.resolve("link_probe_target");
        Path link = baseDir.resolve("link_probe");
        try {
            Files.createDirectory(target);
            Files.createSymbolicLink(link, target);
            return Files.isDirectory(link);
        } catch (Exception e) {
            return false;
        } finally {
            deleteQuietly(link);
            deleteQuietly(target);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            return;
        }
    }
}
