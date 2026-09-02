/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FsOperationTenantTest {

    @TempDir
    Path parentDir;

    Path tenantDir;
    Path otherDir;

    @BeforeEach
    void setup() throws Exception {
        CwdContext.reset();
        tenantDir = parentDir.resolve("tenant");
        otherDir = parentDir.resolve("other");
        Files.createDirectories(tenantDir);
        Files.createDirectories(otherDir);
    }

    @AfterEach
    void teardown() {
        CwdContext.reset();
    }

    private LocalFsOperation createFsOp(Path workDir, Path sandboxRoot) {
        return new LocalFsOperation(LocalWorkConfig.builder()
                .workDir(workDir.toString())
                .restrictToSandbox(true)
                .sandboxRoot(List.of(sandboxRoot.toString()))
                .build());
    }

    @Test
    void testValidateTenantBoundary_outsidePathBlocked() {
        CwdContext.setTenantRoot(tenantDir.toString());

        LocalFsOperation fsOp = new LocalFsOperation(LocalWorkConfig.builder().build());

        assertThatThrownBy(() ->
                fsOp.validateTenantBoundary(otherDir.toAbsolutePath())
        ).isInstanceOf(SecurityException.class);
    }

    @Test
    void testValidateTenantBoundary_withinPathAllowed() {
        CwdContext.setTenantRoot(tenantDir.toString());

        LocalFsOperation fsOp = new LocalFsOperation(LocalWorkConfig.builder().build());

        Path result = fsOp.validateTenantBoundary(tenantDir.toAbsolutePath());
        assertThat(result).isNotNull();
    }

    @Test
    void testValidateTenantBoundary_noTenantRootAllowsAll() {
        LocalFsOperation fsOp = new LocalFsOperation(LocalWorkConfig.builder().build());

        Path arbitraryPath = Path.of("/arbitrary/path").toAbsolutePath();
        Path result = fsOp.validateTenantBoundary(arbitraryPath);
        assertThat(result).isNotNull();
    }

    @Test
    void testReadFileWithinTenantAllowed() throws Exception {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        Path testFile = tenantDir.resolve("safe.txt");
        Files.writeString(testFile, "safe content");
        var result = fsOp.readFile(testFile.toString(), null, null, null, null, null, 0, null);
        assertThat(result).isNotNull();
    }

    @Test
    void testReadFileNoTenantRootAllPathsAllowed() throws Exception {
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        Path testFile = tenantDir.resolve("test.txt");
        Files.writeString(testFile, "hello");
        var result = fsOp.readFile(testFile.toString(), null, null, null, null, null, 0, null);
        assertThat(result).isNotNull();
    }

    @Test
    void testWriteFileOutsideTenantBlocked() {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        assertThatThrownBy(() ->
                fsOp.writeFile(otherDir.resolve("escape.txt").toString(),
                        "escape", null, false, false, true, null, null, null)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testListFilesOutsideTenantBlocked() {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        assertThatThrownBy(() ->
                fsOp.listFiles(otherDir.toString(), false, null, null, false, null, null)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testListFilesWithinTenantAllowed() throws Exception {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, tenantDir);
        Files.writeString(tenantDir.resolve("a.txt"), "a");

        var result = fsOp.listFiles(tenantDir.toString(), false, null, null, false, null, null);
        assertThat(result).isNotNull();
    }

    @Test
    void testSearchFilesOutsideTenantBlocked() {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        assertThatThrownBy(() ->
                fsOp.searchFiles(otherDir.toString(), "*.txt", null)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testDownloadFileOutsideTenantBlocked() {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        assertThatThrownBy(() ->
                fsOp.downloadFile(otherDir.resolve("escape.txt").toString(),
                        tenantDir.resolve("local.txt").toString(), true, true, false, 0, null)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testUploadFileOutsideTenantBlocked() throws Exception {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        Path localSrc = parentDir.resolve("local_source.txt");
        Files.writeString(localSrc, "data");

        LocalFsOperation fsOp = createFsOp(tenantDir, parentDir);

        assertThatThrownBy(() ->
                fsOp.uploadFile(localSrc.toString(),
                        otherDir.resolve("escape.txt").toString(), true, true, false, 0, null)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testListDirectoriesWithinTenantAllowed() throws Exception {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, tenantDir);
        Files.createDirectories(tenantDir.resolve("subdir"));

        var result = fsOp.listDirectories(tenantDir.toString(), false, null, null, false, null);
        assertThat(result).isNotNull();
    }

    @Test
    void testReadFileStreamWithinTenantAllowed() throws Exception {
        CwdContext.setTenantRoot(tenantDir.toString());
        CwdContext.setCwd(tenantDir.toString());

        LocalFsOperation fsOp = createFsOp(tenantDir, tenantDir);
        Files.writeString(tenantDir.resolve("stream.txt"), "stream content");

        var result = fsOp.readFileStream(tenantDir.resolve("stream.txt").toString(),
                "text", null, null, null, null, 0, null);
        assertThat(result).isNotNull();
    }
}
