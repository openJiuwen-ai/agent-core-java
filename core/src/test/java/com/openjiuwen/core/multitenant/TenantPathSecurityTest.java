package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystemException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantPathSecurityTest {

    @TempDir
    Path tenantRoot;

    @TempDir
    Path otherTenantRoot;

    String tenantRootStr;

    @BeforeEach
    void setup() {
        tenantRootStr = tenantRoot.toString();
    }

    @Test
    void testResolveSafePath_normalPath() {
        Path safePath = tenantRoot.resolve("some_file.txt");
        Path result = TenantPathSecurity.resolveSafePath(safePath.toString(), tenantRootStr);
        assertThat(result).isAbsolute();
        assertThat(result.startsWith(Path.of(tenantRootStr).toAbsolutePath().normalize())).isTrue();
    }

    @Test
    void testResolveSafePath_pathTraversalBlocked() {
        assertThatThrownBy(() ->
            TenantPathSecurity.resolveSafePath("../../other_tenant/secret.txt", tenantRootStr)
        ).isInstanceOf(SecurityException.class)
          .hasMessageContaining("Path traversal blocked");
    }

    @Test
    void testResolveSafePath_absolutePathOutsideTenantBlocked() {
        assertThatThrownBy(() ->
            TenantPathSecurity.resolveSafePath("/etc/passwd", tenantRootStr)
        ).isInstanceOf(SecurityException.class);
    }

    @Test
    void testResolveSafePath_nullTenantRoot_allowsAll() {
        Path result = TenantPathSecurity.resolveSafePath("/etc/passwd", null);
        assertThat(result).isAbsolute();
    }

    @Test
    void testResolveSafePath_symlinkTraversalBlocked() throws Exception {
        Assumptions.assumeTrue(canCreateSymbolicLinks(),
            "Skipping: symbolic link creation requires elevated privileges on this system");

        Path secretFile = otherTenantRoot.resolve("secret.txt");
        Files.writeString(secretFile, "confidential data");
        Path link = tenantRoot.resolve("stealth_link");
        Files.createSymbolicLink(link, secretFile);

        assertThatThrownBy(() ->
            TenantPathSecurity.resolveSafePath(link.toString(), tenantRootStr)
        ).isInstanceOf(SecurityException.class)
          .hasMessageContaining("Symlink traversal blocked");
    }

    @Test
    void testResolveSafePath_symlinkWithinTenantAllowed() throws Exception {
        Assumptions.assumeTrue(canCreateSymbolicLinks(),
            "Skipping: symbolic link creation requires elevated privileges on this system");

        Path target = tenantRoot.resolve("target.txt");
        Files.writeString(target, "safe data");
        Path link = tenantRoot.resolve("safe_link");
        Files.createSymbolicLink(link, target);

        Path result = TenantPathSecurity.resolveSafePath(link.toString(), tenantRootStr);
        assertThat(result).isAbsolute();
    }

    private boolean canCreateSymbolicLinks() {
        try {
            Path tempLink = tenantRoot.resolve("symlink_test_probe");
            Path tempTarget = tenantRoot.resolve("symlink_test_target");
            Files.writeString(tempTarget, "probe");
            Files.createSymbolicLink(tempLink, tempTarget);
            Files.delete(tempLink);
            Files.delete(tempTarget);
            return true;
        } catch (FileSystemException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
