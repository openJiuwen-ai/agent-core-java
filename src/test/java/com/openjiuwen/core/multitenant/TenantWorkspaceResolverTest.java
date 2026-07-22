package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class TenantWorkspaceResolverTest {

    @TempDir
    Path baseDir;

    TenantWorkspaceResolver resolver;
    TenantContext tenantA;
    TenantContext tenantB;
    TenantContext noTenant;

    @BeforeEach
    void setup() {
        resolver = new TenantWorkspaceResolver(baseDir.toString());
        tenantA = TenantContext.builder().tenantId("abc123").build();
        tenantB = TenantContext.builder().tenantId("dept_fin_03").build();
        noTenant = TenantContext.builder().tenantId(null).build();
    }

    @Test
    void testResolveWorkspaceRoot_withTenant() {
        Path result = resolver.resolveWorkspaceRoot(tenantA);
        assertThat(result.toString()).contains("tenants").contains("abc123");
        assertThat(result).isAbsolute();
    }

    @Test
    void testResolveWorkspaceRoot_noTenant_backwardCompat() {
        Path result = resolver.resolveWorkspaceRoot(noTenant);
        assertThat(result).isEqualTo(baseDir);
    }

    @Test
    void testResolveSkillRoot_withTenant() {
        Path result = resolver.resolveSkillRoot(tenantA);
        assertThat(result.toString()).contains("tenants").contains("abc123").contains("skills");
    }

    @Test
    void testResolveSkillRoot_noTenant_returnsSkillsPath() {
        Path result = resolver.resolveSkillRoot(noTenant);
        assertThat(result).isNotNull();
        assertThat(result.toString()).contains("skills");
    }

    @Test
    void testResolveTempDir_withTenant() {
        Path result = resolver.resolveTempDir(tenantA);
        assertThat(result.toString()).contains("tenants").contains("abc123").contains("tmp");
    }

    @Test
    void testResolveTempDir_noTenant_defaultTmp() {
        Path result = resolver.resolveTempDir(noTenant);
        assertThat(result).isEqualTo(baseDir.resolve("tmp").toAbsolutePath().normalize());
    }

    @Test
    void testIsPathWithinTenant_withinBoundary() {
        Path tenantPath = resolver.resolveWorkspaceRoot(tenantA).resolve("some_file.txt");
        assertThat(resolver.isPathWithinTenant(tenantPath, tenantA)).isTrue();
    }

    @Test
    void testIsPathWithinTenant_outsideBoundary() {
        Path otherTenantPath = resolver.resolveWorkspaceRoot(tenantB).resolve("secret.txt");
        assertThat(resolver.isPathWithinTenant(otherTenantPath, tenantA)).isFalse();
    }

    @Test
    void testIsPathWithinTenant_noTenant_alwaysTrue() {
        assertThat(resolver.isPathWithinTenant(Path.of("/any/path"), noTenant)).isTrue();
    }

    @Test
    void testInitializeTenantSpace_createsDirectories() {
        Path root = resolver.initializeTenantSpace(tenantA);
        assertThat(root.resolve("skills")).isDirectory();
        assertThat(root.resolve("tmp")).isDirectory();
        assertThat(root.resolve("checkpoints")).isDirectory();
        assertThat(root.resolve("team_memory")).isDirectory();
        assertThat(root.resolve("todo")).isDirectory();
    }

    @Test
    void testInitializeTenantSpace_concurrentSafety() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Path>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> resolver.initializeTenantSpace(tenantA)));
        }

        Set<Path> paths = futures.stream()
            .map(f -> {
                try { return f.get(); }
                catch (Exception e) { throw new RuntimeException(e); }
            })
            .collect(Collectors.toSet());
        assertThat(paths).hasSize(1);
        executor.shutdown();
    }
}
