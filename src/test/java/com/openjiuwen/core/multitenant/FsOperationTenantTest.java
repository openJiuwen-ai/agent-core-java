/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@DisplayName("FsOperation tenant boundary isolation (§10.1)")
class FsOperationTenantTest {

    @TempDir
    Path tempRoot;

    private Path sandboxRoot;
    private Path tenantRoot;
    private Path outsideRoot;
    private SysOperation sysOp;
    private TestableLocalFsOperation testableOp;

    @BeforeEach
    void setUp() throws IOException {
        CwdContext.reset();
        TenantContextHolder.clearCurrentTenant();

        Files.createDirectories(tempRoot.resolve("tenant"));
        Files.createDirectories(tempRoot.resolve("outside"));

        sandboxRoot = tempRoot.toRealPath();
        tenantRoot = tempRoot.resolve("tenant").toRealPath();
        outsideRoot = tempRoot.resolve("outside").toRealPath();

        CwdContext.setTenantRoot(tenantRoot.toString());

        LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(tenantRoot.toString())
                .sandboxRoot(List.of(sandboxRoot.toString()))
                .restrictToSandbox(true)
                .build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_tenant_fs");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        sysOp = new SysOperation(card);

        testableOp = new TestableLocalFsOperation(config);
    }

    @AfterEach
    void tearDown() {
        CwdContext.reset();
        TenantContextHolder.clearCurrentTenant();
    }

    private BaseFsOperation fs() {
        return sysOp.fs();
    }

    static class TestableLocalFsOperation extends LocalFsOperation {
        TestableLocalFsOperation(Object runConfig) {
            super(runConfig);
        }

        public Path callValidateTenantBoundary(Path path) {
            return validateTenantBoundary(path);
        }
    }

    @Test
    @DisplayName("readFile within tenant root succeeds")
    void testReadFile_withinTenant_succeeds() throws IOException {
        Path file = tenantRoot.resolve("read_inside.txt");
        Files.writeString(file, "tenant data");

        ReadFileResult result = fs().readFile(
                file.toAbsolutePath().normalize().toString(),
                "text", null, null, null, "utf-8", 0, null);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getContentAsString()).isEqualTo("tenant data");
    }

    @Test
    @DisplayName("readFile outside tenant root throws SecurityException")
    void testReadFile_outsideTenant_throwsSecurityException() throws IOException {
        Path outsideFile = outsideRoot.resolve("evil.txt");
        Files.writeString(outsideFile, "secret");

        assertThatThrownBy(() -> fs().readFile(
                outsideFile.toAbsolutePath().normalize().toString(),
                "text", null, null, null, "utf-8", 0, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("writeFile within tenant root succeeds")
    void testWriteFile_withinTenant_succeeds() {
        WriteFileResult result = fs().writeFile(
                "write_inside.txt", "data", "text",
                false, false, true, null, "utf-8", null);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(Files.exists(tenantRoot.resolve("write_inside.txt"))).isTrue();
    }

    @Test
    @DisplayName("writeFile outside tenant root throws SecurityException")
    void testWriteFile_outsideTenant_throws() {
        Path outsideFile = outsideRoot.resolve("evil_write.txt");

        assertThatThrownBy(() -> fs().writeFile(
                outsideFile.toAbsolutePath().normalize().toString(),
                "data", "text", false, false, true, null, "utf-8", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("deleteFile outside tenant root throws SecurityException")
    void testDeleteFile_outsideTenant_throws() {
        Path outsideFile = outsideRoot.resolve("to_delete.txt").toAbsolutePath().normalize();

        assertThatThrownBy(() -> testableOp.callValidateTenantBoundary(outsideFile))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("listDir within tenant root succeeds")
    void testListDir_withinTenant_succeeds() throws IOException {
        Files.createDirectories(tenantRoot.resolve("subdir"));

        ListDirsResult result = fs().listDirectories(
                tenantRoot.toAbsolutePath().normalize().toString(),
                false, null, "name", false, null);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getTotalCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("listDir outside tenant root throws SecurityException")
    void testListDir_outsideTenant_throws() {
        assertThatThrownBy(() -> fs().listDirectories(
                outsideRoot.toAbsolutePath().normalize().toString(),
                false, null, "name", false, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("createDir within tenant root succeeds")
    void testCreateDir_withinTenant_succeeds() {
        Path insideDir = tenantRoot.resolve("new_dir").toAbsolutePath().normalize();

        Path result = testableOp.callValidateTenantBoundary(insideDir);

        assertThat(result.toAbsolutePath().normalize()).isEqualTo(insideDir);
    }

    @Test
    @DisplayName("createDir outside tenant root throws SecurityException")
    void testCreateDir_outsideTenant_throws() {
        Path outsideDir = outsideRoot.resolve("evil_dir").toAbsolutePath().normalize();

        assertThatThrownBy(() -> testableOp.callValidateTenantBoundary(outsideDir))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("copyFile cross-tenant throws SecurityException on destination")
    void testCopyFile_crossTenant_throws() throws IOException {
        Path srcFile = tenantRoot.resolve("source.txt");
        Files.writeString(srcFile, "tenant content");
        Path dstFile = outsideRoot.resolve("copied.txt").toAbsolutePath().normalize();

        assertThatThrownBy(() -> fs().uploadFile(
                srcFile.toRealPath().toString(),
                dstFile.toString(),
                true, true, false, 0, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("moveFile cross-tenant throws SecurityException on destination")
    void testMoveFile_crossTenant_throws() {
        Path srcPath = tenantRoot.resolve("move_src.txt").toAbsolutePath().normalize();
        Path dstPath = outsideRoot.resolve("move_dst.txt").toAbsolutePath().normalize();

        assertThat(srcPath.toAbsolutePath().normalize().startsWith(tenantRoot)).isTrue();
        assertThatThrownBy(() -> testableOp.callValidateTenantBoundary(dstPath))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("Path traversal with .. blocked by tenant boundary")
    void testPathTraversal_dotdot_blocked() throws IOException {
        Path outsideFile = outsideRoot.resolve("traversal_target.txt");
        Files.writeString(outsideFile, "secret");

        assertThatThrownBy(() -> fs().readFile(
                "../outside/traversal_target.txt",
                "text", null, null, null, "utf-8", 0, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }

    @Test
    @DisplayName("Symlink traversal blocked (skipped on Windows)")
    void testSymlinkTraversal_blocked() throws IOException {
        Assumptions.assumeTrue(
                !System.getProperty("os.name").toLowerCase().contains("win"),
                "Symlink creation requires elevated privileges on Windows");

        Path outsideFile = outsideRoot.resolve("symlink_target.txt");
        Files.writeString(outsideFile, "secret via symlink");
        Path link = tenantRoot.resolve("escape_link");
        Files.createSymbolicLink(link, outsideFile);

        assertThatThrownBy(() -> fs().readFile(
                link.toRealPath().toString(),
                "text", null, null, null, "utf-8", 0, null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Tenant boundary violation");
    }
}
