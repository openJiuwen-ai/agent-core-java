package com.openjiuwen.core.multitenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TmpFileCleanerTest {

    @TempDir
    Path baseDir;

    TenantWorkspaceResolver resolver;
    TmpFileCleaner cleaner;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        resolver = new TenantWorkspaceResolver(baseDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (cleaner != null) {
            cleaner.stop();
        }
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testCleanExpiredFiles() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        Path expiredFile = defaultTmp.resolve("old_file.txt");
        Files.writeString(expiredFile, "expired content");
        assertThat(expiredFile).exists();

        Thread.sleep(1500);

        assertThat(expiredFile).doesNotExist();
    }

    @Test
    void testKeepFreshFiles() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(10), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        Path freshFile = defaultTmp.resolve("fresh_file.txt");
        Files.writeString(freshFile, "fresh content");

        Thread.sleep(500);

        assertThat(freshFile).exists();
    }

    @Test
    void testStartStop_lifecycle() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(60), Duration.ofMillis(500),
            baseDir.toString(), resolver);

        cleaner.start();
        cleaner.stop();

        Thread.sleep(100);
        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testStop_idempotent() {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(60), Duration.ofMillis(500),
            baseDir.toString(), resolver);
        cleaner.start();

        cleaner.stop();
        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testConcurrentCleanAndWrite() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(5), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        int taskCount = 50;

        for (int i = 0; i < taskCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    Path file = tenantTmp.resolve("concurrent_" + idx + ".txt");
                    Files.writeString(file, "data_" + idx);
                } catch (Exception ignored) {
                }
            });
        }

        Thread.sleep(300);

        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testCleanExpiredFiles_tenantTmpDir() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);
        Path expiredFile = tenantTmp.resolve("tenant_old_file.txt");
        Files.writeString(expiredFile, "expired tenant data");
        assertThat(expiredFile).exists();

        Thread.sleep(1500);

        assertThat(expiredFile).doesNotExist();
    }

    @Test
    void testCleanExpiredFiles_defaultAndTenantBoth() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        Path defaultExpired = defaultTmp.resolve("default_old.txt");
        Files.writeString(defaultExpired, "expired default");
        assertThat(defaultExpired).exists();

        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);
        Path tenantExpired = tenantTmp.resolve("tenant_old.txt");
        Files.writeString(tenantExpired, "expired tenant");
        assertThat(tenantExpired).exists();

        Thread.sleep(1500);

        assertThat(defaultExpired).doesNotExist();
        assertThat(tenantExpired).doesNotExist();
    }

    @Test
    void testKeepFreshFiles_tenantTmp() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(10), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);
        Path freshFile = tenantTmp.resolve("fresh_tenant_file.txt");
        Files.writeString(freshFile, "fresh tenant data");

        Thread.sleep(500);

        assertThat(freshFile).exists();
    }

    @Test
    void testNoTenantTmpDir_noError() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        Files.writeString(defaultTmp.resolve("file.txt"), "data");

        assertThatCode(() -> {
            Thread.sleep(300);
        }).doesNotThrowAnyException();

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testEmptyTmpDir_noError() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);

        Thread.sleep(300);

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testNoDefaultTmpDir_noError() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        assertThatCode(() -> {
            Thread.sleep(300);
        }).doesNotThrowAnyException();

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testCleanExpiredFiles_subdirectories() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(1), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        Path defaultTmp = baseDir.resolve("tmp");
        Path subDir = defaultTmp.resolve("subdir");
        Files.createDirectories(subDir);
        Path expiredInSub = subDir.resolve("nested_old.txt");
        Files.writeString(expiredInSub, "expired nested");
        assertThat(expiredInSub).exists();

        Thread.sleep(1500);

        assertThat(expiredInSub).doesNotExist();
    }

    @Test
    void testConcurrentCleanAndWrite_multipleTenants() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(5), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        TenantContext ctxA = TenantContext.builder().tenantId("tenantA").build();
        TenantContext ctxB = TenantContext.builder().tenantId("tenantB").build();
        resolver.initializeTenantSpace(ctxA);
        resolver.initializeTenantSpace(ctxB);
        Path tmpA = resolver.resolveTempDir(ctxA);
        Path tmpB = resolver.resolveTempDir(ctxB);
        Files.createDirectories(tmpA);
        Files.createDirectories(tmpB);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        int taskCount = 50;

        for (int i = 0; i < taskCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    Path fileA = tmpA.resolve("concurrent_a_" + idx + ".txt");
                    Files.writeString(fileA, "data_a_" + idx);
                    Path fileB = tmpB.resolve("concurrent_b_" + idx + ".txt");
                    Files.writeString(fileB, "data_b_" + idx);
                } catch (Exception ignored) {
                }
            });
        }

        Thread.sleep(300);

        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated).isTrue();

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }

    @Test
    void testCleanExpiredFiles_preservesOtherTenantFiles() throws Exception {
        cleaner = new TmpFileCleaner(Duration.ofSeconds(10), Duration.ofMillis(100),
            baseDir.toString(), resolver);
        cleaner.start();

        TenantContext ctxA = TenantContext.builder().tenantId("tenantA").build();
        TenantContext ctxB = TenantContext.builder().tenantId("tenantB").build();
        resolver.initializeTenantSpace(ctxA);
        resolver.initializeTenantSpace(ctxB);
        Path tmpA = resolver.resolveTempDir(ctxA);
        Path tmpB = resolver.resolveTempDir(ctxB);
        Files.createDirectories(tmpA);
        Files.createDirectories(tmpB);
        Files.writeString(tmpA.resolve("a_data.txt"), "tenantA data");
        Files.writeString(tmpB.resolve("b_data.txt"), "tenantB data");

        Thread.sleep(500);

        assertThat(tmpA.resolve("a_data.txt")).exists();
        assertThat(tmpB.resolve("b_data.txt")).exists();

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();
    }
}
