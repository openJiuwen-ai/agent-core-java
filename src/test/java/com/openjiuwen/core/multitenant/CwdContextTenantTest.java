// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * Unit tests for {@link CwdContext} tenant root isolation behavior described
 * in design §10.1 (multi-tenant path containment). Verifies that the
 * {@code TENANT_ROOT} ThreadLocal is set/read correctly, that
 * {@code isWithinTenantRoot} enforces containment for paths inside/outside
 * the configured root, and that {@code reset()} clears the ThreadLocal so
 * containment is lifted.
 */
@DisplayName("CwdContext tenant root isolation")
class CwdContextTenantTest {
    @TempDir
    Path tenantRoot;

    @TempDir
    Path outsideRoot;

    @BeforeEach
    void setUp() {
        CwdContext.reset();
    }

    @AfterEach
    void tearDown() {
        CwdContext.reset();
    }

    @Test
    @DisplayName("setTenantRoot stores path and getTenantRoot returns it")
    void testSetTenantRoot_getTenantRoot() {
        String root = tenantRoot.toAbsolutePath().normalize().toString();

        CwdContext.setTenantRoot(root);

        assertThat(CwdContext.getTenantRoot()).isEqualTo(root);
    }

    @Test
    @DisplayName("isWithinTenantRoot returns true for paths inside tenant root")
    void testIsWithinTenantRoot_pathInside_returnsTrue() {
        String root = tenantRoot.toAbsolutePath().normalize().toString();
        CwdContext.setTenantRoot(root);

        Path inside = tenantRoot.resolve("subdir").toAbsolutePath().normalize();

        assertThat(CwdContext.isWithinTenantRoot(inside))
            .as("path inside tenant root should be considered within")
            .isTrue();
    }

    @Test
    @DisplayName("isWithinTenantRoot returns false for paths outside tenant root")
    void testIsWithinTenantRoot_pathOutside_returnsFalse() {
        String root = tenantRoot.toAbsolutePath().normalize().toString();
        CwdContext.setTenantRoot(root);

        Path outside = outsideRoot.toAbsolutePath().normalize();

        assertThat(CwdContext.isWithinTenantRoot(outside))
            .as("path outside tenant root should be rejected")
            .isFalse();
    }

    @Test
    @DisplayName("reset clears tenant root and lifts containment")
    void testReset_clearsTenantRoot() {
        String root = tenantRoot.toAbsolutePath().normalize().toString();
        CwdContext.setTenantRoot(root);

        CwdContext.reset();

        assertThat(CwdContext.getTenantRoot()).isNull();
        Path anywhere = outsideRoot.toAbsolutePath().normalize();
        assertThat(CwdContext.isWithinTenantRoot(anywhere))
            .as("no tenant root means no containment restriction")
            .isTrue();
    }
}
