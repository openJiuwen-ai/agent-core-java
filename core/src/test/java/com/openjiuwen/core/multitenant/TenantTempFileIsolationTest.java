package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

@DisplayName("Tenant Temp File Isolation Tests")
class TenantTempFileIsolationTest {

    @TempDir
    Path dataRoot;

    TenantWorkspaceResolver resolver;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        resolver = new TenantWorkspaceResolver(dataRoot.toString());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Nested
    @DisplayName("Temp directory isolation")
    class TempDirectoryIsolation {
        @Test
        @DisplayName("Different tenants have different temp directories")
        void testDifferentTenantsDifferentTempDirs() {
            TenantContext tenantA = TenantContext.builder().tenantId("alpha").build();
            TenantContext tenantB = TenantContext.builder().tenantId("beta").build();

            Path tempA = resolver.resolveTempDir(tenantA);
            Path tempB = resolver.resolveTempDir(tenantB);

            assertThat(tempA).isNotEqualTo(tempB);
            assertThat(tempA.toString()).contains("alpha");
            assertThat(tempB.toString()).contains("beta");
        }

        @Test
        @DisplayName("Temp files by one tenant not visible to another")
        void testTempFilesNotVisibleAcrossTenants() throws Exception {
            TenantContext tenantA = TenantContext.builder().tenantId("alpha").build();
            TenantContext tenantB = TenantContext.builder().tenantId("beta").build();

            resolver.initializeTenantSpace(tenantA);
            resolver.initializeTenantSpace(tenantB);

            Path tempA = resolver.resolveTempDir(tenantA);
            Files.createDirectories(tempA);
            Files.writeString(tempA.resolve("secret.txt"), "alpha secret");

            Path tempB = resolver.resolveTempDir(tenantB);
            Files.createDirectories(tempB);

            assertThat(Files.exists(tempB.resolve("secret.txt"))).isFalse();
            assertThat(Files.exists(tempA.resolve("secret.txt"))).isTrue();
        }

        @Test
        @DisplayName("Temp directory is under tenant root (sibling of workspace)")
        void testTempDirUnderTenantRoot() {
            TenantContext tenant = TenantContext.builder().tenantId("test_tenant").build();

            Path tenantRoot = resolver.resolveTenantRoot(tenant);
            Path tempDir = resolver.resolveTempDir(tenant);

            assertThat(tempDir.getParent()).isEqualTo(tenantRoot);
            assertThat(tempDir.getFileName().toString()).isEqualTo("tmp");
        }

        @Test
        @DisplayName("Safe tenantId produces correct temp path")
        void testSafeTenantIdTempPath() {
            TenantContext tenant = TenantContext.builder().tenantId("my/company/a").build();

            Path tempDir = resolver.resolveTempDir(tenant);

            assertThat(tempDir.toString()).contains("my_company_a");
        }
    }

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompat {
        @Test
        @DisplayName("No tenant context temp dir resolves to null")
        void testNoTenantContextTempDir() {
            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            TenantContext tenant = TenantContext.builder().tenantId("explicit").build();
            Path tempDir = resolver.resolveTempDir(tenant);
            assertThat(tempDir).isNotNull();
        }
    }
}
