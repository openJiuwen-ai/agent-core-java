package com.openjiuwen.core.sysop.cwd;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class CwdContextTenantTest {

    @BeforeEach
    void reset() {
        CwdContext.reset();
    }

    @AfterEach
    void cleanup() {
        CwdContext.reset();
    }

    @Test
    void testSetAndGetTenantRoot() {
        CwdContext.setTenantRoot("/data/tenants/abc123");
        assertThat(CwdContext.getTenantRoot()).isEqualTo("/data/tenants/abc123");
    }

    @Test
    void testIsWithinTenantRoot_withinBoundary() {
        CwdContext.setTenantRoot("/data/tenants/abc123");
        assertThat(CwdContext.isWithinTenantRoot(Path.of("/data/tenants/abc123/workspace/file.txt"))).isTrue();
    }

    @Test
    void testIsWithinTenantRoot_outsideBoundary() {
        CwdContext.setTenantRoot("/data/tenants/abc123");
        assertThat(CwdContext.isWithinTenantRoot(Path.of("/data/tenants/other_tenant/workspace/secret.txt"))).isFalse();
    }

    @Test
    void testIsWithinTenantRoot_nullTenantRoot_allowsAll() {
        assertThat(CwdContext.getTenantRoot()).isNull();
        assertThat(CwdContext.isWithinTenantRoot(Path.of("/any/path"))).isTrue();
    }
}
